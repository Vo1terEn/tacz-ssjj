package cn.fusionarsenal;
import cn.fusionarsenal.config.ProgressionConfig;

import cn.judgfire.EnergyAttributes;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/** Installed dedicated accessories add native stats, without inventing strength damage. */
public final class EyeAccessoryEnergy {
 public static final RegistryObject<Attribute> STRENGTH=EnergyAttributes.ATTRIBUTES.register("energy_strength",()->new RangedAttribute("attribute.judgfire.energy_strength",0,0,1.0e12).setSyncable(true));
 private static final UUID ID=UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0005");
 private static final ResourceLocation POWER=new ResourceLocation("eyeofpunishment:punishment_power"),HEART=new ResourceLocation("eyeofpunishment:king_heart");
 private EyeAccessoryEnergy(){}
 /** Called before the original DeferredRegister is attached to the mod bus. */
 public static void register(IEventBus bus){bus.addListener(EyeAccessoryEnergy::addAttributes);}
 private static void addAttributes(EntityAttributeModificationEvent event){for(var type:event.getTypes())event.add(type,STRENGTH.get());}
 public static int installedCount(ItemStack stack){
  var gun=IGun.getIGunOrNull(stack);if(gun==null)return 0;var id=gun.getGunId(stack);
  if(id==null||!id.getNamespace().equals("eyeofpunishment")||!(id.getPath().equals("notdistinguish")||id.getPath().equals("notdistinguish_tier1")))return 0;
  int count=0;if(POWER.equals(gun.getAttachmentId(stack,AttachmentType.GRIP)))count++;
  if(id.getPath().equals("notdistinguish")&&HEART.equals(gun.getAttachmentId(stack,AttachmentType.STOCK)))count++;
  return count;
 }
 /** Appended to the existing live resistance/penetration stack lookup. */
 public static int fireInstalledCount(ItemStack stack){
  var gun=IGun.getIGunOrNull(stack);if(gun==null)return 0;var id=gun.getGunId(stack);
  if(id==null||!id.getNamespace().equals("judgfire")||!(id.getPath().equals("judgment_flame")||id.getPath().equals("judgment_flame_tier1")))return 0;
  int count=0;
  if(new ResourceLocation("judgfire:red_lotus_grip").equals(gun.getAttachmentId(stack,AttachmentType.GRIP)))count++;
  if(new ResourceLocation("judgfire:emperor_crown_stock").equals(gun.getAttachmentId(stack,AttachmentType.STOCK)))count++;
  return count;
 }
 public static double resistanceOrPenetration(ItemStack stack){return resistanceOrPenetration(stack,false);}
 public static double resistanceOrPenetration(ItemStack stack,boolean penetration){return ProgressionConfig.number(penetration?"eye.accessory_penetration":"eye.accessory_resistance")*installedCount(stack)+ProgressionConfig.number(penetration?"fire.accessory_penetration":"fire.accessory_resistance")*fireInstalledCount(stack);}
 public static double strength(ItemStack stack){return ProgressionConfig.number("eye.accessory_strength")*installedCount(stack)+ProgressionConfig.number("fire.accessory_strength")*fireInstalledCount(stack);}
 public static void itemModifiers(ItemAttributeModifierEvent event){
  if(event.getSlotType()!=EquipmentSlot.MAINHAND)return;double amount=strength(event.getItemStack());
  if(amount>0)event.addModifier(STRENGTH.get(),new AttributeModifier(ID,"Dedicated weapon accessory energy strength",amount,AttributeModifier.Operation.ADDITION));
 }
}
