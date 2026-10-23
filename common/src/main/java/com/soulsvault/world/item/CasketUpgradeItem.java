package com.soulsvault.world.item;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultStorage;
import com.soulsvault.core.VaultSync;
import com.soulsvault.party.CovenantService;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Talisman-Pouch style progression consumables, tuned in the {@code upgrades} / {@code echoes} server config:
 * <ul>
 *   <li>{@link Kind#RELIQUARY_SHARD}: expands the Casket the player currently uses (their party's shared one when
 *       it is shared): more items per kind and, when a distinct-item limit is set, more kinds.</li>
 *   <li>{@link Kind#ECHO_POUCH}: one more Build Echo slot for the player, up to {@code echoes.maxSlots}.</li>
 * </ul>
 */
public class CasketUpgradeItem extends Item {

    public enum Kind {
        RELIQUARY_SHARD,
        ECHO_POUCH
    }

    private final Kind kind;

    public CasketUpgradeItem(Kind kind) {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(serverPlayer);
        Component result = this.kind == Kind.RELIQUARY_SHARD ? expand(serverPlayer, vault) : addEchoSlot(serverPlayer, vault);
        if (result == null) {
            return InteractionResultHolder.fail(stack);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.displayClientMessage(result.copy().withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 0.7F);
        return InteractionResultHolder.consume(stack);
    }

    @Nullable
    private static Component expand(ServerPlayer player, PlayerVaultData vault) {
        VaultStorage storage = vault.getStorage();
        if (storage.getExpansions() >= SoulsVaultConfig.upgrades().maxExpansions()) {
            player.displayClientMessage(Component.translatable("item.soulsvault.reliquary_shard.maxed")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        storage.addExpansion();
        CovenantService.onExpansion(storage);
        VaultSync.resyncViewers(player.server, storage.getId());
        VaultSync.resync(player);
        return Component.translatable("item.soulsvault.reliquary_shard.used", storage.getMaxStackCapacity());
    }

    @Nullable
    private static Component addEchoSlot(ServerPlayer player, PlayerVaultData vault) {
        if (!vault.addEchoSlotUpgrade()) {
            player.displayClientMessage(Component.translatable("item.soulsvault.echo_pouch.maxed")
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        VaultSync.resync(player);
        return Component.translatable("item.soulsvault.echo_pouch.used", vault.getEchoSlots());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (this.kind == Kind.RELIQUARY_SHARD) {
            SoulsVaultConfig.Upgrades upgrades = SoulsVaultConfig.upgrades();
            tooltip.add(Component.translatable("item.soulsvault.reliquary_shard.desc", upgrades.capacityPerExpansion())
                    .withStyle(ChatFormatting.AQUA));
            if (SoulsVaultConfig.vault().maxUniqueEntries() > 0) {
                tooltip.add(Component.translatable("item.soulsvault.reliquary_shard.desc_unique", upgrades.uniqueEntriesPerExpansion())
                        .withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable("item.soulsvault.upgrade.limit", upgrades.maxExpansions())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.soulsvault.echo_pouch.desc").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.soulsvault.upgrade.limit", SoulsVaultConfig.echoes().maxSlots())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
