package cn.ssjjares;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Selects a real equipped flight provider at the three vanilla flight checks. */
public final class AresWingFlight {
    private static final ResourceLocation CAELUS_FLIGHT = new ResourceLocation("caelus", "fall_flying");

    private AresWingFlight() {}

    public static ItemStack flightStack(ItemStack chest, LivingEntity entity) {
        if (!(entity instanceof Player) || chest.canElytraFly(entity)) return chest;
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        if (AresAccessories.kind(legs) == 3 && legs.canElytraFly(entity)) return legs;
        ItemStack wings = AresAccessories.functionalCurio(entity, 3, stack -> stack.canElytraFly(entity));
        return wings.isEmpty() ? chest : wings;
    }

    static Attribute caelusAttribute() {
        return ForgeRegistries.ATTRIBUTES.getValue(CAELUS_FLIGHT);
    }

    public static boolean usesCaelus() {
        return caelusAttribute() != null;
    }

    /** With Caelus installed, equipment modifiers drive both sides of its normal flight path. */
    public static ItemStack vanillaFlightStack(ItemStack chest, LivingEntity entity) {
        return usesCaelus() ? chest : flightStack(chest, entity);
    }
}
