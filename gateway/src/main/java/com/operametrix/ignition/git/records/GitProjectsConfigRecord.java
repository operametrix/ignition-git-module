package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Project → git repo configuration. Migrated from a SimpleORM {@code PersistentRecord} to the
 * Ignition 8.3 resource/config system: storage is a {@link NamedResourceHandler}, the persisted
 * shape is the immutable {@link Config} record, and this class is a mutable DTO façade preserving
 * the previous getter/setter/finder API so callers are unaffected. Numeric {@code id} identity is
 * preserved (resource name = {@code String.valueOf(id)}).
 */
public class GitProjectsConfigRecord {

    public static final String MODULE_ID = "com.operametrix.ignition.git";

    /** Immutable persisted form. */
    public record Config(long id, String projectName, String uri) {}

    public static final ResourceType TYPE = new ResourceType(MODULE_ID, "git-project");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git Projects")
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
    private String projectName;
    private String uri;

    public GitProjectsConfigRecord() {
    }

    private GitProjectsConfigRecord(Config c) {
        this.id = c.id();
        this.projectName = c.projectName();
        this.uri = c.uri();
    }

    public long getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getURI() {
        return uri;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setURI(String uri) {
        this.uri = uri;
    }

    public boolean hasRemote() {
        return uri != null && !uri.isEmpty();
    }

    private static final Object SAVE_LOCK = new Object();

    public void save() {
        try {
            synchronized (SAVE_LOCK) {
                if (id == 0L) {
                    id = nextId();
                }
                Config c = new Config(id, projectName, uri == null ? "" : uri);
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

    private static long nextId() {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .mapToLong(Config::id)
                .max()
                .orElse(0L) + 1L;
    }

    public static GitProjectsConfigRecord findByProjectName(String projectName) {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .filter(c -> projectName != null && projectName.equals(c.projectName()))
                .findFirst()
                .map(GitProjectsConfigRecord::new)
                .orElse(null);
    }

    public static GitProjectsConfigRecord findById(long id) {
        return handler.findResource(String.valueOf(id))
                .map(d -> new GitProjectsConfigRecord(d.config()))
                .orElse(null);
    }
}
