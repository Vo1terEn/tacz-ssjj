package cn.fusionarsenal.config;

import java.util.*;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** Loaded only after the common facade has checked the physical client side. */
final class ClientConfigEditing {
 private ClientConfigEditing(){}
 private static boolean remote(){var mc=Minecraft.getInstance();return mc.getConnection()!=null&&!mc.hasSingleplayerServer();}
 static boolean editable(String key){return key.startsWith("render.")||!remote();}
 static void save(Map<String,Object> input){
  Objects.requireNonNull(input,"values");
  if(input.isEmpty())return;
  if(!ArsenalConfig.defaults().keySet().containsAll(input.keySet()))throw new IllegalArgumentException("配置不能添加未知键。");
  boolean remote=remote();var changes=new LinkedHashMap<String,Object>();
  for(String key:ArsenalConfig.defaults().keySet()){
   if(!input.containsKey(key))continue;
   Object value=ArsenalConfig.checked(key,input.get(key));
   if(remote&&!key.startsWith("render."))throw new IllegalArgumentException("服务器玩法配置为只读，不能从客户端修改。");
   changes.put(key,value);
  }
  MinecraftServer server=Minecraft.getInstance().getSingleplayerServer();
  if(server==null||server.isSameThread()){ConfigPersistence.apply(changes);return;}
  if(!server.isRunning()||server.isStopped())throw new IllegalStateException("世界正在关闭，请返回主菜单后修改配置。");
  try{server.submit(()->ConfigPersistence.apply(changes)).join();}
  catch(CompletionException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new IllegalStateException("保存配置失败。",e.getCause());}
 }
}
