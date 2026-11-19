package cn.ssjjdark;

import cn.fusionarsenal.config.ArsenalConfig;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/** Additive options in the existing authoritative, synchronized ssjjrpg.toml. */
public final class DarkCrystalConfig {
    public record Entry(String key,double value,double minimum,double maximum,String comment){}
    public static final List<Entry> ENTRIES=List.of(
        entry("dragon_tier1_damage",15,0,1000000,"一阶龙印斩每个龙之势的基础伤害；0表示不造成伤害。"),
        entry("dragon_tier2_damage",20,0,1000000,"二阶龙印斩每个龙之势的基础伤害；数量和飞行参数保持原规则。"),
        entry("wrath_damage_per_layer",.05,0,10,"绝·怒意每层额外伤害比例；0.05表示每层增加5%。"),
        entry("wrath_reduction_per_layer",.02,0,1,"二阶绝·怒意每层减伤比例；0.02表示每层减少2%，所有倍率结算后减伤最多100%。"),
        entry("crystal_blade_multiplier",2,0,100,"合法安装水晶刀刃后，该把武器的怒意伤害和减伤每层倍率；不放大终极固定伤害加成。"),
        entry("liberation_health_cost_fraction",.70,0,1,"普通绝·力量解放消耗当前生命值的比例；保留最低正生命值，终极仍固定锁定1点生命。"),
        entry("liberation_blast_damage",30,0,1000000,"力量解放中心爆发的基础伤害；0表示不造成伤害。"),
        entry("liberation_blast_radius",6,0,64,"力量解放中心爆发与起手击退的判定半径，单位为格。"),
        entry("liberation_speed_bonus",.20,0,10,"力量解放期间额外移动速度比例；0.20表示增加20%。"),
        entry("liberation_dragon_multiplier",2,0,100,"力量解放期间龙之势伤害倍率。"),
        entry("liberation_wrath_reduction_multiplier",2,0,100,"力量解放期间绝·怒意减伤倍率；最终减伤最多100%。"),
        entry("shield_capacity_multiplier",2,0,100,"未安装水晶核心时，护盾上限相对最大生命值的倍率。"),
        entry("core_shield_capacity_multiplier",4,0,100,"合法安装水晶核心时，护盾上限相对最大生命值的倍率。"),
        entry("sacrifice_shield_multiplier",2,0,100,"未安装水晶核心时，实际消耗生命值转为初始护盾的倍率，仍受护盾上限限制。"),
        entry("core_sacrifice_shield_multiplier",4,0,100,"合法安装水晶核心时，实际消耗生命值转为初始护盾的倍率，仍受护盾上限限制。"),
        entry("ultimate_wrath_bonus",.99,0,100,"终极期间固定额外伤害比例；0.99表示增加99%，创造之石绕过普通伤害事件的伤害也使用此值。"),
        entry("ending_health_fraction",.30,0,1,"终极自然结束时恢复到最大生命值的比例；0表示不执行结束回血。")
    );
    private DarkCrystalConfig(){}
    private static Entry entry(String name,double value,double min,double max,String comment){return new Entry("dark_crystal."+name,value,min,max,comment);}
    public static Map<String,ForgeConfigSpec.ConfigValue<?>> define(Map<String,ForgeConfigSpec.ConfigValue<?>> values,ForgeConfigSpec.Builder builder,Map<String,Object> defaults){
        for(var entry:ENTRIES){
            if(values.containsKey(entry.key())||defaults.containsKey(entry.key()))throw new IllegalStateException("Duplicate config: "+entry.key());
            values.put(entry.key(),builder.comment(entry.comment(),"以服务端配置为准；数值必须有限，修改后按原全局配置流程重新加载或重启。").defineInRange(entry.key(),entry.value(),entry.minimum(),entry.maximum()));
            defaults.put(entry.key(),entry.value());
        }
        return values;
    }
    public static double number(String name){return ((Number)ArsenalConfig.value("dark_crystal."+name)).doubleValue();}
}
