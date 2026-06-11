package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Gateway-level remote for the data-directory config repo (singleton, fixed id). Holds the
 * remote URI, target branch, and a credential FK into the user-level SSH key / HTTPS credential
 * records — same FK model as the per-project remotes. Pushing is always a manual action.
 */
public class GitConfigRemoteRecord {

    public record Config(long id, String uri, String branch, long sshKeyId, long httpsCredentialId) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-config-remote");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git Config Remote")
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

    private static final long SINGLETON_ID = 1L;
    private static final String SINGLETON_NAME = String.valueOf(SINGLETON_ID);

    private String uri;
    private String branch;
    private long sshKeyId;
    private long httpsCredentialId;

    public GitConfigRemoteRecord() {
    }

    private GitConfigRemoteRecord(Config c) {
        this.uri = c.uri();
        this.branch = c.branch();
        this.sshKeyId = c.sshKeyId();
        this.httpsCredentialId = c.httpsCredentialId();
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
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

    public void save() {
        try {
            Config c = new Config(SINGLETON_ID, uri, branch, sshKeyId, httpsCredentialId);
            if (handler.findResource(SINGLETON_NAME).isPresent()) {
                handler.modify(SINGLETON_NAME, c).join();
            } else {
                handler.create(SINGLETON_NAME, c).join();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void delete() {
        try {
            if (handler.findResource(SINGLETON_NAME).isPresent()) {
                handler.delete(SINGLETON_NAME).join();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** @return the configured remote, or null when none is set. */
    public static GitConfigRemoteRecord get() {
        return handler.findResource(SINGLETON_NAME)
                .map(d -> new GitConfigRemoteRecord(d.config()))
                .orElse(null);
    }
}
