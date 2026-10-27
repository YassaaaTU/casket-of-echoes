package com.soulsvault;

import com.soulsvault.api.SoulsVaultApi;
import com.soulsvault.core.echo.HotbarEchoSlotType;
import com.soulsvault.platform.Services;

/**
 * Loader-independent mod initialisation, called once from each loader's entry point.
 */
public final class CommonClass {

    /**
     * Logs the platform and registers the built-in Build Echo slot types. Runs during mod construction.
     */
    public static void init() {
        Constants.LOG.info("Initializing {} ({}) on {} in {} environment",
                Constants.MOD_NAME,
                Constants.MOD_ID,
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName());
        SoulsVaultApi.registerEchoSlotType(HotbarEchoSlotType.INSTANCE);
    }

    private CommonClass() {
    }
}
