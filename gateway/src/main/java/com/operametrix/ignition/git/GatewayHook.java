package com.operametrix.ignition.git;

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

        // Create the resource handlers (DAOs) and publish them to the record façades.
        projectHandler = new GitProjectsConfigRecord.Handler(context);
        repoUserHandler = new GitReposUsersRecord.Handler(context);
        sshKeyHandler = new GitUserSshKeyRecord.Handler(context);
        httpsCredHandler = new GitUserHttpsCredentialRecord.Handler(context);
        remoteCredHandler = new GitRemoteCredentialsRecord.Handler(context);

        GitProjectsConfigRecord.setHandler(projectHandler);
        GitReposUsersRecord.setHandler(repoUserHandler);
        GitUserSshKeyRecord.setHandler(sshKeyHandler);
        GitUserHttpsCredentialRecord.setHandler(httpsCredHandler);
        GitRemoteCredentialsRecord.setHandler(remoteCredHandler);

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

        routes.newRoute("/restore").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleRestore).mount();

        routes.newRoute("/init").method(HttpMethod.POST).type(RouteGroup.TYPE_JSON)
                .requirePermission(PermissionType.WRITE)
                .handler(this::handleInit).mount();
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
            DataDirGitManager.initRepo(req.getActor());
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("initialized", true);
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
