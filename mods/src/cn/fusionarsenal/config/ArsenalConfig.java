package cn.fusionarsenal.config;
import java.util.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
/** One global file. Gameplay snapshot is server-authoritative; render.* stays local. */
public final class ArsenalConfig {
 public static final String FILE="ssjjrpg.toml";
 public static final ForgeConfigSpec SPEC;
 private static final Map<String,ForgeConfigSpec.ConfigValue<?>> VALUES;
 private static final Map<String,Object> DEFAULTS;
 private static volatile Map<String,Object> remote=Map.of();
 private static boolean registered;
 static {var b=new ForgeConfigSpec.Builder();var values=new LinkedHashMap<String,ForgeConfigSpec.ConfigValue<?>>();var defaults=new LinkedHashMap<String,Object>();
 // 裁决之炎
 b.comment("[SSJJRPG]生死狙击冒险枪包：唯一全局配置。","修改后重启游戏或服务器；玩法以服务端设置为准，render仅影响本机显示。","阶段顺序：基础版(base) → 一阶(tier1) → 二阶(tier2)。true=开启，false=关闭。","比例0.05=5%；倍率1.0=100%；20tick=1秒；距离单位为格。","能量强度按combat.strength_damage_per_1000换算伤害加成。","一、裁决之炎：阶段伤害、炎黄帝冠、红莲技能与配件属性。").push("fire").pop();
 // 1. 阶段伤害
 add(values,defaults,"fire.damage_base",b.comment("【1. 阶段伤害】","基础版：左键激光与帝冠自动攻击伤害倍率；0.5=基础伤害的50%。").defineInRange("fire.damage_base",0.5d,0d,100d),0.5d);
 add(values,defaults,"fire.damage_tier1",b.comment("一阶：左键激光与帝冠自动攻击伤害倍率；0.75=基础伤害的75%。").defineInRange("fire.damage_tier1",0.75d,0d,100d),0.75d);
 add(values,defaults,"fire.damage_tier2",b.comment("二阶：左键激光与帝冠自动攻击伤害倍率；1.0=基础伤害的100%。").defineInRange("fire.damage_tier2",1d,0d,100d),1d);
 // 2. 炎黄帝冠：自动索敌与充能
 add(values,defaults,"fire.crown_enabled",b.comment("【2. 炎黄帝冠：自动索敌与充能】","全阶段帝冠自动攻击开关：true开启，false关闭。").define("fire.crown_enabled",true),true);
 add(values,defaults,"fire.crown_range_base",b.comment("基础版：未装炎黄帝冠枪托时的索敌半径（格），沿用坐标轴边界判定。").defineInRange("fire.crown_range_base",10d,0d,512d),10d);
 add(values,defaults,"fire.crown_range_tier1",b.comment("一阶：未装炎黄帝冠枪托时的索敌半径（格），沿用坐标轴边界判定。").defineInRange("fire.crown_range_tier1",16d,0d,512d),16d);
 add(values,defaults,"fire.crown_range_tier2",b.comment("二阶：未装炎黄帝冠枪托时的索敌半径（格），沿用坐标轴边界判定。").defineInRange("fire.crown_range_tier2",24d,0d,512d),24d);
 add(values,defaults,"fire.stock_range_multiplier",b.comment("合法安装炎黄帝冠枪托后的索敌距离倍率；2.0=对应阶段距离的2倍。").defineInRange("fire.stock_range_multiplier",2d,0d,100d),2d);
 add(values,defaults,"fire.charge_mid_percent",b.comment("帝冠进入中档充能的百分比阈值；34表示34%。").defineInRange("fire.charge_mid_percent",34.0d,0d,100d),34.0d);
 add(values,defaults,"fire.charge_high_percent",b.comment("帝冠进入高档充能的百分比阈值；67表示67%。").defineInRange("fire.charge_high_percent",67.0d,0d,100d),67.0d);
 // 3. 红莲印记：标记与减伤
 add(values,defaults,"fire.lotus_enabled",b.comment("【3. 红莲印记：标记与减伤】","一阶、二阶的红莲印记技能开关：true开启，false关闭。").define("fire.lotus_enabled",true),true);
 add(values,defaults,"fire.lotus_reduction_tier1",b.comment("一阶：来自自己红莲印记目标的伤害减免比例；0.05=5%。").defineInRange("fire.lotus_reduction_tier1",0.05d,0d,1d),0.05d);
 add(values,defaults,"fire.lotus_reduction_tier2",b.comment("二阶：来自自己红莲印记目标的伤害减免比例；0.1=10%。").defineInRange("fire.lotus_reduction_tier2",0.1d,0d,1d),0.1d);
 add(values,defaults,"fire.grip_reduction_multiplier",b.comment("合法安装红莲印记握把后的减伤增强倍率；2.0=减伤翻倍，最终不超过100%。").defineInRange("fire.grip_reduction_multiplier",2d,0d,100d),2d);
 add(values,defaults,"fire.mark_duration_ticks",b.comment("红莲印记持续时间（tick）；100tick=5秒。").defineInRange("fire.mark_duration_ticks",100,1,1200),100);
 // 4. 红莲爆破：技能伤害、冷却与眩晕
 add(values,defaults,"fire.burst_enabled",b.comment("【4. 红莲爆破：技能伤害、冷却与眩晕】","二阶红莲爆破技能开关：true开启，false关闭。").define("fire.burst_enabled",true),true);
 add(values,defaults,"fire.burst_multiplier",b.comment("未装红莲爆破弹匣时，开启技能后的伤害倍率；1.5=150%。").defineInRange("fire.burst_multiplier",1.5d,0d,100d),1.5d);
 add(values,defaults,"fire.magazine_burst_multiplier",b.comment("合法安装红莲爆破弹匣时，开启技能后的伤害倍率；2.0=200%。").defineInRange("fire.magazine_burst_multiplier",2d,0d,100d),2d);
 add(values,defaults,"fire.burst_cooldown_ticks",b.comment("红莲爆破冷却时间（tick），从技能激活时开始计时；500tick=25秒。").defineInRange("fire.burst_cooldown_ticks",500,300,72000),500);
 add(values,defaults,"fire.burst_stun_ticks",b.comment("红莲爆破首次眩晕时间（tick）；60tick=3秒。").defineInRange("fire.burst_stun_ticks",60,1,1200),60);
 add(values,defaults,"fire.burst_restun_ticks",b.comment("爆破期间帝冠再次施加眩晕的时间（tick）；30tick=1.5秒。").defineInRange("fire.burst_restun_ticks",30,1,1200),30);
 add(values,defaults,"fire.burst_immunity_ticks",b.comment("眩晕结束后的眩晕免疫时间（tick）；20tick=1秒。").defineInRange("fire.burst_immunity_ticks",20,1,1200),20);
 // 5. 专属枪托、握把：常驻属性
 add(values,defaults,"fire.accessory_strength",b.comment("【5. 专属枪托、握把：常驻属性】","合法安装炎黄帝冠枪托、红莲印记握把并持枪时，每件增加的能量强度。","能量强度参与通用伤害加成；红莲爆破弹匣不使用此常驻属性。").defineInRange("fire.accessory_strength",800d,0d,1000000000000.0d),800d);
 add(values,defaults,"fire.accessory_resistance",b.comment("合法安装炎黄帝冠枪托、红莲印记握把并持枪时，每件增加的能量抗性。").defineInRange("fire.accessory_resistance",250d,0d,1000000000000.0d),250d);
 add(values,defaults,"fire.accessory_penetration",b.comment("合法安装炎黄帝冠枪托、红莲印记握把并持枪时，每件增加的能量抗性穿透。").defineInRange("fire.accessory_penetration",250d,0d,1000000000000.0d),250d);

 // 天罚之眼
 b.comment("二、天罚之眼：天罚之力、诺法雷构造、王者之心与配件属性。").push("eye").pop();
 // 1. 天罚之力：自带效果与迅捷
 add(values,defaults,"eye.passive_enabled",b.comment("【1. 天罚之力：自带效果与迅捷】","全阶段自带的天罚之力技能开关，包含迅捷、残影与神之注视；true开启，false关闭。").define("eye.passive_enabled",true),true);
 add(values,defaults,"eye.speed_level",b.comment("未装天罚之力握把时的迅捷等级；1=迅捷I，0不授予迅捷。").defineInRange("eye.speed_level",1,0,10),1);
 add(values,defaults,"eye.grip_speed_level",b.comment("合法安装天罚之力握把后的迅捷等级；2=迅捷II，0不授予迅捷。").defineInRange("eye.grip_speed_level",2,0,10),2);
 // 2. 神之注视：分阶段增伤与标记
 add(values,defaults,"eye.gaze_bonus_base",b.comment("【2. 神之注视：分阶段增伤与标记】","基础版：神之注视额外增伤比例；0.125=额外增加12.5%。").defineInRange("eye.gaze_bonus_base",0.125d,0d,100d),0.125d);
 add(values,defaults,"eye.gaze_bonus_tier1",b.comment("一阶：神之注视额外增伤比例；0.1875=额外增加18.75%。").defineInRange("eye.gaze_bonus_tier1",0.1875d,0d,100d),0.1875d);
 add(values,defaults,"eye.gaze_bonus_tier2",b.comment("二阶：神之注视额外增伤比例；0.25=额外增加25%。").defineInRange("eye.gaze_bonus_tier2",0.25d,0d,100d),0.25d);
 add(values,defaults,"eye.grip_gaze_multiplier",b.comment("合法安装天罚之力握把后，对神之注视额外增伤的增强倍率；2.0=额外增伤翻倍。").defineInRange("eye.grip_gaze_multiplier",2d,0d,100d),2d);
 add(values,defaults,"eye.gaze_mark_seconds",b.comment("神之注视标记持续时间（秒）。").defineInRange("eye.gaze_mark_seconds",10,1,3600),10);
 // 3. 诺法雷构造：落雷与星云
 add(values,defaults,"eye.lightning_damage",b.comment("【3. 诺法雷构造：落雷与星云】","安装诺法雷构造后的落雷基础伤害，再乘以下对应阶段的落雷倍率。").defineInRange("eye.lightning_damage",10d,0d,1000000000.0d),10d);
 add(values,defaults,"eye.lightning_scale_base",b.comment("基础版：落雷伤害倍率；0.5=落雷基础伤害的50%。").defineInRange("eye.lightning_scale_base",0.5d,0d,100d),0.5d);
 add(values,defaults,"eye.lightning_scale_tier1",b.comment("一阶：落雷伤害倍率；0.75=落雷基础伤害的75%。").defineInRange("eye.lightning_scale_tier1",0.75d,0d,100d),0.75d);
 add(values,defaults,"eye.lightning_scale_tier2",b.comment("二阶：落雷伤害倍率；1.0=落雷基础伤害的100%。").defineInRange("eye.lightning_scale_tier2",1d,0d,100d),1d);
 add(values,defaults,"eye.cloud_damage",b.comment("诺法星云每次伤害脉冲的基础伤害。").defineInRange("eye.cloud_damage",20d,0d,1000000000.0d),20d);
 add(values,defaults,"eye.cloud_pulse_ticks",b.comment("诺法星云两次伤害脉冲之间的间隔（tick）；2tick=0.1秒。").defineInRange("eye.cloud_pulse_ticks",2,1,120),2);
 // 4. 诺法电球：分阶段发射权限
 add(values,defaults,"eye.ball_base",b.comment("【4. 诺法电球：分阶段发射权限】","基础版：安装诺法雷构造后是否允许发射电球；true允许，false禁止。").define("eye.ball_base",false),false);
 add(values,defaults,"eye.ball_tier1",b.comment("一阶：安装诺法雷构造后是否允许发射电球；true允许，false禁止。").define("eye.ball_tier1",false),false);
 add(values,defaults,"eye.ball_tier2",b.comment("二阶：安装诺法雷构造后是否允许发射电球；true允许，false禁止。").define("eye.ball_tier2",true),true);
 add(values,defaults,"eye.ball_interval_shots",b.comment("允许发射电球且安装诺法雷构造时，每累计多少发射击生成一个电球。").defineInRange("eye.ball_interval_shots",30,1,100000),30);
 // 5. 王者之心：二阶技能与枪托增强
 add(values,defaults,"eye.king_enabled",b.comment("【5. 王者之心：二阶技能与枪托增强】","二阶自带的王者之心技能开关；true开启，false关闭。","未安装王者之心枪托时，仍使用下方的基础持续时间与冷却。").define("eye.king_enabled",true),true);
 add(values,defaults,"eye.king_duration_seconds",b.comment("未装王者之心枪托：技能持续时间（秒）。").defineInRange("eye.king_duration_seconds",20,1,3600),20);
 add(values,defaults,"eye.king_cooldown_seconds",b.comment("未装王者之心枪托：技能结束后的冷却时间（秒）。").defineInRange("eye.king_cooldown_seconds",20,1,3600),20);
 add(values,defaults,"eye.stock_duration_seconds",b.comment("合法安装王者之心枪托：技能持续时间（秒）。").defineInRange("eye.stock_duration_seconds",30,1,3600),30);
 add(values,defaults,"eye.stock_cooldown_seconds",b.comment("合法安装王者之心枪托：技能结束后的冷却时间（秒）。").defineInRange("eye.stock_cooldown_seconds",10,1,3600),10);
 add(values,defaults,"eye.king_health_threshold",b.comment("王者之心技能落雷增强的目标生命比例阈值；0.5=50%。","目标生命比例高于此值时，应用下方的技能落雷倍率。").defineInRange("eye.king_health_threshold",0.5d,0d,1d),0.5d);
 add(values,defaults,"eye.king_lightning_multiplier",b.comment("目标满足生命阈值条件时的王者之心技能落雷倍率；2.0=2倍。").defineInRange("eye.king_lightning_multiplier",2d,0d,100d),2d);
 add(values,defaults,"eye.king_energy_strength",b.comment("王者之心技能期间增加的能量强度；终极强化石使王者之心常驻时同样生效。").defineInRange("eye.king_energy_strength",10000.0d,0d,1000000000000.0d),10000.0d);
 // 6. 天罚之力、王者之心：常驻属性
 add(values,defaults,"eye.accessory_strength",b.comment("【6. 天罚之力、王者之心：常驻属性】","合法安装天罚之力握把、王者之心枪托并持枪时，每件增加的能量强度。","能量强度参与通用伤害加成。").defineInRange("eye.accessory_strength",1000d,0d,1000000000000.0d),1000d);
 add(values,defaults,"eye.accessory_resistance",b.comment("合法安装天罚之力握把、王者之心枪托并持枪时，每件增加的能量抗性。").defineInRange("eye.accessory_resistance",300d,0d,1000000000000.0d),300d);
 add(values,defaults,"eye.accessory_penetration",b.comment("合法安装天罚之力握把、王者之心枪托并持枪时，每件增加的能量抗性穿透。").defineInRange("eye.accessory_penetration",300d,0d,1000000000000.0d),300d);

 // 原生战斗与能量属性
 b.comment("三、原生战斗与能量属性：阶段伤害倍率在基础伤害之上计算。").push("combat").pop();
 // 1. 裁决左键激光
 add(values,defaults,"combat.weapon_damage",b.comment("【1. 裁决左键激光】","裁决左键激光的基础伤害，再乘fire中对应阶段的伤害倍率。").defineInRange("combat.weapon_damage",16.0d,0d,1000000000.0d),16.0d);
 add(values,defaults,"combat.weapon_through_walls",b.comment("裁决左键激光穿墙开关：true允许穿墙，false关闭。").define("combat.weapon_through_walls",false),false);
 // 2. 帝冠自动攻击
 add(values,defaults,"combat.crown_damage",b.comment("【2. 帝冠自动攻击】","裁决帝冠自动攻击的基础伤害，再乘fire中对应阶段的伤害倍率。").defineInRange("combat.crown_damage",24.0d,0d,1000000000.0d),24.0d);
 add(values,defaults,"combat.crown_through_walls",b.comment("帝冠穿墙索敌开关：true允许，false关闭。").define("combat.crown_through_walls",true),true);
 // 3. 能量强度：通用伤害加成
 add(values,defaults,"combat.strength_damage_per_1000",b.comment("【3. 能量强度：通用伤害加成】","每1000点总能量强度增加的伤害比例；0.01=1%。最终倍率为1+总能量强度/1000×此值。").defineInRange("combat.strength_damage_per_1000",0.01d,0d,100d),0.01d);
 // 4. 创造强化石：常驻属性与预留开关
 add(values,defaults,"combat.creative_stone_strength",b.comment("【4. 创造强化石：常驻属性与预留开关】","合法安装创造强化石并持枪时增加的能量强度；按通用能量强度系数增加伤害。").defineInRange("combat.creative_stone_strength",100000.0d,0d,1000000000000.0d),100000.0d);
 add(values,defaults,"combat.creative_stone_resistance",b.comment("合法安装创造强化石并持枪时增加的能量抗性。").defineInRange("combat.creative_stone_resistance",10000.0d,0d,1000000000000.0d),10000.0d);
 add(values,defaults,"combat.creative_stone_penetration",b.comment("合法安装创造强化石并持枪时增加的能量抗性穿透。").defineInRange("combat.creative_stone_penetration",10000.0d,0d,1000000000000.0d),10000.0d);
 add(values,defaults,"combat.creative_stone_enhanced_attack",b.comment("创造强化石增强攻击预留开关：true开启，false关闭。具体增强攻击功能尚未实现，本选项当前不改变攻击行为。").define("combat.creative_stone_enhanced_attack",true),true);
 // 5. 红莲爆破弹匣：技能期间额外属性
 add(values,defaults,"combat.burst_resistance",b.comment("【5. 红莲爆破弹匣：技能期间额外属性】","合法安装红莲爆破弹匣并开启技能后，额外增加的能量抗性。").defineInRange("combat.burst_resistance",500.0d,0d,1000000000000.0d),500.0d);
 add(values,defaults,"combat.burst_penetration",b.comment("合法安装红莲爆破弹匣并开启技能后，额外增加的能量抗性穿透。").defineInRange("combat.burst_penetration",500.0d,0d,1000000000000.0d),500.0d);

 // 本机显示与性能
 b.comment("四、本机显示与性能：仅影响本客户端画面，不改变伤害、索敌或其他玩家设置。","以下显示开关与复用默认开启；true开启，false关闭。").push("render").pop();
 // 1. 裁决之炎显示开关
 add(values,defaults,"render.fire_gun",b.comment("【1. 裁决之炎显示开关】","显示裁决之炎完整枪体；true显示，false隐藏。").define("render.fire_gun",true),true);
 add(values,defaults,"render.fire_native",b.comment("显示裁决默认和低阶的原生几何特效；true显示，false隐藏。").define("render.fire_native",true),true);
 add(values,defaults,"render.fire_surface",b.comment("显示裁决皮肤的枪身表层特效；true显示，false隐藏。").define("render.fire_surface",true),true);
 add(values,defaults,"render.fire_flow",b.comment("显示裁决独立自然流光；true显示，false隐藏。").define("render.fire_flow",true),true);
 add(values,defaults,"render.fire_world",b.comment("显示裁决弹体与世界命中特效；true显示，false隐藏。").define("render.fire_world",true),true);
 // 2. 天罚之眼显示开关
 add(values,defaults,"render.eye_gun",b.comment("【2. 天罚之眼显示开关】","显示天罚之眼完整枪体；true显示，false隐藏。").define("render.eye_gun",true),true);
 add(values,defaults,"render.eye_effects",b.comment("显示天罚各阶段的枪身与枪口特效；true显示，false隐藏。").define("render.eye_effects",true),true);
 // 3. 临时几何复用与缓存上限
 add(values,defaults,"render.reuse_geometry",b.comment("【3. 临时几何复用与缓存上限】","临时几何复用开关：true复用以减少临时对象分配，false关闭复用以便对照排查。").define("render.reuse_geometry",true),true);
 add(values,defaults,"render.surface_cache_entries",b.comment("每线程、每层持枪排序中心缓存的条目上限。").defineInRange("render.surface_cache_entries",4,1,32),4);
 add(values,defaults,"render.max_cached_vertices",b.comment("每层持枪网格或临时缓冲允许保留的顶点上限。").defineInRange("render.max_cached_vertices",65536,256,262144),65536);
 add(values,defaults,"render.world_pool_entries",b.comment("每层世界特效复用池的条目上限。").defineInRange("render.world_pool_entries",512,16,4096),512);
 add(values,defaults,"render.world_max_vertices",b.comment("每层世界特效复用池允许保留的顶点总上限，包含原始点与展开顶点。").defineInRange("render.world_max_vertices",262144,4096,2097152),262144);
 // 寄生者；沿用服务端权威同步，默认值保持现有武器行为。
 b.comment("五、寄生者：普通形态、炮狙形态与巨炮冲击波。","玩法参数以服务器配置为准；伤害为未安装专属枪口时的基础值。").push("parasite").pop();
 add(values,defaults,"parasite.normal_damage",b.comment("普通形态近距离基础伤害；中、远距离保持原有衰减比例。").defineInRange("parasite.normal_damage",15d,0d,1000000d),15d);
 add(values,defaults,"parasite.cannon_damage",b.comment("炮狙形态基础伤害；合法安装专属枪口时再乘以下方枪口伤害倍率。").defineInRange("parasite.cannon_damage",210d,0d,1000000d),210d);
 add(values,defaults,"parasite.muzzle_damage_multiplier",b.comment("合法安装专属枪口时，普通形态与炮狙形态的伤害倍率；2.0=2倍。").defineInRange("parasite.muzzle_damage_multiplier",2d,0d,100d),2d);
 add(values,defaults,"parasite.cannon_rpm",b.comment("炮狙形态射速，每分钟发数（RPM）；60=每秒1发。").defineInRange("parasite.cannon_rpm",60,1,1200),60);
 add(values,defaults,"parasite.shockwave_range",b.comment("贰阶炮狙巨炮冲击波最大射程（格）；中轴遇阻挡方块时提前截止。").defineInRange("parasite.shockwave_range",64d,1d,256d),64d);
 add(values,defaults,"parasite.shockwave_radius",b.comment("未安装专属弹匣时，贰阶巨炮冲击波路径的作用半径（格）。").defineInRange("parasite.shockwave_radius",1d,.1d,16d),1d);
 add(values,defaults,"parasite.magazine_shockwave_radius",b.comment("合法安装专属弹匣时，贰阶巨炮冲击波路径的作用半径（格）。").defineInRange("parasite.magazine_shockwave_radius",3d,.1d,16d),3d);
 add(values,defaults,"parasite.attraction_enabled",b.comment("巨炮冲击波吸引开关；true吸引目标到中轴，false仅造成原有冲击波伤害。").define("parasite.attraction_enabled",true),true);
 VALUES=Collections.unmodifiableMap(values);DEFAULTS=Collections.unmodifiableMap(defaults);SPEC=b.build();}
 private static void add(Map<String,ForgeConfigSpec.ConfigValue<?>> values,Map<String,Object> defaults,String key,ForgeConfigSpec.ConfigValue<?> option,Object value){values.put(key,option);defaults.put(key,value);}
 private ArsenalConfig(){}
 public static synchronized void register(){if(registered)return;registered=true;ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,SPEC,FILE);ConfigSync.init();var bus=FMLJavaModLoadingContext.get().getModEventBus();bus.addListener(ArsenalConfig::loading);bus.addListener(ArsenalConfig::reload);bus.addListener(ArsenalConfig::unloading);}
 private static void loading(ModConfigEvent.Loading event){if(event.getConfig().getSpec()==SPEC)ConfigPersistence.bind(event.getConfig());}
 private static void unloading(ModConfigEvent.Unloading event){if(event.getConfig().getSpec()==SPEC)ConfigPersistence.unbind(event.getConfig());}
 private static void reload(ModConfigEvent.Reloading event){if(event.getConfig().getSpec()!=SPEC)return;ConfigPersistence.bind(event.getConfig());var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)server.execute(()->ConfigSync.sendAll(server));}
 public static ForgeConfigSpec.ConfigValue<?> option(String key){var value=VALUES.get(key);if(value==null)throw new IllegalArgumentException("Unknown config option: "+key);return value;}
 public static Object localValue(String key){return SPEC.isLoaded()?option(key).get():DEFAULTS.get(require(key));}
 private static String require(String key){if(!DEFAULTS.containsKey(key))throw new IllegalArgumentException("Unknown config option: "+key);return key;}
 public static Object value(String key){require(key);var values=remote;return !net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer()&&!key.startsWith("render.")&&values.containsKey(key)?values.get(key):localValue(key);}
 public static double number(String key){return ((Number)value(key)).doubleValue();}
 public static int integer(String key){return ((Number)value(key)).intValue();}
 public static boolean bool(String key){return (Boolean)value(key);}
 public static boolean enabled(String key){return bool(key);}
 public static Map<String,Object> defaults(){return DEFAULTS;}
 public static Map<String,Object> snapshot(){var result=new LinkedHashMap<String,Object>();for(String key:VALUES.keySet())result.put(key,value(key));return Collections.unmodifiableMap(result);}
 public static Map<String,Object> serverSnapshot(){var result=new LinkedHashMap<String,Object>();for(String key:VALUES.keySet())if(!key.startsWith("render."))result.put(key,localValue(key));return Collections.unmodifiableMap(result);}
 public static Object checked(String key,Object value){Object d=DEFAULTS.get(require(key));if(d instanceof Boolean){if(!(value instanceof Boolean))throw new IllegalArgumentException(key);}else{if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException(key);if(d instanceof Integer){if(n.doubleValue()!=n.intValue())throw new IllegalArgumentException(key);value=n.intValue();}else value=n.doubleValue();}var spec=SPEC.getSpec().get(key);if(!(spec instanceof ForgeConfigSpec.ValueSpec s)||!s.test(value))throw new IllegalArgumentException("Invalid config value "+key+"="+value);return value;}
 public static void applyServer(Map<String,Object> input){var result=new LinkedHashMap<String,Object>();for(var e:input.entrySet()){if(e.getKey().startsWith("render."))throw new IllegalArgumentException("Server cannot override local render settings");result.put(e.getKey(),checked(e.getKey(),e.getValue()));}if(result.size()!=DEFAULTS.keySet().stream().filter(k->!k.startsWith("render.")).count())throw new IllegalArgumentException("Incomplete server config");remote=Collections.unmodifiableMap(result);}
 public static void clearServer(){remote=Map.of();}
}
