package com.soulsvault.world.entity;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import com.soulsvault.world.block.BottomlessCasketBlock;
import com.soulsvault.world.inventory.CasketAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Temporary spectral vault entity summoned by a {@link com.soulsvault.world.item.VestigeMothItem} (lifetime from
 * {@code fieldAccess.spectralCasketLifetimeSeconds}). Any player may rest and open their own Casket at it, e.g.
 * before a boss arena; sessions opened here lose their Rest Point powers once it fades.
 */
public class SpectralCasketEntity extends Entity {
    private static final String NBT_REMAINING_TICKS = "RemainingTicks";
    private static final String NBT_OWNER_UUID = "OwnerUUID";

    private static final EntityDataAccessor<Integer> DATA_REMAINING_TICKS =
            SynchedEntityData.defineId(SpectralCasketEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUuid;

    public SpectralCasketEntity(EntityType<? extends SpectralCasketEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_REMAINING_TICKS, lifetimeTicks());
    }

    private static int lifetimeTicks() {
        return Math.max(1, SoulsVaultConfig.fieldAccess().spectralCasketLifetimeSeconds()) * 20;
    }

    public int getRemainingTicks() {
        return this.entityData.get(DATA_REMAINING_TICKS);
    }

    public void setRemainingTicks(int ticks) {
        this.entityData.set(DATA_REMAINING_TICKS, Math.max(0, ticks));
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    @Override
    public void tick() {
        super.tick();
        int remaining = getRemainingTicks();

        if (this.level().isClientSide) {
            if (this.random.nextInt(2) == 0) {
                double angle = this.random.nextDouble() * Math.PI * 2.0D;
                double radius = 0.35D + this.random.nextDouble() * 0.25D;
                double px = this.getX() + Math.cos(angle) * radius;
                double py = this.getY() + 0.25D + this.random.nextDouble() * 0.5D;
                double pz = this.getZ() + Math.sin(angle) * radius;
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0.0D, 0.02D, 0.0D);
            }
        } else {
            remaining--;
            setRemainingTicks(remaining);
            if (remaining % 20 == 0 || remaining == lifetimeTicks() - 1) {
                int secondsLeft = Math.max(1, (remaining + 19) / 20);
                this.setCustomName(Component.translatable("entity.soulsvault.spectral_casket")
                        .append(Component.literal(" (" + secondsLeft + "s)").withStyle(ChatFormatting.AQUA)));
                this.setCustomNameVisible(true);
            }
            if (remaining <= 0) {
                this.level().playSound(
                        null,
                        this.blockPosition(),
                        SoundEvents.ENDER_EYE_DEATH,
                        SoundSource.NEUTRAL,
                        0.8F,
                        0.75F
                );
                this.discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            RestPointProfile profile = RestPointProfiles.SPECTRAL_CASKET;
            BottomlessCasketBlock.openCasketForPlayer(serverPlayer, this.blockPosition(), profile,
                    CasketAnchor.atEntity(this, RestPointProfiles.reachOf(profile)));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(NBT_REMAINING_TICKS)) {
            setRemainingTicks(tag.getInt(NBT_REMAINING_TICKS));
        }
        if (tag.hasUUID(NBT_OWNER_UUID)) {
            this.ownerUuid = tag.getUUID(NBT_OWNER_UUID);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(NBT_REMAINING_TICKS, getRemainingTicks());
        if (this.ownerUuid != null) {
            tag.putUUID(NBT_OWNER_UUID, this.ownerUuid);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
