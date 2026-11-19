package cn.ssjjares;

import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Native indestructible armor; each tier has a normal pixel armor exterior. */
public final class AresArmorItem extends ArmorItem {
    public AresArmorItem(Type type){super(AresArmorItems.MATERIAL,type,new Item.Properties().stacksTo(1));}
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public int getMaxDamage(ItemStack stack){return 0;}
    @Override public int getDamage(ItemStack stack){return 0;}
    @Override public boolean isDamaged(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage){}
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack,int amount,T entity,Consumer<T> onBroken){return 0;}
    @Override public boolean isBarVisible(ItemStack stack){return false;}
    @Override public String getArmorTexture(ItemStack stack,Entity entity,EquipmentSlot slot,String type){
        boolean absolute=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_absolute");
        return "ssjj_ares:textures/models/armor/"+(absolute?"absolute":"extinction")+"_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png";
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){consumer.accept(cn.ssjjares.client.AresInvisibleArmor.INSTANCE);}
}
