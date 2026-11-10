package cn.judgfire;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Resource/session bridge for each supplied non-default skin's own source effects. */
public final class NativeSkinBodyBridge {
  private NativeSkinBodyBridge() {}
  private static final String[] NAMES={"","qinglong","frozen","lightning"};
  private static final boolean[] LOADED=new boolean[4];
  private record Key(int owner,int skin) {}
  private static final class Activation {
    final long born;long seen;final SkinBodyEffects.Session session;
    Activation(int owner,int skin,long now){born=seen=now;session=SkinBodyEffects.newSession(skin,((long)owner<<32)^skin*9749L);}
  }
  private static final Map<Key,Activation> SESSIONS=new HashMap<>();
  private static Object level;
  public static void reload(ResourceManager resources) {
    SESSIONS.clear();SkinBodyEffects.clear();java.util.Arrays.fill(LOADED,false);
    for(int skin=1;skin<=3;skin++) {
      try(var input=resources.open(new ResourceLocation("judgfire","fx/skins/"+NAMES[skin]+"_body.json"));
          var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
        var definition=SkinBodyEffects.load(skin,reader,path->path);
        LOADED[skin]=true;
        LogUtils.getLogger().info("Native skin {}: {} body/head layers",NAMES[skin],definition.layerCount());
      }catch(Exception failure){LogUtils.getLogger().error("Cannot load native skin "+NAMES[skin],failure);}
    }
  }
  public static boolean body(Matrix4f pose,OriginalMotion.Frame motion,int skin,int owner) {
    if(skin<1||skin>3||!LOADED[skin])return false;
    Object next=Minecraft.getInstance().level;
    if(next!=level){SESSIONS.clear();level=next;}
    long now=System.nanoTime();SESSIONS.values().removeIf(a->now-a.seen>30_000_000_000L);
    Key key=new Key(owner,skin);Activation active=SESSIONS.get(key);
    if(active==null||now-active.seen>300_000_000L){active=new Activation(owner,skin,now);SESSIONS.put(key,active);}
    active.seen=now;SkinBodyEffects.render(active.session,(now-active.born)/1e9,pose,motion);return true;
  }
}
