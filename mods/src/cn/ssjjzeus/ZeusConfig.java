package cn.ssjjzeus;

import cn.fusionarsenal.config.ArsenalConfig;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;

/** Defined inside the existing ssjjrpg.toml spec; values use its server authority/sync. */
public final class ZeusConfig {
    public record Entry(String key,Object value,double min,double max,String comment) {}
    private static Entry n(String key,Number value,double min,double max,String comment){return new Entry("zeus."+key,value,min,max,comment);}
    private static Entry b(String key,String comment){return new Entry("zeus."+key,true,0,1,comment);}
    public static final List<Entry> ENTRIES=List.of(
        new Entry("combat.penetration_damage_bonus",false,0,1,"是否将超出目标抗性的能量穿透转换为额外增伤；关闭仍抵消抗性，保留抗性减伤"),
        new Entry("combat.penetration_ignore_damage_reduction",true,0,1,"是否开启能量抗性穿透无视减伤；预留配置，当前暂不生效"),
        b("thunder_enabled","一阶与二阶雷核爆裂开关；关闭时恢复原生弹丸"),
        n("orb_speed",1.5d,.1,8,"雷球速度，格/tick；20TPS时默认30格/秒，预判同源读取"),
        n("orb_lifetime_ticks",60,1,1200,"雷球最长飞行时间，20tick=1秒；默认60tick=3秒"),
        n("orb_contact_radius",.15d,0,2,"雷球扫掠碰撞半径（格）"),
        n("orb_damage_multiplier",1d,0,100,"一阶及二阶玩家与幻影雷球的额外伤害倍率"),
        n("blast_size",1d,.1,16,"未安装枪口时爆炸长宽高（格），非半径"),
        b("muzzle_enabled","专属枪口雷霆爆裂技能增强开关"),
        n("muzzle_blast_size",1.5d,.1,16,"专属枪口增强爆炸长宽高（格），非半径"),
        n("muzzle_damage_multiplier",1.5d,0,100,"专属枪口增强雷球伤害倍率"),
        b("refund_enabled","专属枪口命中有效目标返弹开关，玩家与幻影共用"),
        n("refund_per_target",1,0,100,"每个有效爆炸目标返还原开火枪械的子弹数，不超过弹容量"),
        b("energy_enabled","二阶雷霆能量充能与闪电链开关"),
        n("energy_per_orb",25,1,100,"一颗成功命中雷球的充能百分数；多目标仍只累计一次"),
        b("magazine_enabled","专属弹匣雷霆能量增强开关"),
        n("magazine_energy_per_orb",50,1,100,"安装专属弹匣后每颗雷球充能百分数"),
        n("chain_damage_fraction",.3d,0,10,"普通闪电链伤害占触发雷球伤害的比例"),
        n("magazine_chain_damage_fraction",.5d,0,10,"专属弹匣闪电链伤害比例"),
        n("chain_hops",1,0,16,"普通闪电链击中自身后可额外传导的目标数量"),
        n("magazine_chain_hops",2,0,16,"专属弹匣闪电链可额外传导的目标数量；同一条链不重复命中"),
        n("chain_range",3d,.1,64,"每次闪电链传导距离（格），不穿墙"),
        b("shock_enabled","二阶闪电链施加非药水感电开关"),
        n("shock_ticks",100,1,72000,"感电持续时间，重复施加刷新，20tick=1秒"),
        n("shock_damage_bonus",.15d,0,10,"感电期间宙斯之手额外增伤比例，0.15=15%"),
        b("grip_enabled","专属握把感电增强开关"),
        n("grip_shock_damage_bonus",.3d,0,10,"安装专属握把后感电额外增伤比例"),
        b("shock_recovery_enabled","闪电链击中已经感电目标时恢复宙斯降临进度开关"),
        n("shock_recovery_fraction",.2d,0,1,"每次有效链命中已感电目标恢复的自然充能周期比例；默认40秒的20%=8秒"),
        b("advent_enabled","二阶宙斯降临召唤技能开关，关闭会清除现有幻影"),
        b("focus_enabled","二阶右键集火标记与指令开关"),
        b("stock_enabled","专属枪托宙斯降临增强开关"),
        n("phantom_lifetime_ticks",400,1,72000,"基础幻影持续时间，20tick=1秒"),
        n("stock_phantom_lifetime_ticks",500,1,72000,"安装专属枪托后幻影持续时间"),
        n("summon_recharge_ticks",800,1,72000,"每恢复一次召唤次数所需在线时间，20tick=1秒"),
        n("summon_capacity",4,1,32,"基础最多储存召唤次数"),
        n("stock_summon_capacity",6,1,32,"专属枪托最多储存召唤次数"),
        n("phantom_limit",3,1,16,"基础最多同时存在幻影数"),
        n("stock_phantom_limit",5,1,16,"专属枪托最多同时存在幻影数"),
        n("summon_interval_ticks",37,37,72000,"成功召唤最短间隔；不得短于原素材完整动画37tick，客户端仍等待真实动画结束"),
        n("phantom_range",64d,1,128,"幻影自动索敌和集火攻击距离（格）"),
        n("focus_range",64d,1,128,"玩家右键集火标记最大距离（格）"),
        b("prediction_enabled","幻影按速度、可用导航路径与跳跃落地趋势预判；关闭则瞄准当前位置"),
        b("phantom_energy_enabled","幻影攻击累计并释放原召唤枪械的雷霆能量"),
        b("phantom_refund_enabled","幻影有效命中为原召唤枪械返弹；仍需安装增强枪口"),
        b("replace_oldest_phantom","达到同时存在上限后再次召唤是否替换最早幻影；关闭则不消耗次数、不召唤"),
        n("attachment_energy_resistance",250d,0,1000000,"每件非瞄准镜宙斯之手专属配件增加的能量抗性"),
        n("attachment_energy_penetration",250d,0,1000000,"每件非瞄准镜宙斯之手专属配件增加的能量抗性穿透"),
        n("attachment_energy_strength",250d,0,1000000,"每件非瞄准镜宙斯之手专属配件增加的能量强度")
    );
    private static final Map<String,Entry> BY_KEY;
    static {var map=new LinkedHashMap<String,Entry>();for(var e:ENTRIES)if(map.put(e.key,e)!=null)throw new IllegalStateException(e.key);BY_KEY=Map.copyOf(map);}
    private ZeusConfig() {}
    public static void define(ForgeConfigSpec.Builder builder,Map<String,ForgeConfigSpec.ConfigValue<?>> values,Map<String,Object> defaults) {
        for(var e:ENTRIES){
            builder.comment(e.comment,"与现有全局配置同步；修改后重启游戏或服务器。数值范围由配置规范校验。");
            ForgeConfigSpec.ConfigValue<?> option=e.value instanceof Boolean flag?builder.define(e.key,flag):e.value instanceof Integer integer?
                builder.defineInRange(e.key,integer,(int)e.min,(int)e.max):builder.defineInRange(e.key,((Number)e.value).doubleValue(),e.min,e.max);
            values.put(e.key,option);defaults.put(e.key,e.value);
        }
    }
    public static Object value(String suffix) {
        String key=suffix.startsWith("combat.")?suffix:"zeus."+suffix;Entry e=BY_KEY.get(key);if(e==null)throw new IllegalArgumentException(key);
        try {return ArsenalConfig.value(key);}catch(IllegalArgumentException legacy){return e.value;}
    }
    public static double number(String suffix){return ((Number)value(suffix)).doubleValue();}
    public static int integer(String suffix){return ((Number)value(suffix)).intValue();}
    public static boolean enabled(String suffix){return (Boolean)value(suffix);}
}
