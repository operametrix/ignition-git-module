package com.operametrix.ignition.git.records;

import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.gateway.config.DecodedResource;
import com.inductiveautomation.ignition.gateway.config.NamedResourceHandler;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMeta;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.secrets.Plaintext;
import com.inductiveautomation.ignition.gateway.secrets.Secret;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import com.operametrix.ignition.git.GatewayHook;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * User-level SSH key, shared across projects/remotes. 8.3 resource-backed DTO façade
 * (see {@link GitProjectsConfigRecord}). Numeric {@code id} identity preserved.
 *
 * <p>The private key is stored as an 8.3 {@link SecretConfig} ({@code sshKeySecret}) — either
 * embedded (encrypted via the gateway's {@code SystemEncryptionService}) or a reference to a named
 * secret in a gateway Secret Provider. The legacy plaintext {@code sshKey} component is kept
 * (nullable) so pre-migration resources still deserialize; {@link #getSSHKey()} prefers the secret
 * and falls back to the legacy value, and any {@link #save()} upgrades the row in place.
 */
public class GitUserSshKeyRecord {

    public record Config(long id, String ignitionUser, String keyName, String sshKey,
                         SecretConfig sshKeySecret) {}

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
    private String sshKey;             // legacy plaintext; null once migrated to sshKeySecret
    private SecretConfig sshKeySecret;

    public GitUserSshKeyRecord() {
    }

    private GitUserSshKeyRecord(Config c) {
        this.id = c.id();
        this.ignitionUser = c.ignitionUser();
        this.keyName = c.keyName();
        this.sshKey = c.sshKey();
        this.sshKeySecret = c.sshKeySecret();
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

    /**
     * The private key as plaintext. Prefers the {@link SecretConfig} (embedded or referenced,
     * resolved via {@link Secret}); falls back to the legacy plaintext field for un-migrated rows.
     * Returns "" if unset/undecryptable. Signature unchanged so Designer callers
     * ({@code GitManager.setAuthentication*}) are unaffected.
     */
    public String getSSHKey() {
        if (sshKeySecret != null) {
            try {
                Secret<?> secret = Secret.create(GatewayHook.getContext(), sshKeySecret);
                Plaintext pt = secret.getPlaintext();
                try {
                    return pt.getAsString(StandardCharsets.UTF_8);
                } finally {
                    pt.clear();
                }
            } catch (Exception e) {
                return "";
            }
        }
        return sshKey == null ? "" : sshKey;
    }

    /** Inline path: encrypts the key at rest and drops the legacy plaintext field. */
    public void setSSHKey(String plaintext) {
        this.sshKeySecret = encrypt(plaintext);
        this.sshKey = null;
    }

    /** Reference path: point at a named secret in a Secret Provider. */
    public void setSSHKeySecret(SecretConfig cfg) {
        this.sshKeySecret = cfg;
        this.sshKey = null;
    }

    /** The stored {@link SecretConfig} (embedded or referenced), or null for legacy plaintext-only. */
    public SecretConfig getSecret() {
        return sshKeySecret;
    }

    /** Whether any secret is set (SecretConfig or legacy plaintext). */
    public boolean hasSecret() {
        return sshKeySecret != null || (sshKey != null && !sshKey.isEmpty());
    }

    /** Encrypts a plaintext key into an embedded {@link SecretConfig} (same as the HTTPS record). */
    public static SecretConfig encrypt(String plaintext) {
        try (Plaintext pt = Plaintext.fromString(plaintext == null ? "" : plaintext, StandardCharsets.UTF_8)) {
            JsonElement ciphertext = GatewayHook.getContext()
                    .getSystemEncryptionService()
                    .encryptToJson(pt);
            return SecretConfig.embedded(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
                Config c = new Config(id, ignitionUser, keyName, sshKey, sshKeySecret);
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

    /** All SSH keys regardless of owner — for the gateway-level config-remote credential picker. */
    public static List<GitUserSshKeyRecord> listAll() {
        List<GitUserSshKeyRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            out.add(new GitUserSshKeyRecord(d.config()));
        }
        return out;
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
