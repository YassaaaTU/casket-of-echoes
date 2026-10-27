package com.soulsvault.core.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.RegistryLookups;
import com.soulsvault.core.rest.RestPointProfile.Grants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rest Point profiles from datapacks at {@code data/<namespace>/soulsvault/rest_points/<name>.json}:
 * <pre>{@code
 * {
 *   "blocks": ["minecraft:soul_campfire", "#soulsvault:bonfires"],
 *   "priority": 10,
 *   "requires_lit": true,
 *   "reach": 6.0,
 *   "vault_access": true,
 *   "replenish": true,
 *   "echo_swap": true,
 *   "set_spawn": true,
 *   "heal": true,
 *   "feed": false,
 *   "clear_harmful_effects": true
 * }
 * }</pre>
 * Any block in {@code #soulsvault:rest_points} without a matching profile still works as a plain Rest Point
 * (all three session powers, no resting effects), so the tag alone stays enough for simple packs.
 */
public final class RestPointProfiles {
    public static final String DIRECTORY = "soulsvault/rest_points";

    /** Fallback for {@code #soulsvault:rest_points} blocks no profile file covers. */
    public static final RestPointProfile TAGGED_DEFAULT = new RestPointProfile(
            new ResourceLocation(Constants.MOD_ID, "tagged_default"), Set.of(), List.of(Constants.REST_POINTS_TAG),
            Integer.MIN_VALUE, false, 0.0D, Grants.ALL, false, false, false, false);
    /** Spectral Caskets summoned with a Vestige Moth (an entity, so no block profile applies). */
    public static final RestPointProfile SPECTRAL_CASKET = new RestPointProfile(
            new ResourceLocation(Constants.MOD_ID, "spectral_casket"), Set.of(), List.of(),
            0, false, 0.0D, Grants.ALL, false, false, false, false);

    /** Largest per-profile reach honoured by {@link #findNearby}, bounding its block scan. */
    private static final double MAX_SCAN_REACH = 16.0D;
    /** Blocks scanned above and below the player's feet by {@link #findNearby}. */
    private static final int SCAN_HEIGHT = 2;

    /**
     * A usable Rest Point found around a player.
     */
    public record Nearby(BlockPos pos, RestPointProfile profile) {
    }

    private static volatile List<RestPointProfile> profiles = List.of();
    private static final Map<BlockState, Optional<RestPointProfile>> CACHE = new ConcurrentHashMap<>();

    private RestPointProfiles() {
    }

    /**
     * Forgets cached block state lookups; call when block tags change.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * The profile for a block state, or {@code null} if it isn't a Rest Point (e.g. an unlit bonfire).
     */
    @Nullable
    public static RestPointProfile find(BlockState state) {
        return CACHE.computeIfAbsent(state, RestPointProfiles::compute).orElse(null);
    }

    private static Optional<RestPointProfile> compute(BlockState state) {
        for (RestPointProfile profile : profiles) {
            if (profile.matches(state)) {
                return Optional.of(profile);
            }
        }
        return state.is(Constants.REST_POINTS_TAG) ? Optional.of(TAGGED_DEFAULT) : Optional.empty();
    }

    /**
     * How close a player must stand to use a Rest Point with this profile.
     */
    public static double reachOf(RestPointProfile profile) {
        return profile.reach() > 0.0D ? profile.reach() : SoulsVaultConfig.restPoints().reachBlocks();
    }

    /**
     * Whether the player stands close enough to use the Rest Point at {@code pos}.
     */
    public static boolean inReach(ServerPlayer player, BlockPos pos, RestPointProfile profile) {
        double reach = reachOf(profile);
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
    }

    /**
     * The nearest usable Rest Point around the player, or {@code null}.
     */
    @Nullable
    public static Nearby findNearby(ServerPlayer player) {
        Level level = player.level();
        int radius = (int) Math.ceil(Math.max(SoulsVaultConfig.restPoints().reachBlocks(), maxProfileReach()));
        BlockPos center = player.blockPosition();
        Nearby best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -SCAN_HEIGHT, -radius),
                center.offset(radius, SCAN_HEIGHT, radius))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            RestPointProfile profile = find(level.getBlockState(pos));
            if (profile == null || !inReach(player, pos, profile)) {
                continue;
            }
            double distance = player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                best = new Nearby(pos.immutable(), profile);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double maxProfileReach() {
        double max = 0.0D;
        for (RestPointProfile profile : profiles) {
            max = Math.max(max, profile.reach());
        }
        return Math.min(MAX_SCAN_REACH, max);
    }

    /**
     * Applies a profile's resting effects (heal, feed, cleanse, respawn point). Callers check the combat lock first.
     */
    public static void applyRestEffects(ServerPlayer player, RestPointProfile profile) {
        if (profile.heal()) {
            player.setHealth(player.getMaxHealth());
        }
        if (profile.feed()) {
            player.getFoodData().eat(20, 1.0F);
        }
        if (profile.clearHarmfulEffects()) {
            List<MobEffectInstance> harmful = new ArrayList<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    harmful.add(effect);
                }
            }
            for (MobEffectInstance effect : harmful) {
                player.removeEffect(effect.getEffect());
            }
        }
        if (profile.setSpawn()) {
            BlockPos standing = player.blockPosition();
            boolean changed = !standing.equals(player.getRespawnPosition()) || player.getRespawnDimension() != player.level().dimension();
            player.setRespawnPosition(player.level().dimension(), standing, player.getYRot(), true, false);
            if (changed) {
                player.displayClientMessage(Component.translatable("gui.soulsvault.rest.spawn_set").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /**
     * Datapack reload listener; register it with the loader's reload-listener hook.
     */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(Constants.GSON, DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<RestPointProfile> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                try {
                    loaded.add(parse(file.getKey(), GsonHelper.convertToJsonObject(file.getValue(), "rest point profile")));
                } catch (RuntimeException e) {
                    Constants.LOG.error("Invalid Casket of Echoes rest point profile {}: {}", file.getKey(), e.getMessage());
                }
            }
            loaded.sort(Comparator.comparingInt(RestPointProfile::priority).reversed()
                    .thenComparing(profile -> profile.id().toString()));
            profiles = List.copyOf(loaded);
            CACHE.clear();
            Constants.LOG.info("Loaded {} Casket of Echoes rest point profile(s)", loaded.size());
        }

        private static RestPointProfile parse(ResourceLocation id, JsonObject json) {
            Set<Block> blocks = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "blocks")) {
                String value = GsonHelper.convertToString(element, "block");
                if (value.startsWith("#")) {
                    tags.add(TagKey.create(Registries.BLOCK, new ResourceLocation(value.substring(1))));
                } else {
                    RegistryLookups.block(new ResourceLocation(value)).ifPresent(blocks::add);
                }
            }
            Grants grants = new Grants(
                    GsonHelper.getAsBoolean(json, "vault_access", true),
                    GsonHelper.getAsBoolean(json, "replenish", true),
                    GsonHelper.getAsBoolean(json, "echo_swap", true)
            );
            return new RestPointProfile(
                    id,
                    Set.copyOf(blocks),
                    List.copyOf(tags),
                    GsonHelper.getAsInt(json, "priority", 0),
                    GsonHelper.getAsBoolean(json, "requires_lit", false),
                    GsonHelper.getAsDouble(json, "reach", 0.0D),
                    grants,
                    GsonHelper.getAsBoolean(json, "set_spawn", false),
                    GsonHelper.getAsBoolean(json, "heal", false),
                    GsonHelper.getAsBoolean(json, "feed", false),
                    GsonHelper.getAsBoolean(json, "clear_harmful_effects", false)
            );
        }
    }
}
