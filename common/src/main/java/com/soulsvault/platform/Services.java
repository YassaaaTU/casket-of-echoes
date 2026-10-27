package com.soulsvault.platform;

import com.soulsvault.Constants;
import com.soulsvault.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Loads the loader-specific implementations of common service interfaces through {@link ServiceLoader}.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * Loads the single implementation of {@code clazz} registered in {@code META-INF/services}.
     *
     * @throws NullPointerException when no implementation is registered (a broken jar)
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }

    private Services() {
    }
}
