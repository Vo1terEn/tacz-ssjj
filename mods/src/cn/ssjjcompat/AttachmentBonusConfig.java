package cn.ssjjcompat;
import cn.fusionarsenal.config.ArsenalConfig;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
/** Additional ordinary-part bonuses, registered in the existing single global spec. */
public final class AttachmentBonusConfig {
 public record Entry(String key,double value,String comment){}
 public static final List<Entry> ENTRIES=List.of(
  new Entry("judgment_energy_resistance",250,"裁决之炎每普通配件额外能量抗性"),
  new Entry("parasite_bullet_damage",10,"寄生者每普通配件额外普通弹基础伤害"),
  new Entry("parasite_wave_damage",70,"寄生者每普通配件额外巨炮冲击波基础伤害"),
  new Entry("eye_damage",0,"天罚之眼每普通配件额外普通弹、落雷与狂暴星云基础伤害"),
  new Entry("dimension_energy_resistance",300,"次元之星每普通配件额外能量抗性"),
  new Entry("dimension_energy_penetration",75,"次元之星每普通配件额外能量抗性穿透"),
  new Entry("dimension_energy_strength",300,"次元之星每普通配件额外能量强度"));
 private AttachmentBonusConfig(){}
 public static void define(ForgeConfigSpec.Builder builder,Map<String,ForgeConfigSpec.ConfigValue<?>> values,Map<String,Object> defaults){
  for(var e:ENTRIES){String key="attachment_bonus."+e.key();values.put(key,builder.comment(e.comment(),"不计瞄准镜和激光槽；修改后重启游戏或服务器。").defineInRange(key,e.value(),0,1000000));defaults.put(key,e.value());}
 }
 public static double value(String key){return ArsenalConfig.number("attachment_bonus."+key);}
}
