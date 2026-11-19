package cn.ssjjhyper;

import cn.fusionarsenal.config.ArsenalConfig;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;

/** Hyper gameplay settings live in the existing synchronized global ssjjrpg.toml spec. */
public final class HyperConfig {
    public record Entry(String key,Object value,double min,double max,String label,String tooltip){}
    public static final List<Entry> ENTRIES=List.of(
        new Entry("hyper.ray_enabled",true,0,1,"聚能射线开关","壹、贰阶普通射击使用原有往返射线；关闭后恢复枪包原生弹丸。"),
        new Entry("hyper.charge_enabled",true,0,1,"V聚能弹开关","允许壹、贰阶使用V聚能弹；关闭后技能无法激活。"),
        new Entry("hyper.core_enabled",true,0,1,"G聚能核心开关","允许壹、贰阶使用G蓄能、红蓝球及切回引爆。"),
        new Entry("hyper.overload_enabled",true,0,1,"聚能过载开关","二阶持枪计时及双向切枪增伤。"),
        new Entry("hyper.muzzle_enabled",true,0,1,"聚能导流枪口功能开关","关闭后该专属配件的技能增强与能量属性不生效；安装权限和外观不改变。"),
        new Entry("hyper.grip_enabled",true,0,1,"聚能增幅握把功能开关","关闭后该专属配件的技能增强与能量属性不生效；安装权限和外观不改变。"),
        new Entry("hyper.stock_enabled",true,0,1,"聚能膛线枪托功能开关","关闭后该专属配件的技能增强与能量属性不生效；安装权限和外观不改变。"),
        new Entry("hyper.magazine_enabled",true,0,1,"聚能过载弹匣功能开关","关闭后该专属配件的技能增强与能量属性不生效；安装权限和外观不改变。"),
        new Entry("hyper.accessory_penetration",250.0d,0,1000000,"每件专属配件能量穿透","每件有效枪口、握把、枪托、弹匣的能量抗性穿透；不含瞄准镜与激光槽。"),
        new Entry("hyper.accessory_resistance",200.0d,0,1000000,"每件专属配件能量抗性","每件有效枪口、握把、枪托、弹匣的能量抗性。"),
        new Entry("hyper.accessory_strength",400.0d,0,1000000,"每件专属配件能量强度","每件有效枪口、握把、枪托、弹匣的能量强度。"),
        new Entry("hyper.charge_base_damage",40.0d,0,1000000,"聚能弹首次伤害","每个目标首次成功聚能弹引爆的基础伤害，仍应用原有战斗属性。"),
        new Entry("hyper.charge_increment",0.4d,0,100,"聚能弹每次额外增伤","每次成功引爆累积的额外增伤比例，0.4代表首次伤害的40%。"),
        new Entry("hyper.charge_increment_cap",2.0d,0,100,"聚能弹额外增伤上限","相对首次伤害的额外增伤上限，2代表额外200%。"),
        new Entry("hyper.ultimate_increment",0.8d,0,100,"终极强化每次额外增伤","终极、融合、创造石的聚能弹额外增伤步长，0.8代表80%。"),
        new Entry("hyper.ultimate_increment_cap",4.0d,0,100,"终极强化额外增伤上限","终极、融合、创造石聚能弹的额外增伤上限，4代表额外400%。"),
        new Entry("hyper.charge_duration_ticks",200,20,320,"聚能弹持续时间","单位tick，20tick=1秒；允许20至320tick。"),
        new Entry("hyper.grip_charge_duration_ticks",320,20,320,"握把聚能弹持续时间","安装有效聚能增幅握把时的技能持续时间，单位tick。"),
        new Entry("hyper.charge_second_pulse_enabled",true,0,1,"聚能弹二次火环开关","允许同一聚能弹技能生成第二次扩散火环。"),
        new Entry("hyper.charge_second_pulse_ticks",100,15,319,"聚能弹二次火环时间","激活后经过多少tick生成第二次火环；自动限制在技能结束前。"),
        new Entry("hyper.grip_second_pulse_ticks",160,15,319,"握把二次火环时间","安装有效握把后的第二次火环时间，自动限制在技能结束前。"),
        new Entry("hyper.charge_tier1_mark_cap",10,1,40,"壹阶聚能弹标记上限","壹阶技能同时保留的标记数量，最终上限40。"),
        new Entry("hyper.charge_tier2_mark_cap",20,1,40,"贰阶聚能弹标记上限","贰阶技能同时保留的标记数量，最终上限40。"),
        new Entry("hyper.grip_mark_multiplier",2.0d,1,40,"握把标记数量倍率","有效握把对聚能弹标记上限的倍率，最终不超过40。"),
        new Entry("hyper.charge_tier1_radius",32.0d,1,128,"壹阶聚能弹火环半径","壹阶扩散火环最大半径，单位格，最终不超过128。"),
        new Entry("hyper.charge_tier2_radius",64.0d,1,128,"贰阶聚能弹火环半径","贰阶扩散火环最大半径，单位格，最终不超过128。"),
        new Entry("hyper.grip_radius_multiplier",2.0d,1,128,"握把火环半径倍率","有效握把对扩散火环半径的倍率，最终不超过128格。"),
        new Entry("hyper.charge_cost",100,0,1000,"聚能弹开火能量消耗","一次成功伤害批次消耗的能量单位，1000为满能量；空枪仍免费。"),
        new Entry("hyper.charge_root_cost",50,0,1000,"聚能弹禁锢额外能量消耗","引爆已有五层减速目标时额外消耗的能量单位。"),
        new Entry("hyper.charge_recovery_per_tick",5,5,1000,"聚能弹每tick恢复能量","技能结束后每tick恢复的能量单位，允许5至1000。"),
        new Entry("hyper.charge_slow_ticks",100,0,320,"聚能弹减速持续时间","每次成功引爆后的减速持续tick；0关闭减速。"),
        new Entry("hyper.charge_root_ticks",20,0,320,"聚能弹禁锢持续时间","五层减速后移动禁锢持续tick；0关闭禁锢。"),
        new Entry("hyper.core_cooldown_ticks",300,0,72000,"聚能核心引爆后冷却","G成功引爆批次后的冷却时间，20tick=1秒。"),
        new Entry("hyper.core_max_orbs",15,1,15,"聚能核心红蓝球总上限","同时存在的红球与蓝球总数量，最多15。"),
        new Entry("hyper.core_blue_capacity",200.0d,1,1000,"普通蓝球充能上限","累计实际伤害充能上限，允许1至1000。"),
        new Entry("hyper.core_red_capacity",400.0d,1,1000,"普通红球充能上限","累计实际伤害充能上限，允许1至1000。"),
        new Entry("hyper.stock_blue_capacity",500.0d,1,1000,"枪托蓝球充能上限","累计实际伤害充能上限，允许1至1000。"),
        new Entry("hyper.stock_red_capacity",1000.0d,1,1000,"枪托红球充能上限","累计实际伤害充能上限，允许1至1000。"),
        new Entry("hyper.core_refund_energy",50,0,1000,"普通每球引爆回复能量","1000为满能量，默认50即5%；按实际成功引爆球逐个回复。"),
        new Entry("hyper.stock_refund_energy",100,0,1000,"枪托每球引爆回复能量","有效枪托每球回复的能量单位，默认100即10%。"),
        new Entry("hyper.core_shield_enabled",true,0,1,"球充能抵消视野外伤害","带球目标从视野外造成伤害时，消耗该目标球的充能抵消伤害。"),
        new Entry("hyper.overload_step_permille",25,0,10000,"过载每秒增伤千分数","手持时间每满一秒的充能增量；25代表2.5%。"),
        new Entry("hyper.overload_a_cap_permille",100,0,10000,"过载A上限千分数","切换至其他武器后增伤上限；100代表10%。"),
        new Entry("hyper.overload_b_cap_permille",150,0,10000,"过载B上限千分数","切回聚能装甲后的增伤上限；150代表15%。"),
        new Entry("hyper.overload_duration_ticks",200,1,72000,"过载激活后持续时间","切换后增伤持续tick，20tick=1秒。"),
        new Entry("hyper.magazine_overload_multiplier",2.0d,1,100,"弹匣过载增速与上限倍率","有效专属弹匣同时乘算过载每秒增量和A/B上限。"),
        new Entry("hyper.ultimate_stone_enabled",true,0,1,"终极强化石开关","仅影响二阶聚能装甲激光槽的这类强化效果；其他武器原有效果保留。"),
        new Entry("hyper.ability_stone_enabled",true,0,1,"异能强化石开关","仅影响二阶聚能装甲激光槽的这类强化效果；其他武器原有效果保留。"),
        new Entry("hyper.fusion_stone_enabled",true,0,1,"融合强化石开关","仅影响二阶聚能装甲激光槽的这类强化效果；其他武器原有效果保留。"),
        new Entry("hyper.creative_stone_enabled",true,0,1,"创造石聚能专属效果开关","仅影响二阶聚能装甲激光槽的这类强化效果；其他武器原有效果保留。"),
        new Entry("hyper.ultimate_core_charge_multiplier",2.0d,1,100,"终极强化球充能效率倍率","终极、融合、创造石的聚能核心实际伤害充能倍率；不改变球容量。"),
        new Entry("hyper.ability_outside_view_enabled",true,0,1,"异能强化视野外聚能弹开关","允许异能、融合、创造石引爆视野外标记目标，仍检查场景遮挡。"),
        new Entry("hyper.ability_core_cooldown_reduction_ticks",40,0,72000,"异能强化每球减少G冷却","异能、融合、创造石每成功引爆一球减少的G冷却tick，20tick=1秒。"),
        new Entry("hyper.ray_damage_distance",64.0d,0.1,1024,"射线每增加一倍伤害所需距离","累计往返飞行多少格增加一倍基础伤害；伤害倍率受下面上限限制。"),
        new Entry("hyper.ray_damage_multiplier_cap",2.0d,1,100,"普通射线总伤害倍率上限","不含其他战斗属性的最大射线距离增伤总倍率。"),
        new Entry("hyper.muzzle_ray_damage_multiplier_cap",3.0d,1,100,"枪口射线总伤害倍率上限","安装有效聚能导流枪口后的射线距离增伤总倍率上限。"),
        new Entry("hyper.ray_stun_ticks",10,0,200,"射线命中眩晕时间","射线命中目标眩晕tick，20tick=1秒；0关闭眩晕。"),
        new Entry("hyper.muzzle_refund_per_hit",1,0,100,"枪口每程命中回复子弹","有效枪口每程每次命中回复的子弹数量，不超过原弹容量。")
    );
    private static final Map<String,Entry> BY_KEY;
    static {var map=new LinkedHashMap<String,Entry>();for(var e:ENTRIES)if(map.put(e.key,e)!=null)throw new IllegalStateException(e.key);BY_KEY=Map.copyOf(map);}
    private HyperConfig(){}
    public static void define(ForgeConfigSpec.Builder builder,Map<String,ForgeConfigSpec.ConfigValue<?>> values,Map<String,Object> defaults){
        for(var e:ENTRIES){
            builder.comment(e.label+"；"+e.tooltip,"与现有全局配置同步；修改后重启游戏或服务器。");
            ForgeConfigSpec.ConfigValue<?> option=e.value instanceof Boolean flag?builder.define(e.key,flag):e.value instanceof Integer integer?
                builder.defineInRange(e.key,integer,(int)e.min,(int)e.max):builder.defineInRange(e.key,((Number)e.value).doubleValue(),e.min,e.max);
            values.put(e.key,option);defaults.put(e.key,e.value);
        }
    }
    public static Object value(String suffix){
        String key=suffix.startsWith("hyper.")?suffix:"hyper."+suffix;Entry e=BY_KEY.get(key);if(e==null)throw new IllegalArgumentException(key);
        try{return ArsenalConfig.value(key);}catch(IllegalArgumentException legacy){return e.value;}
    }
    public static double number(String key){return ((Number)value(key)).doubleValue();}
    public static int integer(String key){return ((Number)value(key)).intValue();}
    public static boolean enabled(String key){return (Boolean)value(key);}
}
