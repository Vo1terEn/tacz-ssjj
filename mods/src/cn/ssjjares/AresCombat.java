package cn.ssjjares;

import cn.judgfire.EnergyAttributes;
import cn.ssjjares.AresAccessoryConfig.Stat;
import com.google.common.collect.ImmutableMultimap;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class AresCombat {
    private static final UUID[] ARMOR_MODIFIERS = {
        UUID.fromString("ba7cfb3b-09dd-4615-b4ca-ceaf3dda1889"),
        UUID.fromString("34470d7f-0bc6-423f-9e13-57db2a529641"),
        UUID.fromString("bed4ac26-b9a3-4a41-97cd-6feb0c885c43")
    };
    private AresCombat() {}

    static final class Accessories {
        int damagePercent;
        double incomingMultiplier = 1;
        int shoulderTier;
        int maskTier;

        void add(ItemStack stack) {
            int kind = AresAccessories.kind(stack);
            if (kind == 0) return;
            int tier = AresAccessories.tier(stack);
            damagePercent += Stat.DAMAGE.integer(kind, tier);
            if (kind == 1) maskTier = Math.max(maskTier, tier);
            if (kind == 2) shoulderTier = Math.max(shoulderTier, tier);
            if (kind == 3) incomingMultiplier *= 1 - Stat.REDUCTION.number(kind, tier) / 100;
        }
    }

    static Accessories equipped(LivingEntity entity) {
        var effects = new Accessories();
        for (int index = 0; index < ARMOR_MODIFIERS.length; index++) {
            var stack = entity.getItemBySlot(AresArmorItems.SLOTS.get(index));
            if (AresAccessories.kind(stack) == index + 1) effects.add(stack);
        }
        AresAccessories.forEachFunctionalCurio(entity, effects::add);
        return effects;
    }

    public static int accessoryDamagePercent(int tier) {
        return tier == 1 || tier == 2 ? Stat.DAMAGE.integer(AresAccessoryConfig.MASK, tier) : tier * 20;
    }

    public static int setDamagePercent(int tier) {
        return tier * 50;
    }

    static ImmutableMultimap<Attribute, AttributeModifier> accessoryModifiers(ItemStack stack, UUID id) {
        int tier = AresAccessories.tier(stack);
        var result = ImmutableMultimap.<Attribute, AttributeModifier>builder();
        int kind = AresAccessories.kind(stack);
        switch (kind) {
            case 1 -> {
                if (EnergyAttributes.PENETRATION.isPresent())
                    add(result, EnergyAttributes.PENETRATION.get(), id, Stat.PENETRATION.number(kind, tier), Operation.ADDITION);
            }
            case 2 -> {
                if (EnergyAttributes.RESISTANCE.isPresent())
                    add(result, EnergyAttributes.RESISTANCE.get(), id, Stat.RESISTANCE.number(kind, tier), Operation.ADDITION);
            }
            case 3 -> {
                add(result, Attributes.MOVEMENT_SPEED, id, Stat.SPEED.number(kind, tier) / 100, Operation.MULTIPLY_TOTAL);
                if (Stat.FLIGHT.enabled(kind, tier)) {
                    Attribute flight = AresWingFlight.caelusAttribute();
                    if (flight != null) add(result, flight, id, 1, Operation.ADDITION);
                }
                if (EnergyAttributes.RESISTANCE.isPresent())
                    add(result, EnergyAttributes.RESISTANCE.get(), id, Stat.RESISTANCE.number(kind, tier), Operation.ADDITION);
                if (EnergyAttributes.PENETRATION.isPresent())
                    add(result, EnergyAttributes.PENETRATION.get(), id, Stat.PENETRATION.number(kind, tier), Operation.ADDITION);
            }
        }
        return result.build();
    }

    private static void add(ImmutableMultimap.Builder<Attribute, AttributeModifier> result, Attribute attribute,
            UUID id, double amount, Operation operation) {
        if (amount != 0) result.put(attribute, new AttributeModifier(id, "Ares accessory", amount, operation));
    }

    @SubscribeEvent
    public static void armorAttributes(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        int kind = AresAccessories.kind(stack);
        if (kind > 0 && event.getSlotType() == AresArmorItems.SLOTS.get(kind - 1))
            accessoryModifiers(stack, ARMOR_MODIFIERS[kind - 1]).forEach(event::addModifier);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent event) {
        var target = event.getEntity();
        float amount = event.getAmount();
        if (!(target.level() instanceof ServerLevel) || !Float.isFinite(amount) || amount <= 0) return;
        double reduction = target instanceof ServerPlayer ? equipped(target).incomingMultiplier : 1;
        event.setAmount(scaledAmount(amount, outgoingBonus(target, event.getSource()), reduction));
    }

    /** Creative Stone settlements apply the attacker's bonuses and retain their defense bypass. */
    public static float scaleDirect(LivingEntity target, DamageSource source, float amount) {
        if (source instanceof AresMissileDamage || !Float.isFinite(amount) || amount <= 0) return amount;
        return scaledAmount(amount, outgoingBonus(target, source), 1.0);
    }

    private static int outgoingBonus(LivingEntity target, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target && attacker.level() == target.level()) {
            int bonus = setDamagePercent(AresArmorItems.setTier(attacker)) + equipped(attacker).damagePercent;
            if (AresRage.amplifies(attacker, target)) bonus += AresRage.DAMAGE_PERCENT;
            return bonus;
        }
        return 0;
    }

    static float scaledAmount(float amount, int bonus, int wings) {
        return scaledAmount(amount, bonus, Math.pow(1 - Stat.REDUCTION.number(AresAccessoryConfig.WINGS, 1) / 100, wings));
    }

    private static float scaledAmount(float amount, int bonus, double incomingMultiplier) {
        return (float) Math.min(Float.MAX_VALUE, amount * (1 + bonus / 100.0) * incomingMultiplier);
    }
}
