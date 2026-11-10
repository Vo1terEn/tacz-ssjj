package cn.ssjjcompat;
import java.util.*;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
/** Source identity snapshots keep projectile and delayed skill attribution after switching guns. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class CombatMessages {
 private static final Map<DamageSource,String> STYLES=Collections.synchronizedMap(new WeakHashMap<>());
 public static DamageSource remember(DamageSource source,String style){STYLES.put(source,style);return source;}
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void bullet(EntityHurtByGunEvent.Pre e){
  if(e.isCanceled()||e.getLogicalSide()!=net.minecraftforge.fml.LogicalSide.SERVER)return;
  String f=AttachmentBonusRuntime.family(e.getGunId());if(!f.equals("parasite")&&!f.equals("dimension"))return;
  for(var part:GunDamageSourcePart.values()){var s=e.getDamageSource(part);if(s!=null)remember(s,f+"_shot");}
 }
 public static Component message(DamageSource source,LivingEntity victim){
  String style=source instanceof cn.ssjjparasite2.client.ParasiteDestructionDamage?"parasite_destruction":STYLES.get(source);
  var attacker=source.getEntity();if(style==null||attacker==null)return null;
  return format(style,victim.getDisplayName(),attacker.getDisplayName());
 }
 public static Component format(String style,Component victim,Component attacker){
  boolean parasite=style.startsWith("parasite");int color=parasite?0x92DF57:0x67DDFF;
  return Component.translatable("death.ssjj."+style+".prefix").withStyle(s->s.withColor(color).withBold(true))
   .append(Component.translatable("death.ssjj."+style,victim.copy().withStyle(s->s.withColor(0xE5DCF3)),attacker.copy().withStyle(s->s.withColor(color)))
   .withStyle(s->s.withColor(parasite?0xB5CCA6:0xB9B3EC).withBold(false)));
 }
}
