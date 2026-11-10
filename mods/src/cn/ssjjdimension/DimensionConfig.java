package cn.ssjjdimension;
import cn.fusionarsenal.config.ArsenalConfig;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
/** Added to the existing global spec before it is built; no second config file. */
public final class DimensionConfig {
 public record Entry(String key,Number value,double min,double max,String comment){}
 public static final List<Entry> ENTRIES=List.of(
  new Entry("dimension.pellet_damage",3.0d,0,1000000,"普通弹丸基础伤害（每丸，保留距离与其他加成比例）"),
  new Entry("dimension.melee_damage",15.0d,0,1000000,"枪械近战基础伤害"),
  new Entry("dimension.rpm",200,1,1200,"普通模式射速（每分钟发数；技能期间使用施放间隔）"),
  new Entry("dimension.spread_multiplier",1.0d,0.01,4,"普通模式基础散布倍率，保留各姿态差异"),
  new Entry("dimension.active_ticks",300,1,72000,"满能量高能持续时间，20tick=1秒"),
  new Entry("dimension.recharge_ticks",100,1,72000,"高能结束后的能量回满时间"),
  new Entry("dimension.cast_interval_ticks",6,1,1200,"高能连续施放间隔"),
  new Entry("dimension.field_damage",30.0d,0,1000000,"领域部署和结束引爆基础伤害"),
  new Entry("dimension.field_bonus_damage",30.0d,0,1000000,"领域内每次标记爆炸的额外伤害"),
  new Entry("dimension.field_radius_tier1",3.0d,0.25,16,"一阶领域基础半径（格）"),
  new Entry("dimension.field_radius_tier2",5.0d,0.25,16,"二阶领域基础半径（格）"),
  new Entry("dimension.field_ticks",60,1,72000,"领域基础存在时间"),
  new Entry("dimension.field_stun_ticks",10,1,1200,"领域基础眩晕时间"),
  new Entry("dimension.cast_range",64.0d,1,256,"领域施放最大距离（格）"),
  new Entry("dimension.max_fields",3,1,16,"每玩家普通领域数量上限，分身额外领域不计入"),
  new Entry("dimension.mark_cap",5,1,5,"每个目标最多标记层数，原素材最多支持5层"),
  new Entry("dimension.mark_interval_ticks",10,1,1200,"标记引爆间隔"),
  new Entry("dimension.mark_healing_fraction",0.01d,0,1,"每次有效标记爆炸回复最大生命的比例"),
  new Entry("dimension.mark_seconds_base",1,1,120,"基础版标记持续秒数"),
  new Entry("dimension.mark_seconds_tier1",2,1,120,"一阶标记持续秒数"),
  new Entry("dimension.mark_seconds_tier2",3,1,120,"二阶标记持续秒数"),
  new Entry("dimension.dash_cooldown_ticks",80,1,72000,"瞬闪冷却时间"),
  new Entry("dimension.dash_ticks",5,1,120,"瞬闪水平速度脉冲持续时间"),
  new Entry("dimension.dash_speed",1.2d,0.01,3.9,"瞬闪水平速度，原生速度包范围内"),
  new Entry("dimension.protection_ticks",20,1,1200,"瞬闪减伤与真实隐身持续时间"),
  new Entry("dimension.damage_reduction",0.99d,0,1,"瞬闪期间减伤比例，0.99=减免99%"),
  new Entry("dimension.decoy_ticks",60,1,72000,"站立分身保留时间"),
  new Entry("dimension.overload_ramp_ticks",100,1,72000,"超负荷降至最低伤害所需时间"),
  new Entry("dimension.overload_recovery_ticks",60,1,72000,"换主手后超负荷伤害恢复时间"),
  new Entry("dimension.overload_min_damage",0.001d,1e-06,1,"超负荷最低伤害比例，0.001=原伤害0.1%"),
  new Entry("dimension.muzzle_pellet_damage",8.0d,0,1000000,"专属枪口安装后的每丸基础伤害"),
  new Entry("dimension.muzzle_spread_multiplier",0.5d,0.01,4,"专属枪口在当前扩散基础上的倍率"),
  new Entry("dimension.muzzle_mark_duration_multiplier",2,1,10,"专属枪口标记持续时间倍率"),
  new Entry("dimension.grip_radius_multiplier",2.0d,1,4,"专属握把领域半径倍率"),
  new Entry("dimension.grip_extra_ticks",20,0,72000,"专属握把增加领域存在时间"),
  new Entry("dimension.ultimate_extra_stun_ticks",10,0,1200,"终极/融合/创造石增加眩晕时间"),
  new Entry("dimension.ability_damage_multiplier",1.3d,1,100,"异能/融合/创造石对超负荷敌人伤害倍率"));
 private DimensionConfig(){}
 public static void define(ForgeConfigSpec.Builder builder,Map<String,ForgeConfigSpec.ConfigValue<?>> values,Map<String,Object> defaults){
  for(var e:ENTRIES){ForgeConfigSpec.ConfigValue<?> option=e.value() instanceof Integer?builder.comment(e.comment(),"修改后重启游戏或服务器；服务端配置会同步到客户端。").defineInRange(e.key(),e.value().intValue(),(int)e.min(),(int)e.max()):builder.comment(e.comment(),"修改后重启游戏或服务器；服务端配置会同步到客户端。").defineInRange(e.key(),e.value().doubleValue(),e.min(),e.max());values.put(e.key(),option);defaults.put(e.key(),e.value());}
  cn.ssjjcompat.AttachmentBonusConfig.define(builder,values,defaults);
 }
 public static double number(String suffix){String key="dimension."+suffix;try{return ArsenalConfig.number(key);}catch(IllegalArgumentException legacy){return ENTRIES.stream().filter(e->e.key().equals(key)).findFirst().orElseThrow().value().doubleValue();}}
 public static int integer(String suffix){return (int)number(suffix);}
 public static int markSeconds(int tier){return integer(switch(tier){case 0->"mark_seconds_base";case 1->"mark_seconds_tier1";default->"mark_seconds_tier2";});}
}
