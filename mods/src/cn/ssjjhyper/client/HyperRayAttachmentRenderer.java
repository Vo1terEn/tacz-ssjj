package cn.ssjjhyper.client;

import cn.ssjjdimension.client.DimensionAttachmentRenderer;
import cn.ssjjdimension.client.DimensionMeshRenderer;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Standalone source-mesh muzzle item. The complete Hyper body already shows this muzzle when installed. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperRayAttachmentRenderer {
    public static final ResourceLocation ID=new ResourceLocation("ssjj_hyper","energy_ray_muzzle");
    private static volatile DimensionMeshRenderer.Geometry geometry;
    private static final Map<BedrockAttachmentModel,DimensionMeshRenderer.Geometry> BOUND=new WeakHashMap<>(),REJECTED=new WeakHashMap<>();
    private HyperRayAttachmentRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperRayAttachment(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)HyperRayAttachmentRenderer::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        geometry=null;BOUND.clear();REJECTED.clear();
        try(var stream=resources.open(new ResourceLocation("ssjj_hyper","source_attachment/energy_ray_muzzle.json"));
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var fresh=new Gson().fromJson(reader,DimensionMeshRenderer.Geometry.class);
            validate(fresh);geometry=fresh;
        }catch(Exception error){LogUtils.getLogger().error("Cannot load extracted Hyper energy ray muzzle item",error);}
    }
    public static void validate(DimensionMeshRenderer.Geometry data) {
        DimensionMeshRenderer.validate(data);
        for(var part:data.parts)if(!"item_extract".equals(part.bone))
            throw new IllegalArgumentException("Extracted Zeus muzzle must use the standalone item anchor");
    }
    public static void attach(BedrockAttachmentModel model,DimensionMeshRenderer.Geometry data) {
        validate(data);
        // Reuse the existing native-item callback and currentGunItem duplicate guard.
        DimensionAttachmentRenderer.attach(model,data);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var data=geometry;if(data==null)return;
        TimelessAPI.getClientAttachmentIndex(ID).ifPresent(index->{
            var model=index.getAttachmentModel();
            if(model==null||BOUND.get(model)==data||REJECTED.get(model)==data)return;
            try{attach(model,data);BOUND.put(model,data);}
            catch(RuntimeException error){REJECTED.put(model,data);LogUtils.getLogger().error("Cannot bind extracted Hyper energy ray muzzle item",error);}
        });
    }
}
