package com.soulsvault.forge.registry;

import com.soulsvault.Constants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * {@code soulsvault:equip_load_bonus}: extra Equip Load capacity on players. Rings, skills, potions or KubeJS
 * scripts raise it with ordinary attribute modifiers.
 */
public final class ForgeModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Constants.MOD_ID);

    public static final RegistryObject<Attribute> EQUIP_LOAD_BONUS = ATTRIBUTES.register(
            "equip_load_bonus",
            () -> new RangedAttribute("attribute.name.soulsvault.equip_load_bonus", 0.0D, -1024.0D, 1024.0D).setSyncable(true)
    );

    private ForgeModAttributes() {
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(ForgeModAttributes::onAttributeModification);
    }

    private static void onAttributeModification(final EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, EQUIP_LOAD_BONUS.get());
    }
}
