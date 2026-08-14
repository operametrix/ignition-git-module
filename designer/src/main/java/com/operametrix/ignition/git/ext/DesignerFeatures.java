package com.operametrix.ignition.git.ext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers {@link DesignerFeature} implementations bundled into the same {@code .modl} via
 * {@link ServiceLoader}. Loaded once, lazily, off the module classloader — the pro subproject's
 * features are found while an open-core-only build finds none. Guarded so a bad extension degrades
 * to the free edition instead of breaking the Designer hook.
 */
public final class DesignerFeatures {

    private static final Logger log = LoggerFactory.getLogger(DesignerFeatures.class);

    private static volatile List<DesignerFeature> features;

    private DesignerFeatures() {}

    /** All discovered features (never null; empty on the free edition). Loaded once and cached. */
    public static List<DesignerFeature> all() {
        List<DesignerFeature> f = features;
        if (f == null) {
            synchronized (DesignerFeatures.class) {
                if (features == null) {
                    features = load();
                }
                f = features;
            }
        }
        return f;
    }

    public static boolean hasAny() {
        return !all().isEmpty();
    }

    private static List<DesignerFeature> load() {
        List<DesignerFeature> list = new ArrayList<>();
        try {
            ServiceLoader<DesignerFeature> loader =
                    ServiceLoader.load(DesignerFeature.class, DesignerFeatures.class.getClassLoader());
            for (DesignerFeature feature : loader) {
                list.add(feature);
                log.info("Discovered designer feature extension: {}", safeId(feature));
            }
        } catch (Throwable t) {
            log.error("Failed to load designer feature extensions; continuing without them.", t);
        }
        return List.copyOf(list);
    }

    private static String safeId(DesignerFeature feature) {
        try {
            return feature.id();
        } catch (Exception e) {
            return feature.getClass().getName();
        }
    }
}
