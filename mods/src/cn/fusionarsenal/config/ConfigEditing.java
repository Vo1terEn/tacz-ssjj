package cn.fusionarsenal.config;

import java.util.Map;
import java.util.Objects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Optional configuration-screen facade. No Minecraft client or Cloth types occur in its signatures. */
public final class ConfigEditing {
 private ConfigEditing(){}
 public static Map<String,Object> snapshot(){return ArsenalConfig.snapshot();}
 public static boolean editable(String key){
  ArsenalConfig.option(key);
  return FMLEnvironment.dist==Dist.CLIENT&&ClientConfigEditing.editable(key);
 }
 public static void save(Map<String,Object> values){
  Objects.requireNonNull(values,"values");
  if(values.isEmpty())return;
  if(FMLEnvironment.dist!=Dist.CLIENT)throw new IllegalStateException("游戏内配置界面仅在客户端使用。");
  ClientConfigEditing.save(values);
 }
}
