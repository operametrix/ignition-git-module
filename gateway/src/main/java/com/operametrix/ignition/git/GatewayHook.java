package com.operametrix.ignition.git;

import com.operametrix.ignition.git.records.GitConfigRemoteRecord;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitRemoteCredentialsRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.operametrix.ignition.git.records.GitUserHttpsCredentialRecord;
import com.operametrix.ignition.git.records.GitUserSshKeyRecord;
import com.operametrix.ignition.git.records.legacy.GitLegacyImporter;
import com.operametrix.ignition.git.managers.ConfigAutoCommitter;
import com.operametrix.ignition.git.managers.DataDirGitManager;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.config.ResourceTypeMetaRegistry;
import com.inductiveautomation.ignition.gateway.secrets.ManagedSecretProvider;
import com.inductiveautomation.ignition.gateway.secrets.Plaintext;
import com.inductiveautomation.ignition.gateway.secrets.Secret;
import com.inductiveautomation.ignition.gateway.secrets.SecretConfig;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.PermissionType;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.rpc.GatewayRpcImplementation;
import com.inductiveautomation.ignition.gateway.web.session.WebUiSession;
import com.inductiveautomation.ignition.gateway.web.systemjs.SystemJsModule;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class GatewayHook extends AbstractGatewayModuleHook {
    static public String MODULE_NAME = "Git";

    /** Alias for both the mounted JS bundle (/res/<alias>/…) and the data routes (/data/<alias>/…). */
    private static final String MOUNT_ALIAS = "git-config";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private GatewayScriptModule scriptModule;
    private static GatewayContext context;

    private GitProjectsConfigRecord.Handler projectHandler;
    private GitReposUsersRecord.Handler repoUserHandler;
    private GitUserSshKeyRecord.Handler sshKeyHandler;
    private GitUserHttpsCredentialRecord.Handler httpsCredHandler;
    private GitRemoteCredentialsRecord.Handler remoteCredHandler;
    private GitConfigRemoteRecord.Handler configRemoteHandler;
    private ConfigAutoCommitter autoCommitter;

    /** Gateway context, available after {@link #setup(GatewayContext)} has run. */
    public static GatewayContext getContext() {
        return context;
    }

    @Override
    public void setup(GatewayContext gatewayContext) {
        context = gatewayContext;

        // Register the 8.3 resource/config types (replaces SimpleORM schema bootstrap).
        ResourceTypeMetaRegistry registry = context.getConfigurationManager().getResourceTypeMetaRegistry();
        registry.register(GitProjectsConfigRecord.META);
        registry.register(GitReposUsersRecord.META);
        registry.register(GitUserSshKeyRecord.META);
        registry.register(GitUserHttpsCredentialRecord.META);
        registry.register(GitRemoteCredentialsRecord.META);
        registry.register(GitConfigRemoteRecord.META);

        // Create the resource handlers (DAOs) and publish them to the record façades.
        projectHandler = new GitProjectsConfigRecord.Handler(context);
        repoUserHandler = new GitReposUsersRecord.Handler(context);
        sshKeyHandler = new GitUserSshKeyRecord.Handler(context);
        httpsCredHandler = new GitUserHttpsCredentialRecord.Handler(context);
        remoteCredHandler = new GitRemoteCredentialsRecord.Handler(context);
        configRemoteHandler = new GitConfigRemoteRecord.Handler(context);

        GitProjectsConfigRecord.setHandler(projectHandler);
        GitReposUsersRecord.setHandler(repoUserHandler);
        GitUserSshKeyRecord.setHandler(sshKeyHandler);
        GitUserHttpsCredentialRecord.setHandler(httpsCredHandler);
        GitRemoteCredentialsRecord.setHandler(remoteCredHandler);
        GitConfigRemoteRecord.setHandler(configRemoteHandler);

        scriptModule = new GatewayScriptModule(context);

        // Register the React gateway web page (config-as-code history/restore) under Platform → System,
        // alongside the Scan File System / Modes actions. NOTE: "system" is best-effort to merge into
        // the platform's built-in System category; verify the exact key on a live gateway and adjust
        // (fall back to a dedicated category under Platform if it doesn't merge).
        SystemJsModule jsModule = new SystemJsModule(
                "com.operametrix.ignition.git.GitConfig",
                "/res/" + MOUNT_ALIAS + "/gitConfig.js");
        context.getWebResourceManager().getNavigationModel().getPlatform()
                .addCategory("system", cat -> cat
                        .label("System")
                        .requiredPermission(PermissionType.WRITE)
                        .addPage("Versioning", page -> page
                                .position(50)
                                .mount("/config-versioning", "GitConfigPage", jsModule)));

        logger.info("setup()");
    }

    @Override
    public void startup(LicenseState licenseState) {
        projectHandler.startup();
        repoUserHandler.startup();
        sshKeyHandler.startup();
        httpsCredHandler.startup();
        remoteCredHandler.startup();
        configRemoteHandler.startup();

        // One-time migration of any legacy SimpleORM rows from a pre-8.3 install.
        try {
            GitLegacyImporter.migrateIfNeeded(context);
        } catch (Exception e) {
            logger.error("Legacy git config migration failed; existing credentials may need to be re-entered.", e);
        }

        // Auto-commit gateway config changes (no-op until the data-dir repo is initialized).
        // Manager-level listener — see ConfigAutoCommitter's javadoc for why not getConfigCollection().
        autoCommitter = new ConfigAutoCommitter();
        context.getConfigurationManager().addListener(autoCommitter);
        // Changes made while the gateway/module was offline can't reach the listener — sweep them.
        autoCommitter.commitLeftovers();

        logger.info("startup()");
    }

    @Override
    public void shutdown() {
        if (autoCommitter != null) {
            context.getConfigurationManager().removeListener(autoCommitter);
            autoCommitter.shutdown();
        }
        if (configRemoteHandler != null) configRemoteHandler.shutdown();
        if (remoteCredHandler != null) remoteCredHandler.shutdown();
        if (httpsCredHandler != null) httpsCredHandler.shutdown();
        if (sshKeyHandler != null) sshKeyHandler.shutdown();
        if (repoUserHandler != null) repoUserHandler.shutdown();
        if (projectHandler != null) projectHandler.shutdown();
        logger.info("shutdown()");
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    @Override
    public Optional<GatewayRpcImplementation> getRpcImplementation() {
        return Optional.of(GatewayRpcImplementation.of(GitScriptInterface.SERIALIZER, scriptModule));
    }

    // ── Gateway web page: mounted React bundle + REST routes ───────────────────────────────────

    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of(MOUNT_ALIAS);
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.of("mounted");
    }

    /**
     * REST routes backing the Versioning page. Mounted under {@code /data/git-config/…}.
     * Reads require READ, mutations require WRITE (gateway config-admin). All return JSON.
     */
    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        routes.newRoute("/status").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ).nocache()
                .handler(this::handleStatus).mount();

        // CSRF token for the current web UI session. GET is CSRF-exempt; the page echoes this value
        // in the X-CSRF-Token header on mutating (POST) routes, which the gateway's web-session
        // access control requires.
        routes.newRoute("/csrf").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ).nocache()
                .handler(this::handleCsrf).mount();

        routes.newRoute("/history").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ)
                .handler(this::handleHistory).mount();

        routes.newRoute("/commit-files").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ)
                .handler(this::handleCommitFiles).mount();

        routes.newRoute("/file-diff").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ)
                .handler(this::handleFileDiff).mount();

        routes.newRoute("/remote").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ).nocache()
                .handler(this::handleGetRemote).mount();

        routes.newRoute("/credentials").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ).nocache()
                .handler(this::handleGetCredentials).mount();

        routes.newRoute("/secret-providers").method(HttpMethod.GET).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.READ).nocache()
                .handler(this::handleGetSecretProviders).mount();

        routes.newRoute("/remote").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleSaveRemote).mount();

        routes.newRoute("/remote-remove").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleRemoveRemote).mount();

        routes.newRoute("/remote-test").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleTestRemote).mount();

        routes.newRoute("/push").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handlePush).mount();

        routes.newRoute("/credentials").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleAddCredential).mount();

        routes.newRoute("/restore").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleRestore).mount();

        routes.newRoute("/init").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleInit).mount();

        routes.newRoute("/update-from-remote").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleUpdateFromRemote).mount();

        routes.newRoute("/deinit").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleDeinit).mount();
    }

    private Object handleStatus(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject o = new JsonObject();
            boolean init = DataDirGitManager.isInitialized();
            o.addProperty("initialized", init);
            JsonArray changes = new JsonArray();
            boolean dirty = false;
            if (init) {
                List<DataDirGitManager.ConfigChange> list = DataDirGitManager.getStatus();
                dirty = !list.isEmpty();
                for (DataDirGitManager.ConfigChange c : list) {
                    JsonObject co = new JsonObject();
                    co.addProperty("path", c.path());
                    co.addProperty("type", c.type());
                    changes.add(co);
                }
            }
            o.addProperty("dirty", dirty);
            o.add("changes", changes);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleCsrf(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject o = new JsonObject();
            WebUiSession.find(req).ifPresent(s -> o.addProperty("token", s.getCsrfToken()));
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleHistory(RequestContext req, HttpServletResponse resp) {
        try {
            int skip = parseInt(req.getParameter("skip"), 0);
            int limit = parseInt(req.getParameter("limit"), 25);
            List<String[]> commits = DataDirGitManager.history(skip, limit);
            boolean remoteConfigured = GitConfigRemoteRecord.get() != null;
            String[] pointers = DataDirGitManager.pointerHashes();
            JsonArray arr = new JsonArray();
            for (String[] c : commits) {
                JsonObject co = new JsonObject();
                co.addProperty("hash", c[0]);
                co.addProperty("shortHash", c[1]);
                co.addProperty("author", c[2]);
                co.addProperty("date", c[3]);
                co.addProperty("message", c[4]);
                co.addProperty("refs", c[5]);
                arr.add(co);
            }
            JsonObject o = new JsonObject();
            o.add("commits", arr);
            o.addProperty("remoteConfigured", remoteConfigured);
            // Ref pointers: which single commit the local and remote branch tips point at.
            o.addProperty("localHead", pointers[0]);
            o.addProperty("remoteHead", pointers[1]);
            o.addProperty("hasMore", commits.size() == limit);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleCommitFiles(RequestContext req, HttpServletResponse resp) {
        try {
            String hash = req.getParameter("hash");
            JsonArray arr = new JsonArray();
            for (String entry : DataDirGitManager.commitFiles(hash)) {
                int idx = entry.indexOf(':');
                JsonObject fo = new JsonObject();
                fo.addProperty("changeType", idx >= 0 ? entry.substring(0, idx) : "");
                fo.addProperty("path", idx >= 0 ? entry.substring(idx + 1) : entry);
                arr.add(fo);
            }
            JsonObject o = new JsonObject();
            o.add("files", arr);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleFileDiff(RequestContext req, HttpServletResponse resp) {
        try {
            String hash = req.getParameter("hash");
            String path = req.getParameter("path");
            List<String> diff = DataDirGitManager.fileDiff(hash, path);
            JsonObject o = new JsonObject();
            o.addProperty("old", diff.size() > 0 ? diff.get(0) : "");
            o.addProperty("new", diff.size() > 1 ? diff.get(1) : "");
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleGetRemote(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject o = new JsonObject();
            GitConfigRemoteRecord remote = GitConfigRemoteRecord.get();
            o.addProperty("configured", remote != null);
            if (remote != null) {
                o.addProperty("uri", remote.getUri());
                o.addProperty("branch", remote.getBranch());
                // Secret prefill: mode + (referenced) provider/secret + (https) username. The
                // embedded secret itself is never returned — the drawer leaves it blank to keep it.
                SecretConfig secret = null;
                if (remote.getSshKeyId() > 0) {
                    GitUserSshKeyRecord key = GitUserSshKeyRecord.findById(remote.getSshKeyId());
                    if (key != null) {
                        secret = key.getSecret();
                    }
                } else if (remote.getHttpsCredentialId() > 0) {
                    GitUserHttpsCredentialRecord cred =
                            GitUserHttpsCredentialRecord.findById(remote.getHttpsCredentialId());
                    if (cred != null) {
                        secret = cred.getSecret();
                        o.addProperty("username", cred.getUserName());
                    }
                }
                boolean referenced = secret != null && secret.isReferenced();
                o.addProperty("secretMode", referenced ? "reference" : "inline");
                if (referenced) {
                    o.addProperty("providerName", secret.getAsReferenced().getProviderName());
                    o.addProperty("secretName", secret.getAsReferenced().getSecretName());
                }
                // Unsynced-commit count for the header sync indicator.
                o.addProperty("ahead", DataDirGitManager.aheadCount());
                long time = DataDirGitManager.getLastPushTime();
                if (time > 0) {
                    JsonObject lastPush = new JsonObject();
                    lastPush.addProperty("time", time);
                    String err = DataDirGitManager.getLastPushError();
                    lastPush.addProperty("ok", err == null);
                    if (err != null) {
                        lastPush.addProperty("error", err);
                    }
                    o.add("lastPush", lastPush);
                }
            }
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleGetCredentials(RequestContext req, HttpServletResponse resp) {
        try {
            JsonArray arr = new JsonArray();
            for (GitUserSshKeyRecord key : GitUserSshKeyRecord.listAll()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", key.getId());
                c.addProperty("type", "SSH");
                c.addProperty("label", key.getKeyName());
                arr.add(c);
            }
            for (GitUserHttpsCredentialRecord cred : GitUserHttpsCredentialRecord.listAll()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", cred.getId());
                c.addProperty("type", "HTTPS");
                c.addProperty("label", cred.getHostPattern() + " — " + cred.getUserName());
                arr.add(c);
            }
            JsonObject o = new JsonObject();
            o.add("credentials", arr);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleSaveRemote(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject body = new Gson().fromJson(req.readBody(), JsonObject.class);
            String uri = optString(body, "uri");
            if (uri == null || uri.isBlank()) {
                throw new RuntimeException("A repository URI is required.");
            }
            String branch = optString(body, "branch");
            if (branch == null || branch.isBlank()) {
                branch = "main";
            }
            // Create/update the config remote's own credential in place from the inline secret
            // (no user-entered name; auto-derived), then link it. Keeps a single dedicated record.
            boolean ssh = !uri.trim().toLowerCase().startsWith("http");
            boolean reference = "reference".equalsIgnoreCase(optString(body, "mode"));
            GitConfigRemoteRecord existing = GitConfigRemoteRecord.get();
            long sshKeyId = 0, httpsCredentialId = 0;
            if (ssh) {
                GitUserSshKeyRecord rec = existing != null && existing.getSshKeyId() > 0
                        ? GitUserSshKeyRecord.findById(existing.getSshKeyId()) : null;
                if (rec == null) {
                    rec = new GitUserSshKeyRecord();
                }
                rec.setIgnitionUser(req.getActor());
                rec.setKeyName("Config repository (" + hostOf(uri) + ")");
                if (reference) {
                    rec.setSSHKeySecret(referencedSecret(body));
                } else {
                    String key = optString(body, "key");
                    if (key != null && !key.isBlank()) {
                        rec.setSSHKey(key);
                    } else if (!rec.hasSecret()) {
                        throw new RuntimeException("A private key is required.");
                    }
                }
                rec.save();
                sshKeyId = rec.getId();
            } else {
                GitUserHttpsCredentialRecord rec = existing != null && existing.getHttpsCredentialId() > 0
                        ? GitUserHttpsCredentialRecord.findById(existing.getHttpsCredentialId()) : null;
                if (rec == null) {
                    rec = new GitUserHttpsCredentialRecord();
                }
                rec.setIgnitionUser(req.getActor());
                rec.setHostPattern(hostOf(uri));
                String username = optString(body, "username");
                rec.setUserName(username == null ? "" : username.trim());
                if (reference) {
                    rec.setPasswordSecret(referencedSecret(body));
                } else {
                    String password = optString(body, "password");
                    if (password != null && !password.isBlank()) {
                        rec.setPassword(password);
                    } else if (!rec.hasSecret()) {
                        throw new RuntimeException("A password/token is required.");
                    }
                }
                rec.save();
                httpsCredentialId = rec.getId();
            }
            DataDirGitManager.saveRemote(uri, branch, sshKeyId, httpsCredentialId);
            return ok();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    /** Builds a referenced {@link SecretConfig} from the request's providerName/secretName. */
    private static SecretConfig referencedSecret(JsonObject body) {
        String providerName = optString(body, "providerName");
        String secretName = optString(body, "secretName");
        if (providerName == null || providerName.isBlank() || secretName == null || secretName.isBlank()) {
            throw new RuntimeException("A provider and secret name are required to reference a stored secret.");
        }
        return SecretConfig.referenced(providerName.trim(), secretName.trim());
    }

    /** Best-effort host extracted from a git URI, for auto-naming the credential. */
    private static String hostOf(String uri) {
        if (uri == null) {
            return "remote";
        }
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(?:://|@)([^/:]+)").matcher(uri.trim());
        return m.find() ? m.group(1) : "remote";
    }

    private Object handleRemoveRemote(RequestContext req, HttpServletResponse resp) {
        try {
            DataDirGitManager.removeRemote();
            return ok();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleTestRemote(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject body = new Gson().fromJson(req.readBody(), JsonObject.class);
            String uri = optString(body, "uri");
            boolean ssh = uri != null && !uri.trim().toLowerCase().startsWith("http");
            boolean reference = "reference".equalsIgnoreCase(optString(body, "mode"));
            String username = optString(body, "username");
            if (reference) {
                // Resolve the referenced secret to plaintext for the test.
                SecretConfig cfg = referencedSecret(body);
                Secret<?> secret = Secret.create(context, cfg);
                Plaintext pt = secret.getPlaintext();
                try {
                    String plain = pt.getAsString(java.nio.charset.StandardCharsets.UTF_8);
                    DataDirGitManager.testRemoteRaw(uri, ssh ? plain : null, username, ssh ? null : plain);
                } finally {
                    pt.clear();
                }
            } else {
                String key = optString(body, "key");
                String password = optString(body, "password");
                String secretVal = ssh ? key : password;
                if (secretVal == null || secretVal.isBlank()) {
                    // Editing without re-entering the embedded secret: test the saved credential.
                    GitConfigRemoteRecord remote = GitConfigRemoteRecord.get();
                    if (remote == null) {
                        throw new RuntimeException("Enter a secret to test, or save the remote first.");
                    }
                    DataDirGitManager.testRemote(uri, remote.getSshKeyId(), remote.getHttpsCredentialId());
                } else {
                    DataDirGitManager.testRemoteRaw(uri, ssh ? key : null, username, ssh ? null : password);
                }
            }
            return ok();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handlePush(RequestContext req, HttpServletResponse resp) {
        try {
            DataDirGitManager.push();
            return ok();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleAddCredential(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject body = new Gson().fromJson(req.readBody(), JsonObject.class);
            String type = optString(body, "type");
            // mode ∈ inline (type the secret, encrypted at rest) | reference (point at a
            // Secret Provider secret). Defaults to inline so older callers keep working.
            String mode = optString(body, "mode");
            boolean reference = "reference".equalsIgnoreCase(mode);
            SecretConfig referenced = null;
            if (reference) {
                String providerName = optString(body, "providerName");
                String secretName = optString(body, "secretName");
                if (providerName == null || providerName.isBlank()
                        || secretName == null || secretName.isBlank()) {
                    throw new RuntimeException("A provider and secret name are required to reference a stored secret.");
                }
                referenced = SecretConfig.referenced(providerName.trim(), secretName.trim());
            }
            JsonObject o = new JsonObject();
            if ("SSH".equalsIgnoreCase(type)) {
                String name = optString(body, "name");
                if (name == null || name.isBlank()) {
                    throw new RuntimeException("A key name is required.");
                }
                GitUserSshKeyRecord record = new GitUserSshKeyRecord();
                record.setIgnitionUser(req.getActor());
                record.setKeyName(name.trim());
                if (reference) {
                    record.setSSHKeySecret(referenced);
                } else {
                    String key = optString(body, "key");
                    if (key == null || key.isBlank()) {
                        throw new RuntimeException("The private key is required.");
                    }
                    record.setSSHKey(key);
                }
                record.save();
                o.addProperty("id", record.getId());
            } else if ("HTTPS".equalsIgnoreCase(type)) {
                String host = optString(body, "host");
                String username = optString(body, "username");
                if (host == null || host.isBlank()) {
                    throw new RuntimeException("A host label is required.");
                }
                GitUserHttpsCredentialRecord record = new GitUserHttpsCredentialRecord();
                record.setIgnitionUser(req.getActor());
                record.setHostPattern(host.trim());
                record.setUserName(username == null ? "" : username.trim());
                if (reference) {
                    record.setPasswordSecret(referenced);
                } else {
                    String password = optString(body, "password");
                    if (password == null || password.isBlank()) {
                        throw new RuntimeException("The password/token is required.");
                    }
                    record.setPassword(password);
                }
                record.save();
                o.addProperty("id", record.getId());
            } else {
                throw new RuntimeException("Unknown credential type: " + type);
            }
            o.addProperty("ok", true);
            o.addProperty("type", type.toUpperCase());
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleGetSecretProviders(RequestContext req, HttpServletResponse resp) {
        try {
            JsonArray providers = new JsonArray();
            for (ManagedSecretProvider provider : context.getSecretProviderManager().getProviders()) {
                JsonObject po = new JsonObject();
                po.addProperty("name", provider.getResource().name());
                JsonArray secrets = new JsonArray();
                try {
                    // ManagedSecretProvider extends SecretProvider, so list() is available directly.
                    for (String s : provider.list()) {
                        secrets.add(s);
                    }
                } catch (Exception e) {
                    // A provider that can't list (or doesn't support it) shouldn't blank the picker.
                    po.addProperty("error", e.getMessage() == null ? e.toString() : e.getMessage());
                }
                po.add("secrets", secrets);
                providers.add(po);
            }
            JsonObject o = new JsonObject();
            o.add("providers", providers);
            return o.toString();
        } catch (Exception e) {
            // Degrade to inline-only rather than 500 the drawer.
            logger.warn("Could not list secret providers", e);
            JsonObject o = new JsonObject();
            o.add("providers", new JsonArray());
            return o.toString();
        }
    }

    private Object handleDeinit(RequestContext req, HttpServletResponse resp) {
        try {
            DataDirGitManager.deleteRepo();
            return ok();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private static String optString(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull()
                ? body.get(key).getAsString() : null;
    }

    private static long optLong(JsonObject body, String key) {
        return body != null && body.has(key) && !body.get(key).isJsonNull()
                ? body.get(key).getAsLong() : 0L;
    }

    private static String ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o.toString();
    }

    private Object handleRestore(RequestContext req, HttpServletResponse resp) {
        try {
            JsonObject body = new Gson().fromJson(req.readBody(), JsonObject.class);
            String hash = body != null && body.has("hash") && !body.get("hash").isJsonNull()
                    ? body.get("hash").getAsString() : null;
            if (hash == null || hash.isEmpty()) {
                throw new RuntimeException("Missing commit hash.");
            }
            DataDirGitManager.restoreToCommit(hash, req.getActor());
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("applied", true);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private Object handleInit(RequestContext req, HttpServletResponse resp) {
        try {
            DataDirGitManager.initRepo();
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("initialized", true);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    /**
     * Fetch the configured remote and bring config to its HEAD — used to pull committed changes,
     * and to re-attach + recover after a gateway-backup restore (which drops {@code .git} but keeps
     * the remote record). See {@link DataDirGitManager#updateFromRemote()}.
     */
    private Object handleUpdateFromRemote(RequestContext req, HttpServletResponse resp) {
        try {
            String hash = DataDirGitManager.updateFromRemote();
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("hash", hash);
            return o.toString();
        } catch (Exception e) {
            return error(resp, e);
        }
    }

    private String error(HttpServletResponse resp, Exception e) {
        logger.error("Config-versioning route error", e);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonObject o = new JsonObject();
        o.addProperty("error", e.getMessage() == null ? e.toString() : e.getMessage());
        return o.toString();
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
