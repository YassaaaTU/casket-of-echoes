package com.soulsvault.config;

import com.soulsvault.Constants;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Platform-independent, server-authoritative gameplay configuration for Casket of Echoes.
 * <p>
 * Backed by a server config on Forge ({@code <world>/serverconfig/soulsvault-server.toml}, with modpack
 * defaults shipped in {@code defaultconfigs/soulsvault-server.toml}) and synced to every client so both
 * sides always agree. These values are intentionally read-only in game: the modpack / server decides them,
 * and they can be edited through config GUI mods (Configured, Config Menus for Forge) or the file itself.
 */
public final class SoulsVaultConfig {

    /** How remote (away from a Rest Point) Vault access is paid for, once per Casket session. */
    public enum RemoteCostType {
        NONE,
        XP_LEVELS,
        XP_POINTS,
        ITEM
    }

    /** Which party system defines who shares a Casket and shows up on the party HUD. */
    public enum PartyProviderType {
        /** FTB Teams party teams, else Open Parties and Claims, else built-in parties. */
        AUTO,
        BUILTIN,
        FTB_TEAMS,
        OPAC,
        VANILLA_TEAMS,
        NONE
    }

    /** Server rule for a party option: let each party leader decide, or force it. */
    public enum PartyRule {
        LEADER_CHOICE,
        ALWAYS,
        NEVER;

        /**
         * The effective value for a party whose leader chose {@code leaderChoice}.
         */
        public boolean resolve(boolean leaderChoice) {
            return switch (this) {
                case LEADER_CHOICE -> leaderChoice;
                case ALWAYS -> true;
                case NEVER -> false;
            };
        }
    }

    /**
     * Whether the Casket replaces the vanilla inventory, and whether the Vault then works away from Rest Points.
     */
    public record Inventory(boolean fullInventoryReplacement, boolean allowRemoteVaultInFullInventory) {
        public static final Inventory DEFAULT = new Inventory(true, true);
    }

    /**
     * @param maxUniqueEntries distinct items a Casket may hold ({@code <= 0}: unlimited)
     * @param maxItemNbtBytes  largest item NBT accepted, in bytes ({@code <= 0}: unlimited)
     */
    public record Vault(boolean overflowOnFullInventory, long maxStackCapacity, int maxUniqueEntries, int maxItemNbtBytes,
                        boolean requireAttunement) {
        public static final Vault DEFAULT = new Vault(true, Constants.DEFAULT_MAX_STACK_CAPACITY, 0, 65536, false);
    }

    /**
     * Rest Point reach and which actions need one.
     */
    public record RestPoints(
            double reachBlocks,
            boolean replenishRequiresRestPoint,
            boolean echoSwapRequiresRestPoint,
            boolean replenishOnWakeUp
    ) {
        public static final RestPoints DEFAULT = new RestPoints(6.0D, false, false, true);
    }

    /**
     * Build Echo slots every player has, and the most Echo Pouches can raise them to.
     */
    public record Echoes(int baseSlots, int maxSlots) {
        public static final Echoes DEFAULT = new Echoes(Constants.MAX_BUILD_ECHOES, 10);
    }

    /**
     * Vestige Moth cost and Spectral Casket lifetime.
     */
    public record FieldAccess(int vestigeMothXpLevelCost, int spectralCasketLifetimeSeconds) {
        public static final FieldAccess DEFAULT = new FieldAccess(1, 60);
    }

    /**
     * @param lockSecondsAfterDamage seconds after dealing/taking damage during which the Vault is locked (0: off)
     * @param hostileRadius          hostile mobs within this many blocks lock the Vault (0: off)
     * @param bossRadius             a living boss within this many blocks locks the Vault (0: off)
     */
    public record Combat(int lockSecondsAfterDamage, double hostileRadius, double bossRadius) {
        public static final Combat DEFAULT = new Combat(0, 0.0D, 0.0D);

        public boolean isEnabled() {
            return this.lockSecondsAfterDamage > 0 || this.hostileRadius > 0.0D || this.bossRadius > 0.0D;
        }
    }

    /**
     * What opening the Vault away from a Rest Point costs, once per Casket session.
     */
    public record RemoteAccess(RemoteCostType costType, int costAmount, String costItem) {
        public static final RemoteAccess DEFAULT = new RemoteAccess(RemoteCostType.NONE, 1, "minecraft:ender_pearl");

        public boolean hasCost() {
            return this.costType != RemoteCostType.NONE && this.costAmount > 0;
        }
    }

