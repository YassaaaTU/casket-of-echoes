package com.soulsvault.forge.config;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.config.SoulsVaultClientConfig.HudAnchor;
import com.soulsvault.config.SoulsVaultClientConfig.HungerDisplay;
import com.soulsvault.config.SoulsVaultClientConfig.PartyHudStyle;
import com.soulsvault.config.SoulsVaultClientConfig.PortraitMode;
import com.soulsvault.config.SoulsVaultClientConfig.ToastPosition;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.config.SoulsVaultConfig.PartyRule;
import com.soulsvault.config.SoulsVaultConfig.RemoteCostType;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultSortMode;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.party.PartyService;
import com.soulsvault.world.inventory.SoulsVaultMenu.LeftPaneMode;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge config specs backing {@link SoulsVaultConfig} and {@link SoulsVaultClientConfig}.
 * <p>
 * The server spec is {@link ModConfig.Type#SERVER} ({@code <world>/serverconfig/soulsvault-server.toml}): the server
 * is the single source of truth, Forge syncs it on login, modpacks ship defaults in
 * {@code defaultconfigs/soulsvault-server.toml}, and reloads are pushed to clients with
 * {@link com.soulsvault.network.PacketS2CSyncConfig}. The client spec ({@code config/soulsvault-client.toml}) holds
 * personal HUD / toast / screen preferences. Both carry {@code config.soulsvault.*} translation keys so Configured
 * and Config Menus for Forge build clean GUIs automatically.
 */
public final class ForgeSoulsVaultConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // inventory
    private static final ForgeConfigSpec.BooleanValue FULL_INVENTORY_REPLACEMENT;
    private static final ForgeConfigSpec.BooleanValue ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY;
    // vault
    private static final ForgeConfigSpec.BooleanValue OVERFLOW_ON_FULL_INVENTORY;
    private static final ForgeConfigSpec.LongValue MAX_STACK_CAPACITY;
    private static final ForgeConfigSpec.IntValue MAX_UNIQUE_ENTRIES;
    private static final ForgeConfigSpec.IntValue MAX_ITEM_NBT_BYTES;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_ATTUNEMENT;
    // rest points
    private static final ForgeConfigSpec.DoubleValue REST_REACH;
    private static final ForgeConfigSpec.BooleanValue REPLENISH_REQUIRES_REST_POINT;
    private static final ForgeConfigSpec.BooleanValue ECHO_SWAP_REQUIRES_REST_POINT;
    private static final ForgeConfigSpec.BooleanValue REPLENISH_ON_WAKE_UP;
    // echoes
    private static final ForgeConfigSpec.IntValue ECHO_BASE_SLOTS;
    private static final ForgeConfigSpec.IntValue ECHO_MAX_SLOTS;
    // field access
    private static final ForgeConfigSpec.IntValue VESTIGE_MOTH_XP_COST;
    private static final ForgeConfigSpec.IntValue SPECTRAL_CASKET_SECONDS;
    // combat
    private static final ForgeConfigSpec.IntValue COMBAT_LOCK_SECONDS;
    private static final ForgeConfigSpec.DoubleValue COMBAT_HOSTILE_RADIUS;
    private static final ForgeConfigSpec.DoubleValue COMBAT_BOSS_RADIUS;
    // remote access
    private static final ForgeConfigSpec.EnumValue<RemoteCostType> REMOTE_COST_TYPE;
    private static final ForgeConfigSpec.IntValue REMOTE_COST_AMOUNT;
    private static final ForgeConfigSpec.ConfigValue<String> REMOTE_COST_ITEM;
    // upgrades
    private static final ForgeConfigSpec.LongValue CAPACITY_PER_EXPANSION;
    private static final ForgeConfigSpec.IntValue UNIQUE_PER_EXPANSION;
    private static final ForgeConfigSpec.IntValue MAX_EXPANSIONS;
    // equip load
    private static final ForgeConfigSpec.BooleanValue EQUIP_LOAD_ENABLED;
    private static final ForgeConfigSpec.DoubleValue EQUIP_LOAD_BASE;
    private static final ForgeConfigSpec.BooleanValue EQUIP_LOAD_SLOWDOWN;
    private static final ForgeConfigSpec.DoubleValue EQUIP_LOAD_PENALTY;
    // party
    private static final ForgeConfigSpec.EnumValue<PartyProviderType> PARTY_PROVIDER;
    private static final ForgeConfigSpec.BooleanValue BUILTIN_PARTIES;
    private static final ForgeConfigSpec.IntValue PARTY_MAX_MEMBERS;
    private static final ForgeConfigSpec.EnumValue<PartyRule> PARTY_SHARED_CASKET;
    private static final ForgeConfigSpec.EnumValue<PartyRule> PARTY_FRIENDLY_FIRE;
    private static final ForgeConfigSpec.BooleanValue PARTY_PROTECT_OWN_PETS;
    private static final ForgeConfigSpec.EnumValue<PartyRule> PARTY_SHARE_ADVANCEMENTS;
    private static final ForgeConfigSpec.EnumValue<PartyRule> PARTY_SHARE_QUESTS;
    private static final ForgeConfigSpec.BooleanValue PARTY_ALLOW_MERGE;
    private static final ForgeConfigSpec.BooleanValue PARTY_ALLOW_OVERRIDE;
    private static final ForgeConfigSpec.BooleanValue PARTY_RETURN_ON_LEAVE;
    private static final ForgeConfigSpec.IntValue PARTY_UPGRADE_CREDIT_MINUTES;
    private static final ForgeConfigSpec.BooleanValue KEEPSAKES_REQUIRE_UNLOCK;
    private static final ForgeConfigSpec.IntValue KEEPSAKES_SLOTS;
    private static final ForgeConfigSpec.IntValue KEEPSAKES_BASE_STACKS;
    private static final ForgeConfigSpec.IntValue KEEPSAKES_UPGRADED_STACKS;

    // client
    private static final ForgeConfigSpec.BooleanValue TOASTS_ENABLED;
    private static final ForgeConfigSpec.EnumValue<ToastPosition> TOAST_POSITION;
    private static final ForgeConfigSpec.DoubleValue TOAST_DURATION;
    private static final ForgeConfigSpec.BooleanValue TOAST_SOUND;
    private static final ForgeConfigSpec.EnumValue<VaultSortMode> DEFAULT_SORT;
    private static final ForgeConfigSpec.BooleanValue REMEMBER_LAST_TAB;
    private static final ForgeConfigSpec.EnumValue<VaultCategory> LAST_CATEGORY;
    private static final ForgeConfigSpec.EnumValue<VaultSortMode> LAST_SORT;
    private static final ForgeConfigSpec.EnumValue<LeftPaneMode> LAST_LEFT_PANE;
    private static final ForgeConfigSpec.BooleanValue PARTY_HUD_VISIBLE;
    private static final ForgeConfigSpec.EnumValue<PartyHudStyle> PARTY_HUD_STYLE;
    private static final ForgeConfigSpec.EnumValue<HudAnchor> PARTY_HUD_ANCHOR;
    private static final ForgeConfigSpec.DoubleValue PARTY_HUD_SCALE;
    private static final ForgeConfigSpec.IntValue PARTY_HUD_FULL_ROWS;
    private static final ForgeConfigSpec.EnumValue<PortraitMode> PARTY_PORTRAIT;
    private static final ForgeConfigSpec.EnumValue<HungerDisplay> PARTY_HUNGER;
    private static final ForgeConfigSpec.BooleanValue PARTY_MARKERS;
    private static final ForgeConfigSpec.IntValue PARTY_MARKER_MIN_DISTANCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        section(b, "inventory", "Inventory integration. Decided by the modpack/server; players cannot toggle these in game.");
        FULL_INVENTORY_REPLACEMENT = b.comment(
                        "If true, opening the player inventory (E) in Survival/Adventure mode replaces the vanilla",
                        "inventory with the Souls-like Inventory & Casket UI (including 3x3 Crafting, Curios, Aether,",
                        "and Cosmetic Armor slots). If false, the Casket UI only opens at Rest Points, Bottomless",
                        "Caskets, Spectral Caskets, or via the 'Open Casket' key while near a Rest Point.")
                .translation(key("inventory.fullInventoryReplacement"))
                .define("fullInventoryReplacement", SoulsVaultConfig.Inventory.DEFAULT.fullInventoryReplacement());
        ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY = b.comment(
                        "If true, players using fullInventoryReplacement can deposit/withdraw from their Vault anywhere",
                        "(optionally for a cost, see remoteAccess). If false, opening the Souls Inventory away from a Rest",
                        "Point allows equipment, Curios and 3x3 crafting only.")
                .translation(key("inventory.allowRemoteVaultInFullInventory"))
                .define("allowRemoteVaultInFullInventory", SoulsVaultConfig.Inventory.DEFAULT.allowRemoteVaultInFullInventory());
        b.pop();

        section(b, "vault", "Vault storage rules. Also see the #soulsvault:vault_blacklist and #soulsvault:category/<tab> item tags.");
        OVERFLOW_ON_FULL_INVENTORY = b.comment("If true, world item pickups when the player's inventory is full are routed to the Vault.")
                .translation(key("vault.overflowOnFullInventory"))
                .define("overflowOnFullInventory", SoulsVaultConfig.Vault.DEFAULT.overflowOnFullInventory());
        MAX_STACK_CAPACITY = b.comment("Maximum count stored per unique item (item + NBT) in one Casket, before Reliquary Shards.")
                .translation(key("vault.maxStackCapacity"))
                .defineInRange("maxStackCapacity", SoulsVaultConfig.Vault.DEFAULT.maxStackCapacity(), 64L, Long.MAX_VALUE);
        MAX_UNIQUE_ENTRIES = b.comment("Maximum distinct items one Casket holds before Reliquary Shards (0 = unlimited).")
                .translation(key("vault.maxUniqueEntries"))
                .defineInRange("maxUniqueEntries", SoulsVaultConfig.Vault.DEFAULT.maxUniqueEntries(), 0, Integer.MAX_VALUE);
        MAX_ITEM_NBT_BYTES = b.comment(
                        "Items whose NBT is larger than this many bytes cannot be stored (book-ban protection; 0 = unlimited).",
                        "Normal heavily-modded gear (Apotheosis affixes, Armourer's Workshop skins) stays far below the default.")
                .translation(key("vault.maxItemNbtBytes"))
                .defineInRange("maxItemNbtBytes", SoulsVaultConfig.Vault.DEFAULT.maxItemNbtBytes(), 0, 1048576);
        REQUIRE_ATTUNEMENT = b.comment(
                        "Casket Attunement: the Casket stays sealed (no storage, Rest & Replenish, Build Echo swaps, pickup",
                        "overflow or Spectral Caskets) until the player uses a Casket Sigil or receives /casket attune,",
                        "e.g. as a quest reward. Players who already used their Casket before this was turned on stay attuned.")
                .translation(key("vault.requireAttunement"))
                .define("requireAttunement", SoulsVaultConfig.Vault.DEFAULT.requireAttunement());
        b.pop();

        section(b, "restPoints", "Rest Points (#soulsvault:rest_points blocks, Bottomless Caskets, Spectral Caskets).");
        REST_REACH = b.comment("How close (blocks) a player must be to a Rest Point to rest or open the Casket there.")
                .translation(key("restPoints.reachBlocks"))
                .defineInRange("reachBlocks", SoulsVaultConfig.RestPoints.DEFAULT.reachBlocks(), 1.0D, 32.0D);
        REPLENISH_REQUIRES_REST_POINT = b.comment("If true, Rest & Replenish only works when the Casket was opened at a Rest Point.")
                .translation(key("restPoints.replenishRequiresRestPoint"))
                .define("replenishRequiresRestPoint", SoulsVaultConfig.RestPoints.DEFAULT.replenishRequiresRestPoint());
        ECHO_SWAP_REQUIRES_REST_POINT = b.comment("If true, swapping Build Echoes only works when the Casket was opened at a Rest Point.")
                .translation(key("restPoints.echoSwapRequiresRestPoint"))
                .define("echoSwapRequiresRestPoint", SoulsVaultConfig.RestPoints.DEFAULT.echoSwapRequiresRestPoint());
        REPLENISH_ON_WAKE_UP = b.comment("If true, sleeping through the night counts as resting (Rest & Replenish on wake-up).")
                .translation(key("restPoints.replenishOnWakeUp"))
                .define("replenishOnWakeUp", SoulsVaultConfig.RestPoints.DEFAULT.replenishOnWakeUp());
        b.pop();

        section(b, "buildEchoes", "Build Echoes (saved loadouts).");
        ECHO_BASE_SLOTS = b.comment("Build Echo slots every player starts with.")
                .translation(key("buildEchoes.baseSlots"))
                .defineInRange("baseSlots", SoulsVaultConfig.Echoes.DEFAULT.baseSlots(), 1, Constants.ABSOLUTE_MAX_BUILD_ECHOES);
        ECHO_MAX_SLOTS = b.comment("Maximum Build Echo slots reachable with Echo Pouches.")
                .translation(key("buildEchoes.maxSlots"))
                .defineInRange("maxSlots", SoulsVaultConfig.Echoes.DEFAULT.maxSlots(), 1, Constants.ABSOLUTE_MAX_BUILD_ECHOES);
        b.pop();

        section(b, "fieldAccess", "Vestige Moth and Spectral Casket (temporary field access).");
        VESTIGE_MOTH_XP_COST = b.comment("Experience levels consumed when summoning a Spectral Casket with a Vestige Moth.")
                .translation(key("fieldAccess.vestigeMothXpLevelCost"))
                .defineInRange("vestigeMothXpLevelCost", SoulsVaultConfig.FieldAccess.DEFAULT.vestigeMothXpLevelCost(), 0, 100);
        SPECTRAL_CASKET_SECONDS = b.comment("How long a summoned Spectral Casket lasts, in seconds.")
                .translation(key("fieldAccess.spectralCasketLifetimeSeconds"))
                .defineInRange("spectralCasketLifetimeSeconds", SoulsVaultConfig.FieldAccess.DEFAULT.spectralCasketLifetimeSeconds(), 5, 3600);
        b.pop();

        section(b, "combat", "Combat lock: no resting, Vault access or Echo swaps while in combat. The inventory itself stays usable.",
                "All values 0 = off. Bosses are entities in the #soulsvault:bosses entity tag.");
        COMBAT_LOCK_SECONDS = b.comment("Seconds after dealing or taking damage during which the Vault is locked.")
                .translation(key("combat.lockSecondsAfterDamage"))
                .defineInRange("lockSecondsAfterDamage", SoulsVaultConfig.Combat.DEFAULT.lockSecondsAfterDamage(), 0, 600);
        COMBAT_HOSTILE_RADIUS = b.comment("Hostile mobs within this many blocks lock the Vault.")
                .translation(key("combat.hostileRadius"))
                .defineInRange("hostileRadius", SoulsVaultConfig.Combat.DEFAULT.hostileRadius(), 0.0D, 64.0D);
        COMBAT_BOSS_RADIUS = b.comment("A living boss within this many blocks locks the Vault.")
                .translation(key("combat.bossRadius"))
                .defineInRange("bossRadius", SoulsVaultConfig.Combat.DEFAULT.bossRadius(), 0.0D, 256.0D);
        b.pop();

        section(b, "remoteAccess", "Cost of using the Vault away from a Rest Point (allowRemoteVaultInFullInventory).",
                "Charged once per Casket session from an 'Unlock' button in the Vault pane; opening the inventory stays free.");
        REMOTE_COST_TYPE = b.comment("NONE, XP_LEVELS, XP_POINTS or ITEM.")
                .translation(key("remoteAccess.costType"))
                .defineEnum("costType", SoulsVaultConfig.RemoteAccess.DEFAULT.costType());
        REMOTE_COST_AMOUNT = b.comment("Levels, points or items charged per session.")
                .translation(key("remoteAccess.costAmount"))
                .defineInRange("costAmount", SoulsVaultConfig.RemoteAccess.DEFAULT.costAmount(), 0, 10000);
        REMOTE_COST_ITEM = b.comment("Item id charged when costType = ITEM.")
                .translation(key("remoteAccess.costItem"))
                .define("costItem", SoulsVaultConfig.RemoteAccess.DEFAULT.costItem());
        b.pop();

        section(b, "upgrades", "Reliquary Shard expansions (per Casket; a party's shared Casket has its own).");
        CAPACITY_PER_EXPANSION = b.comment("Extra items per kind each Reliquary Shard adds.")
                .translation(key("upgrades.capacityPerExpansion"))
                .defineInRange("capacityPerExpansion", SoulsVaultConfig.Upgrades.DEFAULT.capacityPerExpansion(), 0L, Long.MAX_VALUE / 1024L);
        UNIQUE_PER_EXPANSION = b.comment("Extra distinct items each Reliquary Shard adds (only when vault.maxUniqueEntries > 0).")
                .translation(key("upgrades.uniqueEntriesPerExpansion"))
                .defineInRange("uniqueEntriesPerExpansion", SoulsVaultConfig.Upgrades.DEFAULT.uniqueEntriesPerExpansion(), 0, 100000);
        MAX_EXPANSIONS = b.comment("Maximum Reliquary Shards one Casket accepts.")
                .translation(key("upgrades.maxExpansions"))
                .defineInRange("maxExpansions", SoulsVaultConfig.Upgrades.DEFAULT.maxExpansions(), 0, 1000);
        b.pop();

        section(b, "equipLoad", "Equip Load meter. Weights: datapack files in data/<ns>/soulsvault/equip_load/, then Epic Fight",
                "weight, then armor/attack stats. Capacity: baseCapacity + the soulsvault:equip_load_bonus attribute.");
        EQUIP_LOAD_ENABLED = b.comment("Show the Equip Load meter in the Gear pane.")
                .translation(key("equipLoad.enabled"))
                .define("enabled", SoulsVaultConfig.EquipLoad.DEFAULT.enabled());
        EQUIP_LOAD_BASE = b.comment("Base carrying capacity.")
                .translation(key("equipLoad.baseCapacity"))
                .defineInRange("baseCapacity", SoulsVaultConfig.EquipLoad.DEFAULT.baseCapacity(), 1.0D, 10000.0D);
        EQUIP_LOAD_SLOWDOWN = b.comment("Slow players down while overloaded. Leave off with Epic Fight, which already penalises weight.")
                .translation(key("equipLoad.slowdownWhenOverloaded"))
                .define("slowdownWhenOverloaded", SoulsVaultConfig.EquipLoad.DEFAULT.slowdownWhenOverloaded());
        EQUIP_LOAD_PENALTY = b.comment("Movement speed reduction while overloaded (0.3 = 30% slower).")
                .translation(key("equipLoad.overloadedSpeedPenalty"))
                .defineInRange("overloadedSpeedPenalty", SoulsVaultConfig.EquipLoad.DEFAULT.overloadedSpeedPenalty(), 0.0D, 0.95D);
        b.pop();

        section(b, "party", "Parties and Covenant Caskets. Rules: LEADER_CHOICE (party leader decides in the party GUI),",
                "ALWAYS or NEVER (forced for every party).");
        PARTY_PROVIDER = b.comment("AUTO (FTB Teams party teams, else Open Parties and Claims, else built-in), BUILTIN, FTB_TEAMS,",
                        "OPAC, VANILLA_TEAMS (/team; operators manage options) or NONE.")
                .translation(key("party.provider"))
                .defineEnum("provider", SoulsVaultConfig.Party.DEFAULT.provider());
        BUILTIN_PARTIES = b.comment("Allow Casket of Echoes' own parties (party GUI, /casket party).")
                .translation(key("party.builtinPartiesEnabled"))
                .define("builtinPartiesEnabled", SoulsVaultConfig.Party.DEFAULT.builtinPartiesEnabled());
        PARTY_MAX_MEMBERS = b.comment("Maximum members of a built-in party.")
                .translation(key("party.maxMembers"))
                .defineInRange("maxMembers", SoulsVaultConfig.Party.DEFAULT.maxMembers(), 2, 64);
        PARTY_SHARED_CASKET = b.comment("Covenant Casket: party members share one Casket instead of private ones.")
                .translation(key("party.sharedCasket"))
                .defineEnum("sharedCasket", SoulsVaultConfig.Party.DEFAULT.sharedCasket());
        PARTY_FRIENDLY_FIRE = b.comment("Prevent party members, their pets, mounts (e.g. Book of Dragons) and projectiles hurting each other.")
                .translation(key("party.preventFriendlyFire"))
                .defineEnum("preventFriendlyFire", SoulsVaultConfig.Party.DEFAULT.preventFriendlyFire());
        PARTY_PROTECT_OWN_PETS = b.comment("Owners can't hurt their own tamed pets and mounts unless sneaking, party or not: covers",
                        "Epic Fight battle-mode swings, sweeps, stray arrows and spells (Domestication Innovation's",
                        "swing-through only covers vanilla-mode clicks).")
                .translation(key("party.protectOwnPets"))
                .define("protectOwnPets", SoulsVaultConfig.Party.DEFAULT.protectOwnPets());
        PARTY_SHARE_ADVANCEMENTS = b.comment("Grant advancements earned by one online member to the other online members.")
                .translation(key("party.shareAdvancements"))
                .defineEnum("shareAdvancements", SoulsVaultConfig.Party.DEFAULT.shareAdvancements());
        PARTY_SHARE_QUESTS = b.comment("Complete FTB Quests quests for the other online members. FTB Teams party teams always share",
                        "quests (FTB's own design), so this only matters for other providers.")
                .translation(key("party.shareQuests"))
                .defineEnum("shareQuests", SoulsVaultConfig.Party.DEFAULT.shareQuests());
        PARTY_ALLOW_MERGE = b.comment("Allow merging a private Casket into a party Casket (on join or later).")
                .translation(key("party.allowCasketMerge"))
                .define("allowCasketMerge", SoulsVaultConfig.Party.DEFAULT.allowCasketMerge());
        PARTY_ALLOW_OVERRIDE = b.comment("Allow deleting a private Casket to join a party (always confirmed in the GUI).")
                .translation(key("party.allowCasketOverride"))
                .define("allowCasketOverride", SoulsVaultConfig.Party.DEFAULT.allowCasketOverride());
        PARTY_RETURN_ON_LEAVE = b.comment("If true, a member who leaves (or is kicked) gets back the items they still own in the",
                        "Covenant Casket. If false, those items stay with the party. On disband or when sharing is turned",
                        "off, everyone always gets their items back.")
                .translation(key("party.returnContributionsOnLeave"))
                .define("returnContributionsOnLeave", SoulsVaultConfig.Party.DEFAULT.returnContributionsOnLeave());
        PARTY_UPGRADE_CREDIT_MINUTES = b.comment("Minutes a member must have been in the Covenant for a Reliquary Shard used on it to",
                        "also count for their own Casket when they leave (stops join-right-before-the-upgrade farming).")
                .translation(key("party.upgradeCreditMinutes"))
                .defineInRange("upgradeCreditMinutes", SoulsVaultConfig.Party.DEFAULT.upgradeCreditMinutes(), 0, 10080);
        b.pop();

        section(b, "keepsakes", "Keepsakes: a small private stash beside the Casket, never shared with a party's Covenant Casket.",
                "Same access rules as the Vault (attunement, Rest Points or the remote cost, combat lock).");
        KEEPSAKES_REQUIRE_UNLOCK = b.comment("If true, Keepsakes stay locked until the player uses a Keepsake Coffer.")
                .translation(key("keepsakes.requireUnlock"))
                .define("requireUnlock", SoulsVaultConfig.Keepsakes.DEFAULT.requireUnlock());
        KEEPSAKES_SLOTS = b.comment("Different items Keepsakes hold.")
                .translation(key("keepsakes.slots"))
                .defineInRange("slots", SoulsVaultConfig.Keepsakes.DEFAULT.slots(), 1, 42);
        KEEPSAKES_BASE_STACKS = b.comment("Stacks of each item (2 = 128 of a 64-stack item, 2 of an unstackable one).")
                .translation(key("keepsakes.baseStacks"))
                .defineInRange("baseStacks", SoulsVaultConfig.Keepsakes.DEFAULT.baseStacks(), 1, 64);
        KEEPSAKES_UPGRADED_STACKS = b.comment("Stacks of each item after a second Keepsake Coffer is used.")
                .translation(key("keepsakes.upgradedStacks"))
                .defineInRange("upgradedStacks", SoulsVaultConfig.Keepsakes.DEFAULT.upgradedStacks(), 1, 64);
        b.pop();

        SERVER_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        SoulsVaultClientConfig.Settings clientDefaults = SoulsVaultClientConfig.Settings.DEFAULT;
        section(c, "toasts", "Pickup overflow / replenish banners.");
        TOASTS_ENABLED = c.translation(key("toasts.enabled")).define("enabled", clientDefaults.toastsEnabled());
        TOAST_POSITION = c.translation(key("toasts.position")).defineEnum("position", clientDefaults.toastPosition());
        TOAST_DURATION = c.translation(key("toasts.durationSeconds"))
                .defineInRange("durationSeconds", clientDefaults.toastDurationSeconds(), 0.5D, 30.0D);
        TOAST_SOUND = c.translation(key("toasts.sound")).define("sound", clientDefaults.toastSound());
        c.pop();

        section(c, "casketScreen", "Casket screen preferences.");
        DEFAULT_SORT = c.comment("Sort mode used when rememberLastTab is off.")
                .translation(key("casketScreen.defaultSortMode")).defineEnum("defaultSortMode", clientDefaults.defaultSortMode());
        REMEMBER_LAST_TAB = c.comment("Reopen the Casket on the last category tab and sort mode.")
                .translation(key("casketScreen.rememberLastTab")).define("rememberLastTab", clientDefaults.rememberLastTab());
        LAST_CATEGORY = c.comment("Remembered automatically.").translation(key("casketScreen.lastCategory"))
                .defineEnum("lastCategory", clientDefaults.lastCategory());
        LAST_SORT = c.comment("Remembered automatically.").translation(key("casketScreen.lastSortMode"))
                .defineEnum("lastSortMode", clientDefaults.lastSortMode());
        LAST_LEFT_PANE = c.comment("Left pane (Gear & Curios, 3x3 Craft, Echoes) reopened when rememberLastTab is on. Remembered automatically.")
                .translation(key("casketScreen.lastLeftPane")).defineEnum("lastLeftPane", clientDefaults.lastLeftPane());
        c.pop();

        section(c, "partyHud", "Party member frames (also changeable in the party GUI).");
        PARTY_HUD_VISIBLE = c.translation(key("partyHud.visible")).define("visible", clientDefaults.partyHudVisible());
        PARTY_HUD_STYLE = c.comment("PORTRAIT, BARS or MINIMAL.").translation(key("partyHud.style"))
                .defineEnum("style", clientDefaults.partyHudStyle());
        PARTY_HUD_ANCHOR = c.translation(key("partyHud.anchor")).defineEnum("anchor", clientDefaults.partyHudAnchor());
        PARTY_HUD_SCALE = c.translation(key("partyHud.scale")).defineInRange("scale", clientDefaults.partyHudScale(), 0.5D, 2.0D);
        PARTY_HUD_FULL_ROWS = c.comment("Teammates shown in the chosen style; any more use compact one-line rows.")
                .translation(key("partyHud.fullRows")).defineInRange("fullRows", clientDefaults.partyHudFullRows(), 0, 12);
        PARTY_PORTRAIT = c.comment("MODEL (static 3D model with skin, armor and held weapons) or FACE.")
                .translation(key("partyHud.portrait")).defineEnum("portrait", clientDefaults.partyPortrait());
        PARTY_HUNGER = c.comment("Hunger bar in party frames: AUTO (hidden when HungerStrike removes hunger), SHOW or HIDE.")
                .translation(key("partyHud.hunger")).defineEnum("hunger", clientDefaults.partyHunger());
        PARTY_MARKERS = c.comment("Show party members' heads, names and distance in the world (through walls).")
                .translation(key("partyHud.markers")).define("markers", clientDefaults.partyMarkers());
        PARTY_MARKER_MIN_DISTANCE = c.comment("Hide a member's marker when they are closer than this many blocks.")
                .translation(key("partyHud.markerMinDistance"))
                .defineInRange("markerMinDistance", clientDefaults.partyMarkerMinDistance(), 0, 256);
        c.pop();

        CLIENT_SPEC = c.build();
    }

    private ForgeSoulsVaultConfig() {
    }

    private static String key(String path) {
        return "config." + Constants.MOD_ID + "." + path;
    }

    private static void section(ForgeConfigSpec.Builder builder, String name, String... comment) {
        builder.comment(comment).translation(key(name)).push(name);
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, Constants.MOD_ID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, Constants.MOD_ID + "-client.toml");
        modEventBus.addListener(ForgeSoulsVaultConfig::onConfigLoad);
        modEventBus.addListener(ForgeSoulsVaultConfig::onConfigReload);
    }

    private static void onConfigLoad(final ModConfigEvent.Loading event) {
        apply(event.getConfig().getSpec());
    }

    private static void onConfigReload(final ModConfigEvent.Reloading event) {
        apply(event.getConfig().getSpec());
        if (event.getConfig().getSpec() != SERVER_SPEC) {
            return;
        }
        // Reloads can come from the file watcher thread; hop onto the server thread before broadcasting.
        // On a remote client (receiving Forge's login sync) there is no server, so nothing is re-sent.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                PartyService.invalidate();
                ForgeNetworkHandler.broadcastConfigSync();
            });
        }
    }

    private static void apply(Object spec) {
        if (spec == SERVER_SPEC) {
            SoulsVaultConfig.apply(readServer());
        } else if (spec == CLIENT_SPEC) {
            SoulsVaultClientConfig.apply(readClient(), new ClientWriter());
        }
    }

    private static SoulsVaultConfig.Settings readServer() {
        return new SoulsVaultConfig.Settings(
                new SoulsVaultConfig.Inventory(FULL_INVENTORY_REPLACEMENT.get(), ALLOW_REMOTE_VAULT_IN_FULL_INVENTORY.get()),
                new SoulsVaultConfig.Vault(OVERFLOW_ON_FULL_INVENTORY.get(), MAX_STACK_CAPACITY.get(), MAX_UNIQUE_ENTRIES.get(),
                        MAX_ITEM_NBT_BYTES.get(), REQUIRE_ATTUNEMENT.get()),
                new SoulsVaultConfig.RestPoints(REST_REACH.get(), REPLENISH_REQUIRES_REST_POINT.get(),
                        ECHO_SWAP_REQUIRES_REST_POINT.get(), REPLENISH_ON_WAKE_UP.get()),
                new SoulsVaultConfig.Echoes(ECHO_BASE_SLOTS.get(), Math.max(ECHO_BASE_SLOTS.get(), ECHO_MAX_SLOTS.get())),
                new SoulsVaultConfig.FieldAccess(VESTIGE_MOTH_XP_COST.get(), SPECTRAL_CASKET_SECONDS.get()),
                new SoulsVaultConfig.Combat(COMBAT_LOCK_SECONDS.get(), COMBAT_HOSTILE_RADIUS.get(), COMBAT_BOSS_RADIUS.get()),
                new SoulsVaultConfig.RemoteAccess(REMOTE_COST_TYPE.get(), REMOTE_COST_AMOUNT.get(), REMOTE_COST_ITEM.get()),
                new SoulsVaultConfig.Upgrades(CAPACITY_PER_EXPANSION.get(), UNIQUE_PER_EXPANSION.get(), MAX_EXPANSIONS.get()),
                new SoulsVaultConfig.EquipLoad(EQUIP_LOAD_ENABLED.get(), EQUIP_LOAD_BASE.get(), EQUIP_LOAD_SLOWDOWN.get(),
                        EQUIP_LOAD_PENALTY.get()),
                new SoulsVaultConfig.Party(PARTY_PROVIDER.get(), BUILTIN_PARTIES.get(), PARTY_MAX_MEMBERS.get(),
                        PARTY_SHARED_CASKET.get(), PARTY_FRIENDLY_FIRE.get(), PARTY_PROTECT_OWN_PETS.get(), PARTY_SHARE_ADVANCEMENTS.get(),
                        PARTY_SHARE_QUESTS.get(), PARTY_ALLOW_MERGE.get(), PARTY_ALLOW_OVERRIDE.get(),
                        PARTY_RETURN_ON_LEAVE.get(), PARTY_UPGRADE_CREDIT_MINUTES.get()),
                new SoulsVaultConfig.Keepsakes(KEEPSAKES_REQUIRE_UNLOCK.get(), KEEPSAKES_SLOTS.get(), KEEPSAKES_BASE_STACKS.get(),
                        KEEPSAKES_UPGRADED_STACKS.get())
        );
    }

    private static SoulsVaultClientConfig.Settings readClient() {
        return new SoulsVaultClientConfig.Settings(
                TOASTS_ENABLED.get(),
                TOAST_POSITION.get(),
                TOAST_DURATION.get(),
                TOAST_SOUND.get(),
                DEFAULT_SORT.get(),
                REMEMBER_LAST_TAB.get(),
                LAST_CATEGORY.get(),
                LAST_SORT.get(),
                LAST_LEFT_PANE.get(),
                PARTY_HUD_VISIBLE.get(),
                PARTY_HUD_STYLE.get(),
                PARTY_HUD_ANCHOR.get(),
                PARTY_HUD_SCALE.get(),
                PARTY_HUD_FULL_ROWS.get(),
                PARTY_PORTRAIT.get(),
                PARTY_HUNGER.get(),
                PARTY_MARKERS.get(),
                PARTY_MARKER_MIN_DISTANCE.get()
        );
    }

    /**
     * Saves values the client changes at runtime and re-applies them immediately.
     */
    private static final class ClientWriter implements SoulsVaultClientConfig.Writer {
        @Override
        public void rememberTab(VaultCategory category, VaultSortMode sortMode) {
            LAST_CATEGORY.set(category);
            LAST_SORT.set(sortMode);
            CLIENT_SPEC.save();
            SoulsVaultClientConfig.apply(readClient(), null);
        }

        @Override
        public void rememberLeftPane(LeftPaneMode mode) {
            LAST_LEFT_PANE.set(mode);
            CLIENT_SPEC.save();
            SoulsVaultClientConfig.apply(readClient(), null);
        }

        @Override
        public void setPartyVisuals(PortraitMode portrait, boolean markers) {
            PARTY_PORTRAIT.set(portrait);
            PARTY_MARKERS.set(markers);
            CLIENT_SPEC.save();
            SoulsVaultClientConfig.apply(readClient(), null);
        }

        @Override
        public void setPartyHud(boolean visible, PartyHudStyle style, HudAnchor anchor) {
            PARTY_HUD_VISIBLE.set(visible);
            PARTY_HUD_STYLE.set(style);
            PARTY_HUD_ANCHOR.set(anchor);
            CLIENT_SPEC.save();
            SoulsVaultClientConfig.apply(readClient(), null);
        }
    }
}
