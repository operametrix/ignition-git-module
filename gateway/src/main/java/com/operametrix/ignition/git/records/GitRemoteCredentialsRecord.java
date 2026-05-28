package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Per (project, user, remote) credential reference (FK ids into the user-level SSH key / HTTPS
 * credential resources). 8.3 resource-backed DTO façade (see {@link GitProjectsConfigRecord}).
 */
public class GitRemoteCredentialsRecord {

    public record Config(long id, long projectId, String ignitionUser, String remoteName,
                         long sshKeyId, long httpsCredentialId) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-remote-credential");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git Remote Credentials")
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
    private String remoteName;
    private long sshKeyId;
    private long httpsCredentialId;

    public GitRemoteCredentialsRecord() {
    }

    private GitRemoteCredentialsRecord(Config c) {
        this.id = c.id();
        this.projectId = c.projectId();
        this.ignitionUser = c.ignitionUser();
        this.remoteName = c.remoteName();
        this.sshKeyId = c.sshKeyId();
        this.httpsCredentialId = c.httpsCredentialId();
    }

    public long getId() {
        return id;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public void setIgnitionUser(String ignitionUser) {
        this.ignitionUser = ignitionUser;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    public long getSshKeyId() {
        return sshKeyId;
    }

    public void setSshKeyId(long sshKeyId) {
        this.sshKeyId = sshKeyId;
    }

    public long getHttpsCredentialId() {
        return httpsCredentialId;
    }

    public void setHttpsCredentialId(long httpsCredentialId) {
        this.httpsCredentialId = httpsCredentialId;
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
                Config c = new Config(id, projectId, ignitionUser, remoteName, sshKeyId, httpsCredentialId);
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

    public static GitRemoteCredentialsRecord findByProjectUserRemote(long projectId, String ignitionUser, String remoteName) {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .filter(c -> c.projectId() == projectId
                        && ignitionUser != null && ignitionUser.equals(c.ignitionUser())
                        && remoteName != null && remoteName.equals(c.remoteName()))
                .findFirst()
                .map(GitRemoteCredentialsRecord::new)
                .orElse(null);
    }

    public static List<GitRemoteCredentialsRecord> listBySshKeyId(long sshKeyId) {
        List<GitRemoteCredentialsRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            if (d.config().sshKeyId() == sshKeyId) {
                out.add(new GitRemoteCredentialsRecord(d.config()));
            }
        }
        return out;
    }

    public static List<GitRemoteCredentialsRecord> listByHttpsCredentialId(long httpsCredentialId) {
        List<GitRemoteCredentialsRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            if (d.config().httpsCredentialId() == httpsCredentialId) {
                out.add(new GitRemoteCredentialsRecord(d.config()));
            }
        }
        return out;
    }
}
