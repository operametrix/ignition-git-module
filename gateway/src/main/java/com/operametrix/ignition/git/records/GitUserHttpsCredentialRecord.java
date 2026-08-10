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
 * User-level HTTPS credential. 8.3 resource-backed DTO façade (see {@link GitProjectsConfigRecord}).
 * The password is stored as an 8.3 {@link SecretConfig} (embedded, encrypted via the gateway's
 * {@code SystemEncryptionService}); the DTO still exposes plaintext get/set so callers and the RPC
 * contract are unaffected.
 */
public class GitUserHttpsCredentialRecord {

    public record Config(long id, String ignitionUser, String hostPattern, String userName,
                         SecretConfig password) {}

    public static final ResourceType TYPE =
            new ResourceType(GitProjectsConfigRecord.MODULE_ID, "git-user-https-credential");

    public static final ResourceTypeMeta<Config> META =
            ResourceTypeMeta.newBuilder(Config.class)
                    .resourceType(TYPE)
                    .categoryName("Git User HTTPS Credentials")
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
    private String hostPattern;
    private String userName;
    private SecretConfig password;

    public GitUserHttpsCredentialRecord() {
    }

    private GitUserHttpsCredentialRecord(Config c) {
        this.id = c.id();
        this.ignitionUser = c.ignitionUser();
        this.hostPattern = c.hostPattern();
        this.userName = c.userName();
        this.password = c.password();
    }

    public long getId() {
        return id;
    }

    public void setIgnitionUser(String ignitionUser) {
        this.ignitionUser = ignitionUser;
    }

    public String getHostPattern() {
        return hostPattern;
    }

    public void setHostPattern(String hostPattern) {
        this.hostPattern = hostPattern;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    /** Decrypts and returns the stored password as plaintext, or "" if unset/undecryptable. */
    public String getPassword() {
        if (password == null) {
            return "";
        }
        try {
            Secret<?> secret = Secret.create(GatewayHook.getContext(), password);
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

    /** Encrypts the given plaintext via the gateway's SystemEncryptionService and stores it. */
    public void setPassword(String plaintext) {
        this.password = encrypt(plaintext);
    }

    /** Reference path: point the password at a named secret in a Secret Provider. */
    public void setPasswordSecret(SecretConfig cfg) {
        this.password = cfg;
    }

    /** The stored {@link SecretConfig} (embedded or referenced), or null if unset. */
    public SecretConfig getSecret() {
        return password;
    }

    /** Whether a password secret is set. */
    public boolean hasSecret() {
        return password != null;
    }

    /** Encrypts a plaintext password into an embedded {@link SecretConfig}. */
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
                Config c = new Config(id, ignitionUser, hostPattern, userName, password);
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

    public static GitUserHttpsCredentialRecord findById(long id) {
        return handler.findResource(String.valueOf(id))
                .map(d -> new GitUserHttpsCredentialRecord(d.config()))
                .orElse(null);
    }

    public static GitUserHttpsCredentialRecord findByIdAndUser(long id, String ignitionUser) {
        return handler.findResource(String.valueOf(id))
                .map(DecodedResource::config)
                .filter(c -> ignitionUser != null && ignitionUser.equals(c.ignitionUser()))
                .map(GitUserHttpsCredentialRecord::new)
                .orElse(null);
    }

    public static GitUserHttpsCredentialRecord findByUserAndHostPattern(String ignitionUser, String hostPattern) {
        return handler.getResources().stream()
                .map(DecodedResource::config)
                .filter(c -> ignitionUser != null && ignitionUser.equals(c.ignitionUser())
                        && hostPattern != null && hostPattern.equals(c.hostPattern()))
                .findFirst()
                .map(GitUserHttpsCredentialRecord::new)
                .orElse(null);
    }

    /** All HTTPS credentials regardless of owner — for the gateway-level config-remote credential picker. */
    public static List<GitUserHttpsCredentialRecord> listAll() {
        List<GitUserHttpsCredentialRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            out.add(new GitUserHttpsCredentialRecord(d.config()));
        }
        return out;
    }

    public static List<GitUserHttpsCredentialRecord> listByUser(String ignitionUser) {
        List<GitUserHttpsCredentialRecord> out = new ArrayList<>();
        for (DecodedResource<Config> d : handler.getResources()) {
            if (ignitionUser != null && ignitionUser.equals(d.config().ignitionUser())) {
                out.add(new GitUserHttpsCredentialRecord(d.config()));
            }
        }
        return out;
    }
}
