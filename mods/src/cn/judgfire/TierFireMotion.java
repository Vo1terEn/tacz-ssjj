package cn.judgfire;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import java.util.function.Consumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** The new tiers use the common source skinning with the original 1024px UV stream. */
public final class TierFireMotion {
  private static volatile MotionSkin.Mesh tier;
  private TierFireMotion() {}

  /** Reuse the exact production default/LOD installer for each new gameplay index. */
  public static void installDisplays(Consumer<ClientGunIndex> installer) {
    TimelessAPI.getClientGunIndex(new ResourceLocation("judgfire:judgment_flame_base")).ifPresent(installer);
    TimelessAPI.getClientGunIndex(new ResourceLocation("judgfire:judgment_flame_tier1")).ifPresent(installer);
  }

  public static boolean withTier(boolean legacy, BedrockGunModel model) {
    return legacy || model.getNode("tier_original_skin_anchor") != null;
  }

  public static MotionSkin.Mesh select(MotionSkin.Mesh fallback, BedrockGunModel model) {
    return model.getNode("tier_original_skin_anchor") != null && tier != null ? tier : fallback;
  }

  public static void reload(ResourceManager resources) {
    tier = null;
    try (var stream = resources.open(new ResourceLocation("judgfire:rig/judgment_flame_tiers.json"));
         var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      tier = new Gson().fromJson(reader, MotionSkin.Mesh.class);
    } catch (Exception error) {
      // Existing Qinglong source UV mesh is geometrically identical and remains a usable fallback.
      LogUtils.getLogger().error("Cannot load Judgment Flame tier original-UV mesh", error);
    }
  }

}
