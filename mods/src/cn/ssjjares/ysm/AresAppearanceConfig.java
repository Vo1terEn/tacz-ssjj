package cn.ssjjares.ysm;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import cn.fusionarsenal.config.ArsenalConfig;
/** Registered in the existing global config and server snapshot. */
public final class AresAppearanceConfig {
 public static final String KEY="ares.change_ysm_model";
 private AresAppearanceConfig(){}
 public static Map<String,ForgeConfigSpec.ConfigValue<?>> define(Map<String,ForgeConfigSpec.ConfigValue<?>> values,ForgeConfigSpec.Builder builder,Map<String,Object> defaults){
  values.put(KEY,builder.comment("阿瑞斯套装是否自动改变YSM模型外观；true开启（默认），false关闭。","关闭后，完整灭世/绝套装及对应饰品不再替换YSM模型；已自动替换的外观恢复原选择，装备属性不变。","以服务端配置为准；修改后重启游戏或服务器。").define(KEY,true));
  defaults.put(KEY,true);return values;
 }
 public static boolean enabled(){return (Boolean)ArsenalConfig.localValue(KEY);}
}
