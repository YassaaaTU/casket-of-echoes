package com.soulsvault;

import com.soulsvault.api.SoulsVaultApi;
import com.soulsvault.core.echo.HotbarEchoSlotType;
import com.soulsvault.platform.Services;

public final class CommonClass {

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
