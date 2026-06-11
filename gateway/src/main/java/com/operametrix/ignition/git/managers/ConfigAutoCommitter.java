package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionChangeEvent;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Auto-commits gateway config changes. Registered via {@code ConfigurationManager.addListener}
 * — the manager-level {@link ResourceCollectionListener} is the live notification surface
 * (per-resource {@code ResourceListener}s only work on a {@code RuntimeResourceCollection} you
 * hold yourself; {@code getConfigCollection()} builds a snapshot per call, so listeners added
 * there are never notified). Manager events carry no per-resource detail, so the commit message
 * is derived from the git status: changed files grouped into their resource directories. Commits
 * are authored as the gateway (its system name).
 *
 * <p>Events arriving within a short quiesce window coalesce into one commit (an operation often
 * triggers follow-up resources, e.g. a new device also creates its System tag definitions).
 * A clean tree after the quiesce is a no-op (e.g. the scan after {@link
 * DataDirGitManager#restoreToCommit} re-applies already-committed files), so no empty or
 * duplicate commits. The gitignored {@code local} collection is skipped.
 */
public class ConfigAutoCommitter implements ResourceCollectionListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigAutoCommitter.class);

    /** Follow-up resources of one logical operation arrive within well under this window. */
    private static final long QUIESCE_MS = 2000;

    /** Startup sweep delay — lets boot-time config writes settle into the same commit. */
    private static final long STARTUP_SWEEP_MS = 10_000;

    /** Runtime-only collection, gitignored — never part of a commit. */
    private static final String LOCAL_COLLECTION = "local";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "git-config-auto-commit");
        t.setDaemon(true);
        return t;
    });

    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduled;

    @Override
    public void collectionAdded(ResourceCollectionChangeEvent event) {
        poke(event);
    }

    @Override
    public void collectionDeleted(ResourceCollectionChangeEvent event) {
        poke(event);
    }

    @Override
    public void collectionUpdated(ResourceCollectionChangeEvent event) {
        poke(event);
    }

    /**
     * Commits anything already dirty shortly after startup — changes made while the gateway or
     * this module was offline are the one case the listener can't see, so sweep them here
     * instead of leaving the repo dirty until the next config change.
     */
    public void commitLeftovers() {
        schedule(STARTUP_SWEEP_MS);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void poke(ResourceCollectionChangeEvent event) {
        if (LOCAL_COLLECTION.equals(event.getName()) || !DataDirGitManager.isInitialized()) {
            return;
        }
        schedule(QUIESCE_MS);
    }

    private void schedule(long delayMs) {
        synchronized (scheduleLock) {
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            scheduled = executor.schedule(this::flush, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flush() {
        synchronized (scheduleLock) {
            scheduled = null;
        }
        try {
            if (!DataDirGitManager.isInitialized()) {
                return;
            }
            List<DataDirGitManager.ConfigChange> status = DataDirGitManager.getStatus();
            if (status.isEmpty()) {
                return;
            }
            DataDirGitManager.commitAllIfDirty(buildMessage(status));
        } catch (Exception e) {
            logger.error("Auto-commit of config changes failed; the changes stay uncommitted.", e);
        }
    }

    /** Subject = first impacted resource (+count); body = one line per resource. */
    private static String buildMessage(List<DataDirGitManager.ConfigChange> status) {
        // Group files into their resource directory; mixed member change types read as an update.
        Map<String, Set<String>> verbsByDir = new LinkedHashMap<>();
        for (DataDirGitManager.ConfigChange c : status) {
            int slash = c.path().lastIndexOf('/');
            String dir = slash >= 0 ? c.path().substring(0, slash) : c.path();
            verbsByDir.computeIfAbsent(dir, k -> new LinkedHashSet<>()).add(verb(c.type()));
        }
        List<String> lines = verbsByDir.entrySet().stream()
                .map(e -> (e.getValue().size() == 1 ? e.getValue().iterator().next() : "Updated")
                        + " " + display(e.getKey()))
                .toList();

        if (lines.size() == 1) {
            return lines.get(0);
        }
        StringBuilder sb = new StringBuilder(lines.get(0))
                .append(" (+").append(lines.size() - 1).append(" more)\n");
        for (String line : lines) {
            sb.append('\n').append(line);
        }
        return sb.toString();
    }

    private static String verb(String type) {
        return switch (type) {
            case "DELETED" -> "Deleted";
            case "MODIFIED" -> "Updated";
            default -> "Added";
        };
    }

    private static String display(String dir) {
        return dir.startsWith("config/resources/") ? dir.substring("config/resources/".length()) : dir;
    }
}
