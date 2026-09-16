package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * An SSH server host key an operator has accepted, pinned by fingerprint. Stored as a resource rather
 * than a {@code known_hosts} file because the data directory is itself versioned by the config repo,
 * which would otherwise commit the file.
 *
 * <p>Only the fingerprint is kept: it is what identifies the key, what is shown to the operator when
 * accepting, and what detects a later substitution. 8.3 resource-backed DTO façade (see
 * {@link GitProjectsConfigRecord}).
 */
public class GitKnownHostRecord {

    public record Config(long id, String host, String keyType, String fingerprint) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-known-host");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git Known Hosts")
                    .build();

    public static final class Handler extends NamedResourceHandler<Config> {
        public Handler(GatewayContext context) {
            super(context, META);
        }
    }

    private static volatile Handler handler;

    public static void setHandler(Handler h) {
        handler = h;
    }

    public static Handler handler() {
        return handler;
    }

    private long id;
    private String host;
    private String keyType;
    private String fingerprint;

    public GitKnownHostRecord() {
    }

    private GitKnownHostRecord(Config c) {
        this.id = c.id();
        this.host = c.host();
        this.keyType = c.keyType();
        this.fingerprint = c.fingerprint();
    }

    public long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        try {
            synchronized (SAVE_LOCK) {
                if (id == 0L) {
                    id = handler.getResources().stream()
                            .map(DecodedResource::config)
                            .mapToLong(Config::id)
                            .max()
                            .orElse(0L) + 1L;
                }
                Config c = new Config(id, host, keyType, fingerprint);
                String name = String.valueOf(id);
                if (handler.findResource(name).isPresent()) {
                    handler.modify(name, c).join();
                } else {
                    handler.create(name, c).join();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void delete() {
        try {
            handler.delete(String.valueOf(id)).join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Accepted keys for a host ("host" or "host:port"), newest last. Empty when the host is unknown. */
    public static List<GitKnownHostRecord> findByHost(String host) {
        List<GitKnownHostRecord> out = new ArrayList<>();
        if (handler == null || host == null || host.isBlank()) {
            return out;
        }
        for (DecodedResource<Config> d : handler.getResources()) {
            if (host.equalsIgnoreCase(d.config().host())) {
                out.add(new GitKnownHostRecord(d.config()));
            }
        }
        return out;
    }

    public static List<GitKnownHostRecord> listAll() {
        List<GitKnownHostRecord> out = new ArrayList<>();
        if (handler == null) {
            return out;
        }
        for (DecodedResource<Config> d : handler.getResources()) {
            out.add(new GitKnownHostRecord(d.config()));
        }
        return out;
    }
}
