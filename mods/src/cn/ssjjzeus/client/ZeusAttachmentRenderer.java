package cn.ssjjzeus.client;

import cn.ssjjdimension.client.DimensionAttachmentRenderer;
import cn.ssjjdimension.client.DimensionMeshRenderer;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

/** Source-mesh items share a lifecycle; each attachment keeps its own binding state. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusAttachmentRenderer {
    private static final List<Attachment> ATTACHMENTS=List.of(
        new Attachment("thunder_advent_stock","supplied Zeus energy stock rear shell","rear shell"),
        new Attachment("thunder_energy_mag","supplied Zeus energy magazine drum","drum"),
        new Attachment("thunder_shock_grip","supplied Zeus shock foregrip","foregrip"),
        new Attachment("thunder_burst","extracted Zeus Thunder Burst muzzle item","muzzle"));
    private ZeusAttachmentRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusAttachmentRenderer::reload);
        }
    }
    public static void reload(ResourceManager resources) {
        for(var attachment:ATTACHMENTS)attachment.reload(resources);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var attachment:ATTACHMENTS)attachment.bind();
    }
    public static void validate(ResourceLocation id,DimensionMeshRenderer.Geometry data) {
        attachment(id).validate(data);
    }
    public static void attach(ResourceLocation id,BedrockAttachmentModel model,DimensionMeshRenderer.Geometry data) {
        attachment(id).attach(model,data);
    }
    private static Attachment attachment(ResourceLocation id) {
        for(var attachment:ATTACHMENTS)if(attachment.id.equals(id))return attachment;
        throw new IllegalArgumentException("Unknown Zeus source attachment: "+id);
    }

    private static final class Attachment {
        private final ResourceLocation id,resource;
        private final String description,anchor;
        private volatile DimensionMeshRenderer.Geometry geometry;
        private final Map<BedrockAttachmentModel,DimensionMeshRenderer.Geometry> bound=new WeakHashMap<>(),rejected=new WeakHashMap<>();
        private Attachment(String name,String description,String anchor) {
            id=new ResourceLocation("ssjj_zeus",name);
            resource=new ResourceLocation("ssjj_zeus","source_attachment/"+name+".json");
            this.description=description;this.anchor=anchor;
        }
        private void reload(ResourceManager resources) {
            geometry=null;bound.clear();rejected.clear();
            try(var stream=resources.open(resource);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var fresh=new Gson().fromJson(reader,DimensionMeshRenderer.Geometry.class);
                validate(fresh);geometry=fresh;
            }catch(Exception error){LogUtils.getLogger().error("Cannot load "+description,error);}
        }
        private void validate(DimensionMeshRenderer.Geometry data) {
            DimensionMeshRenderer.validate(data);
            for(var part:data.parts)if(!"item_extract".equals(part.bone))
                throw new IllegalArgumentException("Extracted Zeus "+anchor+" must use the standalone item anchor");
        }
        private void attach(BedrockAttachmentModel model,DimensionMeshRenderer.Geometry data) {
            validate(data);DimensionAttachmentRenderer.attach(model,data);
        }
        private void bind() {
            var data=geometry;if(data==null)return;
            TimelessAPI.getClientAttachmentIndex(id).ifPresent(index->{
                var model=index.getAttachmentModel();
                if(model==null||bound.get(model)==data||rejected.get(model)==data)return;
                try{attach(model,data);bound.put(model,data);}
                catch(RuntimeException error){rejected.put(model,data);LogUtils.getLogger().error("Cannot bind "+description,error);}
            });
        }
    }
}
