package com.operametrix.ignition.git.ext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link GatewayFeature} implementations packed into the same {@code .modl} via
 * {@link ServiceLoader} (their {@code META-INF/services/com.operametrix.ignition.git.ext.GatewayFeature}
 * file). Loaded once, lazily, off the module classloader — so the pro subproject's features, bundled
 * into the same gateway classpath, are found while an open-core-only build finds none.
 *
 * <p>Discovery and each feature invocation are defensively guarded: a broken or missing extension
 * degrades to the free edition rather than taking down the module.
 */
public final class GatewayFeatures {

    private static final Logger log = LoggerFactory.getLogger(GatewayFeatures.class);

    private static volatile List<GatewayFeature> features;

    private GatewayFeatures() {}

    /** All discovered features (never null; empty on the free edition). Loaded once and cached. */
    public static List<GatewayFeature> all() {
        List<GatewayFeature> f = features;
        if (f == null) {
            synchronized (GatewayFeatures.class) {
                if (features == null) {
                    features = load();
                }
                f = features;
            }
        }
        return f;
    }

    /** True when at least one add-on feature is present — used to flip {@code isFreeModule()}. */
    public static boolean hasAny() {
        return !all().isEmpty();
    }

    private static List<GatewayFeature> load() {
        List<GatewayFeature> list = new ArrayList<>();
        try {
            ServiceLoader<GatewayFeature> loader =
                    ServiceLoader.load(GatewayFeature.class, GatewayFeatures.class.getClassLoader());
            for (GatewayFeature feature : loader) {
                list.add(feature);
                log.info("Discovered gateway feature extension: {}", safeId(feature));
            }
        } catch (Throwable t) {
            // Never let a bad extension break module load — fall back to the free edition.
            log.error("Failed to load gateway feature extensions; continuing without them.", t);
        }
        return List.copyOf(list);
    }

    private static String safeId(GatewayFeature feature) {
        try {
            return feature.id();
        } catch (Exception e) {
            return feature.getClass().getName();
        }
    }
}
