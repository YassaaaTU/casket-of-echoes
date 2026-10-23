package com.soulsvault.forge.config;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge server config backing {@link SoulsVaultConfig}.
 * <p>
 * Registered as {@link ModConfig.Type#SERVER} ({@code <world>/serverconfig/soulsvault-server.toml}) so the
 * server is the single source of truth: Forge syncs it to clients on login, modpacks ship defaults in
 * {@code defaultconfigs/soulsvault-server.toml}, and config GUI mods (Configured, Config Menus for Forge)
 * pick it up automatically using the {@code config.soulsvault.*} translation keys. Reloads are pushed to
 * connected clients with {@link com.soulsvault.network.PacketS2CSyncConfig}.
 */
public final class ForgeSoulsVaultConfig {
    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.BooleanValue FULL_INVENTORY_REPLACEMENT;
    public static final ForgeConfigSpec.BooleanValue ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY;
    public static final ForgeConfigSpec.BooleanValue OVERFLOW_ON_FULL_INVENTORY;
    public static final ForgeConfigSpec.LongValue MAX_STACK_CAPACITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Inventory integration. Decided by the modpack/server; players cannot toggle these in game.")
                .translation("config.soulsvault.inventory")
                .push("inventory");

        FULL_INVENTORY_REPLACEMENT = builder
                .comment(
                        "If true, opening the player inventory (E) in Survival/Adventure mode replaces the vanilla",
                        "inventory with the Souls-like Inventory & Casket UI (including 3x3 Crafting, Curios, Aether,",
                        "and Cosmetic Armor slots). If false, the Casket UI only opens at Rest Points, Bottomless",
                        "Caskets, Spectral Caskets, or via the 'Open Casket' key while near a Rest Point."
                )
                .translation("config.soulsvault.inventory.fullInventoryReplacement")
                .define("fullInventoryReplacement", true);

        ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY = builder
                .comment(
                        "If true, players using fullInventoryReplacement can deposit/withdraw from their Vault anywhere.",
                        "If false, opening the Souls Inventory away from a Rest Point allows equipment, Curios, and 3x3",
                        "crafting, while Vault deposit/withdrawal still requires a Rest Point or Bottomless Casket."
                )
                .translation("config.soulsvault.inventory.allowRemoteVaultInFullInventory")
                .define("allowRemoteVaultInFullInventory", true);

        builder.pop();

        builder.comment("Vault storage rules.")
                .translation("config.soulsvault.vault")
                .push("vault");

        OVERFLOW_ON_FULL_INVENTORY = builder
                .comment("If true, world item pickups when the player's inventory is full are automatically routed to the Vault.")
                .translation("config.soulsvault.vault.overflowOnFullInventory")
                .define("overflowOnFullInventory", true);

        MAX_STACK_CAPACITY = builder
                .comment("Maximum count stored per unique item (item + NBT) inside a player's Vault.")
                .translation("config.soulsvault.vault.maxStackCapacity")
                .defineInRange("maxStackCapacity", Constants.DEFAULT_MAX_STACK_CAPACITY, 64L, Long.MAX_VALUE);

        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private ForgeSoulsVaultConfig() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, Constants.MOD_ID + "-server.toml");
        modEventBus.addListener(ForgeSoulsVaultConfig::onConfigLoad);
        modEventBus.addListener(ForgeSoulsVaultConfig::onConfigReload);
    }

    private static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            syncToCommon();
        }
    }

    private static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SERVER_SPEC) {
            return;
        }
        syncToCommon();
        // Reloads can come from the file watcher thread; hop onto the server thread before broadcasting.
        // On a remote client (receiving Forge's login sync) there is no server, so nothing is re-sent.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(ForgeNetworkHandler::broadcastConfigSync);
        }
    }

    private static void syncToCommon() {
        SoulsVaultConfig.bindValues(
                FULL_INVENTORY_REPLACEMENT.get(),
                ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY.get(),
                OVERFLOW_ON_FULL_INVENTORY.get(),
                MAX_STACK_CAPACITY.get()
        );
    }
}
