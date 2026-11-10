package cn.fusionarsenal.config;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.server.ServerLifecycleHooks;

/** One atomic disk replacement and a reload of the ORIGINAL Forge-owned configuration object. */
public final class ConfigPersistence {
 private static volatile ModConfig bound;
 private ConfigPersistence(){}
 public static void bind(ModConfig config){if(config.getSpec()==ArsenalConfig.SPEC)bound=config;}
 public static void unbind(ModConfig config){if(bound==config)bound=null;}
 static synchronized boolean apply(Map<String,Object> changes){
  Objects.requireNonNull(changes,"changes");
  if(changes.isEmpty())return false;
  var checkedChanges=new LinkedHashMap<String,Object>();
  for(var entry:changes.entrySet())checkedChanges.put(entry.getKey(),ArsenalConfig.checked(entry.getKey(),entry.getValue()));
  if(!ArsenalConfig.SPEC.isLoaded())throw new IllegalStateException("配置尚未加载，请稍后重试。");
  var modConfig=bound;
  if(modConfig==null||!(modConfig.getConfigData() instanceof CommentedFileConfig live))throw new IllegalStateException("全局配置未绑定到Forge文件，无法保存。");
  var server=ServerLifecycleHooks.getCurrentServer();
  if(server!=null&&!server.isSameThread())throw new IllegalStateException("玩法配置必须在服务器线程保存。");
  var beforeValues=new LinkedHashMap<String,Object>();
  for(String key:ArsenalConfig.defaults().keySet())beforeValues.put(key,ArsenalConfig.checked(key,ArsenalConfig.localValue(key)));
  Path path=live.getNioPath().toAbsolutePath().normalize();
  if(!path.getFileName().toString().equals(ArsenalConfig.FILE))throw new IllegalStateException("拒绝写入其他配置文件。");
  Path temp=null;byte[] original=null,candidate=null;boolean replaced=false;
  boolean gameplay=false;
  try{
   original=Files.readAllBytes(path);
   // Parse exactly the bytes used by the concurrent-edit guard, not stale Forge caches.
   var disk=readValues(original);var after=new LinkedHashMap<>(disk);after.putAll(checkedChanges);
   for(String key:after.keySet())if(!key.startsWith("render.")&&!Objects.equals(beforeValues.get(key),after.get(key)))gameplay=true;
   if(after.equals(disk)){
    if(!Arrays.equals(original,Files.readAllBytes(path)))throw new IOException("配置刚被其他程序修改，请重新打开界面。");
    if(!after.equals(beforeValues)){live.load();ArsenalConfig.SPEC.afterReload();if(gameplay&&server!=null)ConfigSync.sendAll(server);}
    return false;
   }
   temp=Files.createTempFile(path.getParent(),".ssjjrpg-",".toml");
   try(var target=CommentedFileConfig.builder(temp).sync().preserveInsertionOrder().build()){
    for(var entry:after.entrySet())target.set(entry.getKey(),entry.getValue());
    ArsenalConfig.SPEC.correct(target);target.save();
    if(!ArsenalConfig.SPEC.isCorrect(target))throw new IOException("生成的配置未通过校验。");
   }
   candidate=Files.readAllBytes(temp);
   if(!Arrays.equals(original,Files.readAllBytes(path)))throw new IOException("配置刚被其他程序修改，请重新打开界面。");
   replace(temp,path);temp=null;replaced=true;
   live.load();ArsenalConfig.SPEC.afterReload();
   for(var entry:after.entrySet())if(!Objects.equals(ArsenalConfig.localValue(entry.getKey()),entry.getValue()))throw new IOException("配置重新加载校验失败。 "+entry.getKey());
   if(gameplay&&server!=null)ConfigSync.sendAll(server);
   return true;
  }catch(Exception failure){
   if(replaced&&original!=null){
    try{
     if(!Arrays.equals(candidate,Files.readAllBytes(path)))throw new IOException("配置已被其他程序修改，跳过回滚以免覆盖外部内容。");
     Path rollback=Files.createTempFile(path.getParent(),".ssjjrpg-rollback-",".toml");
     try{Files.write(rollback,original);replace(rollback,path);}finally{Files.deleteIfExists(rollback);}
     live.load();ArsenalConfig.SPEC.afterReload();
     if(gameplay&&server!=null)ConfigSync.sendAll(server);
    }catch(Exception rollback){failure.addSuppressed(rollback);}
   }
   throw new IllegalStateException("保存配置失败，请检查日志与文件写入权限。",failure);
  }finally{
   if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}
  }
 }
 private static LinkedHashMap<String,Object> readValues(byte[] raw)throws IOException{
  String text=new String(raw,StandardCharsets.UTF_8);if(text.startsWith("\ufeff"))text=text.substring(1);
  var parsed=new TomlParser().parse(text);var found=new HashMap<String,Object>();
  for(var section:parsed.valueMap().entrySet()){
   if(!(section.getValue() instanceof UnmodifiableConfig table))throw new IOException("配置节结构不正确："+section.getKey());
   for(var entry:table.valueMap().entrySet()){
    String key=section.getKey()+"."+entry.getKey();found.put(key,ArsenalConfig.checked(key,entry.getValue()));
   }
  }
  if(!found.keySet().equals(ArsenalConfig.defaults().keySet()))throw new IOException("配置项不完整，请先检查磁盘中的全局配置。");
  var result=new LinkedHashMap<String,Object>();for(String key:ArsenalConfig.defaults().keySet())result.put(key,found.get(key));return result;
 }
 private static void replace(Path source,Path destination)throws IOException{
  try{Files.move(source,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException e){Files.move(source,destination,StandardCopyOption.REPLACE_EXISTING);}
 }
}
