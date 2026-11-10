package cn.punishmenteye;
import cn.fusionarsenal.EyeAccessoryEnergy;
import cn.fusionarsenal.config.ArsenalConfig;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** The active King skill grants strength as a native temporary attribute. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class KingHeartEnergy {
 public static final UUID ID=UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0006");
 private KingHeartEnergy(){}
 public static double bonus(LivingEntity entity){
  return entity instanceof ServerPlayer player&&player.getInventory()!=null&&SkillTimer.active(player)?ArsenalConfig.number("eye.king_energy_strength"):0;
 }
 public static AttributeModifier modifier(double value){return new AttributeModifier(ID,"King heart energy strength",value,AttributeModifier.Operation.ADDITION);}
 public static void sync(Player player){
  if(!(player instanceof ServerPlayer)||player.getInventory()==null||!EyeAccessoryEnergy.STRENGTH.isPresent()||player.getAttributes()==null)return;
  var attribute=player.getAttribute(EyeAccessoryEnergy.STRENGTH.get());if(attribute==null)return;
  double value=bonus(player);var old=attribute.getModifier(ID);
  if(old!=null&&(value<=0||old.getAmount()!=value||old.getOperation()!=AttributeModifier.Operation.ADDITION)){attribute.removeModifier(ID);old=null;}
  if(value>0&&old==null)attribute.addTransientModifier(modifier(value));
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END)sync(event.player);}
}
