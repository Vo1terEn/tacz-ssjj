package cn.judgfire;
import cn.fusionarsenal.EyeAccessoryEnergy;
import cn.punishmenteye.KingHeartEnergy;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
/** Recompute known dynamic contributions without duplicating synchronized native modifiers. */
public final class StrengthAttributes {
 public static final UUID ITEM_ID=UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0005");
 private StrengthAttributes(){}
 public static double value(LivingEntity entity){
  if(entity==null||entity instanceof Player p&&p.getInventory()==null||!EyeAccessoryEnergy.STRENGTH.isPresent()||entity.getAttributes()==null)return 0;
  var actual=entity.getAttribute(EyeAccessoryEnergy.STRENGTH.get());if(actual==null)return 0;
  var result=new AttributeInstance(EyeAccessoryEnergy.STRENGTH.get(),ignored->{});result.setBaseValue(actual.getBaseValue());
  boolean authoritative=entity instanceof ServerPlayer;
  for(var modifier:actual.getModifiers())if(!ITEM_ID.equals(modifier.getId())&&(!authoritative||!KingHeartEnergy.ID.equals(modifier.getId())))result.addTransientModifier(modifier);
  var hand=entity.getMainHandItem();double item=hand==null?0:EyeAccessoryEnergy.strength(hand);
  if(item>0)result.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ITEM_ID,"Current held weapon energy strength",item,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
  if(authoritative){double king=KingHeartEnergy.bonus(entity);if(king>0)result.addTransientModifier(KingHeartEnergy.modifier(king));}
  return result.getValue();
 }
}
