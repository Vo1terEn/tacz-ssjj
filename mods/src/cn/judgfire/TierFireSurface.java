package cn.judgfire;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Keep the opaque native surface pass on the actual gun's own diffuse and UV2-flow textures. */
public final class TierFireSurface {
  private TierFireSurface() {}
  private static final Map<Matrix4f,Integer> CAPTURED=new IdentityHashMap<>();
  private static final DefaultFlameEffects.Definition[] DEFINITIONS=new DefaultFlameEffects.Definition[2];

  public static void reload(ResourceManager resources) {
    CAPTURED.clear();Arrays.fill(DEFINITIONS,null);
    String[] names={"base","tier1"};
    for(int tier=0;tier<2;tier++) {
      try(var input=resources.open(new ResourceLocation("judgfire","fx/tiers/"+names[tier]+"_native.json"));
          var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
        var definition=new Gson().fromJson(reader,DefaultFlameEffects.Definition.class);
        if(definition==null||definition.materials==null||definition.groups==null||definition.emitters==null)
          throw new IllegalArgumentException("Incomplete tier surface definition");
        DEFINITIONS[tier]=definition;
      }catch(Exception failure){LogUtils.getLogger().error("Cannot load native tier surface "+names[tier],failure);}
    }
  }
  public static void capture(Matrix4f pose,ItemStack stack,int skin) {
    CAPTURED.remove(pose);if(skin!=0||stack==null)return;
    var gun=IGun.getIGunOrNull(stack);if(gun==null)return;var id=gun.getGunId(stack);if(id==null)return;
    int tier=switch(id.toString()) {
      case "judgfire:judgment_flame_base"->0;
      case "judgfire:judgment_flame_tier1"->1;
      default->-1;
    };
    if(tier>=0){if(CAPTURED.size()>=1024)CAPTURED.clear();CAPTURED.put(pose,tier);}
  }
  public static DefaultFlameEffects.Definition select(DefaultFlameEffects.Definition original,Matrix4f pose) {
    Integer tier=CAPTURED.remove(pose);
    return tier==null||DEFINITIONS[tier]==null?original:DEFINITIONS[tier];
  }
}
