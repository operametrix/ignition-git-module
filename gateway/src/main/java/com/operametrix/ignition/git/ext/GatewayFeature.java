package com.operametrix.ignition.git.ext;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Gateway-scope extension point for optional add-on features (e.g. the closed-source DevOps
 * features) that plug into {@link com.operametrix.ignition.git.GatewayHook} without the open core
 * depending on them.
 *
 * <p>The dependency arrow points one way only: implementations live in a separate (proprietary)
 * subproject that depends on this open-core module; the open core never references implementations.
 * They are discovered at runtime via {@link java.util.ServiceLoader} (see {@link GatewayFeatures}),
 * so if no implementation is on the classpath the module builds and runs as the free edition.
 *
 * <p>Each lifecycle method mirrors the corresponding {@code GatewayHook} phase and receives exactly
 * what that phase has available. Implementations are responsible for their own license gating —
 * the open core invokes every discovered feature unconditionally; a feature that is not licensed
 * should no-op (typically by not registering its routes / UI).
 */
public interface GatewayFeature {

    /** Stable identifier for logging and license-entitlement mapping, e.g. {@code "devops.pipelines"}. */
    String id();

    /** Mirrors {@code GatewayHook.setup} — register resource types, nav pages, managers. */
    default void setup(GatewayContext context) {}

    /** Mirrors {@code GatewayHook.startup} — license state is now known; wire live behaviour. */
    default void startup(GatewayContext context, LicenseState license) {}

    /**
     * Mirrors {@code GatewayHook.notifyLicenseStateChanged} — the module's license changed at runtime
     * (e.g. a license key was activated without a restart). Re-evaluate entitlement here. Because
     * routes are mounted once, features should gate at request time against the latest license rather
     * than relying on conditional mounting.
     */
    default void licenseChanged(GatewayContext context, LicenseState license) {}

    /**
     * Mirrors {@code GatewayHook.mountRouteHandlers} — add REST routes to the module's route group
     * (already namespaced under {@code /data/git-config/…}). Prefix your routes to avoid collisions
     * with the open core's routes.
     */
    default void mountRoutes(GatewayContext context, RouteGroup routes) {}

    /** Mirrors {@code GatewayHook.shutdown} — release resources. Must not throw. */
    default void shutdown() {}
}
