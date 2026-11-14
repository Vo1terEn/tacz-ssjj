package cn.ssjjzeus.client;

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

/** Supplied tier-II rear shell as a standalone item; the gun already contains this geometry. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusAdventStockRenderer {
    public static final ResourceLocation ID=new ResourceLocation("ssjj_zeus","thunder_advent_stock");
    private static volatile DimensionMeshRenderer.Geometry geometry;
    private static final Map<BedrockAttachmentModel,DimensionMeshRenderer.Geometry> BOUND=new WeakHashMap<>(),REJECTED=new WeakHashMap<>();
    private ZeusAdventStockRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusAdventStockRenderer::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        geometry=null;BOUND.clear();REJECTED.clear();
        try(var stream=resources.open(new ResourceLocation("ssjj_zeus","source_attachment/thunder_advent_stock.json"));
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var fresh=new Gson().fromJson(reader,DimensionMeshRenderer.Geometry.class);
            validate(fresh);geometry=fresh;
        }catch(Exception error){LogUtils.getLogger().error("Cannot load supplied Zeus energy stock rear shell",error);}
    }
    public static void validate(DimensionMeshRenderer.Geometry data) {
        DimensionMeshRenderer.validate(data);
        for(var part:data.parts)if(!"item_extract".equals(part.bone))
            throw new IllegalArgumentException("Extracted Zeus rear shell must use the standalone item anchor");
    }
    public static void attach(BedrockAttachmentModel model,DimensionMeshRenderer.Geometry data) {
        validate(data);DimensionAttachmentRenderer.attach(model,data);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var data=geometry;if(data==null)return;
        TimelessAPI.getClientAttachmentIndex(ID).ifPresent(index->{
            var model=index.getAttachmentModel();
            if(model==null||BOUND.get(model)==data||REJECTED.get(model)==data)return;
            try{attach(model,data);BOUND.put(model,data);}
            catch(RuntimeException error){REJECTED.put(model,data);LogUtils.getLogger().error("Cannot bind supplied Zeus energy stock rear shell",error);}
        });
    }
}
