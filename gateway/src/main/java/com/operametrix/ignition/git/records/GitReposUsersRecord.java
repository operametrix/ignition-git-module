package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Project ↔ user registration marker. 8.3 resource-backed DTO façade (see
 * {@link GitProjectsConfigRecord} for the pattern). Numeric {@code id} identity preserved.
 */
public class GitReposUsersRecord {

    public record Config(long id, long projectId, String ignitionUser) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-repo-user");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git Repo Users")
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
    private long projectId;
    private String ignitionUser;

    public GitReposUsersRecord() {
    }

    private GitReposUsersRecord(Config c) {
        this.id = c.id();
        this.projectId = c.projectId();
        this.ignitionUser = c.ignitionUser();
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public void setIgnitionUser(String ignitionUser) {
        this.ignitionUser = ignitionUser;
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
                Config c = new Config(id, projectId, ignitionUser);
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

    public static GitReposUsersRecord findByProjectAndUser(long projectId, String ignitionUser) {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .filter(c -> c.projectId() == projectId
                        && ignitionUser != null && ignitionUser.equals(c.ignitionUser()))
                .findFirst()
                .map(GitReposUsersRecord::new)
                .orElse(null);
    }
}
