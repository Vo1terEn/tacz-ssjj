package cn.ssjjcompat;

import com.mojang.logging.LogUtils;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Finish these weapons' cold assets while the client resource loading screen is active. */
public final class WeaponAssetWarmup {
    private WeaponAssetWarmup() {}

    public static void prepare() {
        long started=System.nanoTime();
        var textures=Minecraft.getInstance().getTextureManager();
        int count=prepare(ClientIndexManager.GUN_DISPLAY,textures::getTexture);
        LogUtils.getLogger().info("[SSJJRPG] Prepared {} weapon displays during resource loading in {} ms",
            count,(System.nanoTime()-started)/1_000_000);
    }

    static int prepare(Map<ResourceLocation,GunDisplayInstance> displays,Consumer<ResourceLocation> loadTexture) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<GunDisplayInstance,Boolean>());
        var textures=new LinkedHashSet<ResourceLocation>();
        int count=0;
        for(var entry:displays.entrySet()) {
            String namespace=entry.getKey().getNamespace();
            if(!namespace.equals("judgfire") && !namespace.equals("ssjj_dark"))continue;
            var display=entry.getValue();
            if(!seen.add(display) || display.getGunModel()==null)continue;
            // Native getters finish CPU preparation without starting or advancing an animation.
            display.getAnimationStateMachine();
            textures.add(display.getModelTexture());
            var lod=display.getLodModel();
            if(lod!=null)textures.add(lod.getRight());
            count++;
        }
        textures.remove(null);
        // This hook runs on the reload apply/render thread. No preload future is joined here.
        textures.forEach(loadTexture);
        return count;
    }
}
