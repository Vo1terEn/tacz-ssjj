package cn.ssjjares;

import cn.ssjjares.AresAccessoryConfig.Stat;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** A native armor replacement that can also be used as an optional decoration. */
public final class AresAccessoryItem extends ArmorItem {
    private final int kind,tier;
    public AresAccessoryItem(Type type,int kind,boolean absolute){super(AresArmorItems.MATERIAL,type,new Item.Properties().stacksTo(1));this.kind=kind;this.tier=absolute?2:1;}
    public int kind(){return kind;}
    public int tier(){return tier;}
    @Override public int getDefense() { return Stat.ARMOR.integer(kind, tier); }
    @Override public float getToughness() { return (float) Stat.TOUGHNESS.number(kind, tier); }
    @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        var original = super.getDefaultAttributeModifiers(slot);
        if (slot != getEquipmentSlot()) return original;
        var updated = ImmutableMultimap.<Attribute, AttributeModifier>builder();
        original.forEach((attribute, modifier) -> {
            double amount;
            if (attribute == Attributes.ARMOR) amount = getDefense();
            else if (attribute == Attributes.ARMOR_TOUGHNESS) amount = getToughness();
            else amount = modifier.getAmount();
            if (amount != 0) updated.put(attribute, amount == modifier.getAmount() ? modifier
                : new AttributeModifier(modifier.getId(), modifier.getName(), amount, modifier.getOperation()));
        });
        return updated.build();
    }
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public int getMaxDamage(ItemStack stack){return 0;}
    @Override public int getDamage(ItemStack stack){return 0;}
    @Override public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return kind == AresAccessoryConfig.WINGS && Stat.FLIGHT.enabled(kind, tier);
    }
    @Override public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int ticks) {
        if (!canElytraFly(stack, entity)) return false;
        if (!entity.level().isClientSide && (ticks + 1) % 10 == 0) entity.gameEvent(GameEvent.ELYTRA_GLIDE);
        return true;
    }
    @Override public boolean isDamaged(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage){}
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack,int amount,T entity,Consumer<T> onBroken){return 0;}
    @Override public boolean isBarVisible(ItemStack stack){return false;}
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type){return "ssjj_ares:textures/models/armor/"+(tier==2?"absolute":"extinction")+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png";}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(cn.ssjjares.client.AresAccessoryArmor.INSTANCE);}
}