    /**
     * What each Reliquary Shard adds to a Casket, and how many may be applied.
     */
    public record Upgrades(long capacityPerExpansion, int uniqueEntriesPerExpansion, int maxExpansions) {
        public static final Upgrades DEFAULT = new Upgrades(1000L, 25, 10);
    }

    /**
     * Equip Load capacity and the optional overload slowdown.
     */
    public record EquipLoad(boolean enabled, double baseCapacity, boolean slowdownWhenOverloaded, double overloadedSpeedPenalty) {
        public static final EquipLoad DEFAULT = new EquipLoad(true, 60.0D, false, 0.3D);
    }

    /**
     * Party provider, size and the server rules for each leader option.
     */
    public record Party(
            PartyProviderType provider,
            boolean builtinPartiesEnabled,
            int maxMembers,
            PartyRule sharedCasket,
            PartyRule preventFriendlyFire,
            boolean protectOwnPets,
            PartyRule shareAdvancements,
            PartyRule shareQuests,
            boolean allowCasketMerge,
            boolean allowCasketOverride,
            boolean returnContributionsOnLeave,
            int upgradeCreditMinutes
    ) {
        public static final Party DEFAULT = new Party(
                PartyProviderType.AUTO,
                true,
                4,
                PartyRule.LEADER_CHOICE,
                PartyRule.LEADER_CHOICE,
                true,
                PartyRule.LEADER_CHOICE,
                PartyRule.LEADER_CHOICE,
                true,
                true,
                true,
                10
        );
    }

    /**
     * Private Keepsakes: {@code slots} kinds of items, each up to {@code baseStacks} stacks
     * ({@code upgradedStacks} after a second Keepsake Coffer).
     */
    public record Keepsakes(boolean requireUnlock, int slots, int baseStacks, int upgradedStacks) {
        public static final Keepsakes DEFAULT = new Keepsakes(true, 9, 2, 4);
    }

    /** Full server settings snapshot, swapped atomically on load / reload / sync. */
    public record Settings(
            Inventory inventory,
            Vault vault,
            RestPoints restPoints,
            Echoes echoes,
            FieldAccess fieldAccess,
            Combat combat,
            RemoteAccess remoteAccess,
            Upgrades upgrades,
            EquipLoad equipLoad,
            Party party,
            Keepsakes keepsakes
    ) {
        public static final Settings DEFAULT = new Settings(
                Inventory.DEFAULT,
                Vault.DEFAULT,
                RestPoints.DEFAULT,
                Echoes.DEFAULT,
                FieldAccess.DEFAULT,
                Combat.DEFAULT,
                RemoteAccess.DEFAULT,
                Upgrades.DEFAULT,
                EquipLoad.DEFAULT,
                Party.DEFAULT,
                Keepsakes.DEFAULT
        );

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(this.inventory.fullInventoryReplacement());
            buf.writeBoolean(this.inventory.allowRemoteVaultInFullInventory());

            buf.writeBoolean(this.vault.overflowOnFullInventory());
            buf.writeVarLong(this.vault.maxStackCapacity());
            buf.writeVarInt(this.vault.maxUniqueEntries());
            buf.writeVarInt(this.vault.maxItemNbtBytes());
            buf.writeBoolean(this.vault.requireAttunement());

            buf.writeDouble(this.restPoints.reachBlocks());
            buf.writeBoolean(this.restPoints.replenishRequiresRestPoint());
            buf.writeBoolean(this.restPoints.echoSwapRequiresRestPoint());
            buf.writeBoolean(this.restPoints.replenishOnWakeUp());

            buf.writeVarInt(this.echoes.baseSlots());
            buf.writeVarInt(this.echoes.maxSlots());

            buf.writeVarInt(this.fieldAccess.vestigeMothXpLevelCost());
            buf.writeVarInt(this.fieldAccess.spectralCasketLifetimeSeconds());

            buf.writeVarInt(this.combat.lockSecondsAfterDamage());
            buf.writeDouble(this.combat.hostileRadius());
            buf.writeDouble(this.combat.bossRadius());

            buf.writeEnum(this.remoteAccess.costType());
            buf.writeVarInt(this.remoteAccess.costAmount());
            buf.writeUtf(this.remoteAccess.costItem(), 256);

            buf.writeVarLong(this.upgrades.capacityPerExpansion());
            buf.writeVarInt(this.upgrades.uniqueEntriesPerExpansion());
            buf.writeVarInt(this.upgrades.maxExpansions());

            buf.writeBoolean(this.equipLoad.enabled());
            buf.writeDouble(this.equipLoad.baseCapacity());
            buf.writeBoolean(this.equipLoad.slowdownWhenOverloaded());
            buf.writeDouble(this.equipLoad.overloadedSpeedPenalty());

            buf.writeEnum(this.party.provider());
            buf.writeBoolean(this.party.builtinPartiesEnabled());
            buf.writeVarInt(this.party.maxMembers());
            buf.writeEnum(this.party.sharedCasket());
            buf.writeEnum(this.party.preventFriendlyFire());
            buf.writeBoolean(this.party.protectOwnPets());
            buf.writeEnum(this.party.shareAdvancements());
            buf.writeEnum(this.party.shareQuests());
            buf.writeBoolean(this.party.allowCasketMerge());
            buf.writeBoolean(this.party.allowCasketOverride());
            buf.writeBoolean(this.party.returnContributionsOnLeave());
            buf.writeVarInt(this.party.upgradeCreditMinutes());

            buf.writeBoolean(this.keepsakes.requireUnlock());
            buf.writeVarInt(this.keepsakes.slots());
            buf.writeVarInt(this.keepsakes.baseStacks());
            buf.writeVarInt(this.keepsakes.upgradedStacks());
        }

