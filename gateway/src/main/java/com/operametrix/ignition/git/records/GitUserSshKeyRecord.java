package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * User-level SSH key, shared across projects/remotes. 8.3 resource-backed DTO façade
 * (see {@link GitProjectsConfigRecord}). Numeric {@code id} identity preserved.
 */
public class GitUserSshKeyRecord {

    public record Config(long id, String ignitionUser, String keyName, String sshKey) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-user-ssh-key");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git User SSH Keys")
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
    private String ignitionUser;
    private String keyName;
    private String sshKey;

    public GitUserSshKeyRecord() {
    }

    private GitUserSshKeyRecord(Config c) {
        this.id = c.id();
        this.ignitionUser = c.ignitionUser();
        this.keyName = c.keyName();
        this.sshKey = c.sshKey();
    }

    public long getId() {
        return id;
    }

    public void setIgnitionUser(String ignitionUser) {
        this.ignitionUser = ignitionUser;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getSSHKey() {
        return sshKey;
    }

    public void setSSHKey(String sshKey) {
        this.sshKey = sshKey;
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
                Config c = new Config(id, ignitionUser, keyName, sshKey);
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

    public static GitUserSshKeyRecord findById(long id) {
        return handler.findResource(String.valueOf(id))
                .map(d -> new GitUserSshKeyRecord(d.config()))
                .orElse(null);
    }

    public static GitUserSshKeyRecord findByIdAndUser(long id, String ignitionUser) {
        return handler.findResource(String.valueOf(id))
                .map(DecodedResource::config)
                .filter(c -> ignitionUser != null && ignitionUser.equals(c.ignitionUser()))
                .map(GitUserSshKeyRecord::new)
                .orElse(null);
    }

    public static GitUserSshKeyRecord findByUserAndKeyName(String ignitionUser, String keyName) {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .filter(c -> ignitionUser != null && ignitionUser.equals(c.ignitionUser())
                        && keyName != null && keyName.equals(c.keyName()))
                .findFirst()
                .map(GitUserSshKeyRecord::new)
                .orElse(null);
    }

    public static List<GitUserSshKeyRecord> listByUser(String ignitionUser) {
        List<GitUserSshKeyRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            if (ignitionUser != null && ignitionUser.equals(d.config().ignitionUser())) {
                out.add(new GitUserSshKeyRecord(d.config()));
            }
        }
        return out;
    }
}
