package com.operametrix.ignition.git.ext;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.DesignerContext;

/**
 * Designer-scope extension point for optional add-on features that plug into
 * {@link com.operametrix.ignition.git.DesignerHook} without the open core depending on them.
 *
 * <p>Same one-way-dependency contract as {@link GatewayFeature}: implementations live in a separate
 * (proprietary) subproject that depends on this open-core module and are discovered at runtime via
 * {@link java.util.ServiceLoader} (see {@link DesignerFeatures}). An open-core-only build finds none
 * and behaves as the free edition. Implementations gate themselves on the license state.
 */
public interface DesignerFeature {

    /** Stable identifier for logging and license-entitlement mapping, e.g. {@code "devops.pipelines"}. */
    String id();

    /**
     * Mirrors {@code DesignerHook.startup} — add status-bar controls, dockable panels, menu actions.
     * Runs after the open core's own UI has been built.
     */
    default void startup(DesignerContext context, LicenseState license) {}

    /** Mirrors {@code DesignerHook.shutdown} — remove UI and release resources. Must not throw. */
    default void shutdown() {}
}