        public static Settings decode(FriendlyByteBuf buf) {
            Inventory inventory = new Inventory(buf.readBoolean(), buf.readBoolean());
            Vault vault = new Vault(buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
            RestPoints restPoints = new RestPoints(buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
            Echoes echoes = new Echoes(buf.readVarInt(), buf.readVarInt());
            FieldAccess fieldAccess = new FieldAccess(buf.readVarInt(), buf.readVarInt());
            Combat combat = new Combat(buf.readVarInt(), buf.readDouble(), buf.readDouble());
            RemoteAccess remoteAccess = new RemoteAccess(buf.readEnum(RemoteCostType.class), buf.readVarInt(), buf.readUtf(256));
            Upgrades upgrades = new Upgrades(buf.readVarLong(), buf.readVarInt(), buf.readVarInt());
            EquipLoad equipLoad = new EquipLoad(buf.readBoolean(), buf.readDouble(), buf.readBoolean(), buf.readDouble());
            Party party = new Party(
                    buf.readEnum(PartyProviderType.class),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readEnum(PartyRule.class),
                    buf.readEnum(PartyRule.class),
                    buf.readBoolean(),
                    buf.readEnum(PartyRule.class),
                    buf.readEnum(PartyRule.class),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt()
            );
            Keepsakes keepsakes = new Keepsakes(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            return new Settings(inventory, vault, restPoints, echoes, fieldAccess, combat, remoteAccess, upgrades, equipLoad, party,
                    keepsakes);
        }
    }

    private static volatile Settings settings = Settings.DEFAULT;

    private SoulsVaultConfig() {
    }

    /**
     * The current settings (local config on the server, the server's synced copy on clients).
     */
    public static Settings get() {
        return settings;
    }

    /**
     * Applies values from the loader's config backend (on load / reload) or from the server's
     * config sync packet (on clients).
     */
    public static void apply(Settings newSettings) {
        settings = newSettings != null ? newSettings : Settings.DEFAULT;
    }

    public static Inventory inventory() {
        return settings.inventory();
    }

    public static Vault vault() {
        return settings.vault();
    }

    public static RestPoints restPoints() {
        return settings.restPoints();
    }

    public static Echoes echoes() {
        return settings.echoes();
    }

    public static FieldAccess fieldAccess() {
        return settings.fieldAccess();
    }

    public static Combat combat() {
        return settings.combat();
    }

    public static RemoteAccess remoteAccess() {
        return settings.remoteAccess();
    }

    public static Upgrades upgrades() {
        return settings.upgrades();
    }

    public static EquipLoad equipLoad() {
        return settings.equipLoad();
    }

    public static Party party() {
        return settings.party();
    }

    public static Keepsakes keepsakes() {
        return settings.keepsakes();
    }

    /**
     * When {@code true}, opening the player inventory ({@code E} key in Survival/Adventure)
     * replaces the vanilla InventoryScreen with the Souls-like Casket &amp; Inventory UI.
     */
    public static boolean isFullInventoryReplacement() {
        return settings.inventory().fullInventoryReplacement();
    }

    /**
     * When {@code true} and {@link #isFullInventoryReplacement()} is enabled, the player can
     * deposit and withdraw items from their Vault anywhere (not only at Rest Points).
     */
    public static boolean isAllowRemoteVaultInFullInventory() {
        return settings.inventory().allowRemoteVaultInFullInventory();
    }

    public static boolean isOverflowOnFullInventory() {
        return settings.vault().overflowOnFullInventory();
    }

    public static long getMaxStackCapacity() {
        return settings.vault().maxStackCapacity();
    }
}
