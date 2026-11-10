package cn.ssjjcompat;

import com.google.gson.*;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.*;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Context-only presentation bridge. In-gun attachments and native held gun renderers never enter here. */
public final class ItemPresentationCompat {
    private static final Set<String> NAMESPACES=Set.of("eyeofpunishment","judgfire","ssjj_dark","ssjj_dimension","ssjjparasite2","ssjjrpg","eye_judgment_arsenal","ssjj_workbench");
    private static final ThreadLocal<Boolean> WORKBENCH=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> REENTER=ThreadLocal.withInitial(()->false);
    private static ResourceManager lastResources;
    private static JsonObject manifest=new JsonObject();
    private ItemPresentationCompat(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((ResourceManagerReloadListener)resources->{lastResources=null;manifest=new JsonObject();PreviewGeometry.clear();});
        }
    }
    public static boolean ownId(ResourceLocation id){return id!=null&&NAMESPACES.contains(id.getNamespace());}
    public static ResourceLocation contentId(ItemStack stack){
        if(stack==null||stack.isEmpty())return null;
        if(stack.getItem() instanceof IGun gun)return gun.getGunId(stack);
        if(stack.getItem() instanceof IAttachment attachment)return attachment.getAttachmentId(stack);
        if(stack.getItem() instanceof IAmmo ammo)return ammo.getAmmoId(stack);
        if(stack.getItem() instanceof IBlock block)return block.getBlockId(stack);
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
    public static boolean own(ItemStack stack){return ownId(contentId(stack));}
    /** ItemRenderer entry, before its own model transforms and centering. */
    public static boolean render(ItemRenderer renderer,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
        if(context==ItemDisplayContext.GUI||REENTER.get()||!own(stack))return false;
        if(WORKBENCH.get()&&context==ItemDisplayContext.FIXED)return preview(renderer,stack,left,pose,buffers,light,overlay,model);
        if(stack.getItem() instanceof IGun){
            if(context!=ItemDisplayContext.GROUND)return false;
            return groundGun(renderer,stack,context,left,pose,buffers,light,overlay,model);
        }
        pose.pushPose();
        try {
            applyIconTransform(context,left,pose);
            // GUI is the authoritative icon path for native TaCZ attachments/ammo,
            // generated fragments and the original custom workbench item model.
            renderer.render(stack,ItemDisplayContext.GUI,false,pose,buffers,light,overlay,model);
        } finally {pose.popPose();}
        return true;
    }
    public static void applyIconTransform(ItemDisplayContext context,boolean left,PoseStack pose){
        float sign=left?-1:1;
        switch(context){
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                pose.translate(sign*1.13/16.0,3.2/16.0,1.13/16.0);
                pose.mulPose(Axis.YP.rotationDegrees(sign*-90));pose.mulPose(Axis.ZP.rotationDegrees(sign*25));pose.scale(.68f,.68f,.68f);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {pose.translate(0,3/16.0,1/16.0);pose.scale(.55f,.55f,.55f);}
            case GROUND -> {pose.translate(0,2/16.0,0);pose.scale(.5f,.5f,.5f);}
            case HEAD -> {pose.translate(0,13/16.0,7/16.0);pose.mulPose(Axis.YP.rotationDegrees(180));}
            case FIXED -> pose.mulPose(Axis.YP.rotationDegrees(180));
            default -> {}
        }
    }
    private static boolean groundGun(ItemRenderer renderer,ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);if(display==null)return false;
        var held=display.getTransform().getScale().getThirdPerson();var ground=display.getTransform().getScale().getGround();
        pose.pushPose();REENTER.set(true);
        try {
            String namespace=contentId(stack).getNamespace();
            JsonObject groundFrames=data().getAsJsonObject("ground");
            if(groundFrames!=null&&groundFrames.has(display.getSlotTexture().toString())){
                JsonArray offset=groundFrames.getAsJsonArray(display.getSlotTexture().toString());
                pose.translate(offset.get(0).getAsDouble(),offset.get(1).getAsDouble(),offset.get(2).getAsDouble());
            }
            // These two source-mesh rigs use the opposite vertical basis. Their
            // calibrated held/fixed anchors already contain this exact correction.
            if(namespace.equals("ssjj_dimension")||namespace.equals("ssjjparasite2"))pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(ratio(held.x,ground.x),ratio(held.y,ground.y),ratio(held.z,ground.z));
            renderer.render(stack,context,left,pose,buffers,light,overlay,model);
        } finally {REENTER.set(false);pose.popPose();}
        return true;
    }
    public static float ratio(float held,float ground){return ground==0?1:held/ground;}
    private static JsonObject data(){
        ResourceManager resources=Minecraft.getInstance().getResourceManager();
        if(resources!=lastResources){
            lastResources=resources;manifest=new JsonObject();
            try(var in=resources.open(new ResourceLocation("ssjjrpg","item_presentation.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){manifest=JsonParser.parseReader(reader).getAsJsonObject();}
            catch(IOException ignored){}
        }
        return manifest;
    }
    /** The native screen retains its timestamp rotation, zoom buttons, depth and lights. */
    public static void workbenchRender(ItemRenderer renderer,ItemStack stack,ItemDisplayContext context,int light,int overlay,PoseStack pose,MultiBufferSource buffers,net.minecraft.world.level.Level level,int seed){
        boolean previous=WORKBENCH.get();WORKBENCH.set(own(stack));
        try{renderer.renderStatic(stack,context,light,overlay,pose,buffers,level,seed);}finally{WORKBENCH.set(previous);}
    }
    public static boolean previewActive(){return WORKBENCH.get();}
    private static boolean preview(ItemRenderer renderer,ItemStack stack,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel baked){
        pose.pushPose();
        try{
            if(stack.getItem() instanceof IGun){
                var display=TimelessAPI.getGunDisplay(stack).orElse(null);if(display==null)return false;
                var frames=data().getAsJsonObject("preview_guns");String key=display.getSlotTexture().toString();
                if(frames!=null&&frames.has(key)){
                    var fit=frames.getAsJsonArray(key);float scale=fit.get(0).getAsFloat();pose.scale(scale,scale,scale);pose.translate(0,fit.get(1).getAsDouble(),0);
                }
                return groundGun(renderer,stack,ItemDisplayContext.GROUND,left,pose,buffers,light,overlay,baked);
            }
            if(stack.getItem() instanceof IAttachment attachment){
                var index=TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(stack)).orElse(null);
                if(index!=null){
                    var model=index.getAttachmentModel();var bounds=PreviewGeometry.bounds(model);
                    if(bounds!=null){
                        pose.scale(-1,-1,1);PreviewGeometry.fit(pose,bounds);
                        model.render(stack,null,pose,ItemDisplayContext.FIXED,net.minecraft.client.renderer.RenderType.entityCutout(index.getModelTexture()),light,overlay);
                    }else{
                        // Empty installed-only models use TaCZ's own standalone SlotModel.
                        // It is rendered in the same rotating 3D scene, never a screen blit.
                        pose.scale(-1,-1,1);PreviewGeometry.fit(pose,PreviewGeometry.slotBounds());
                        com.tacz.guns.client.renderer.item.AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL.renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(index.getSlotTexture())),light,overlay,1,1,1,1);
                    }return true;
                }
            }
            if(stack.getItem() instanceof IAmmo ammo){
                var index=TimelessAPI.getClientAmmoIndex(ammo.getAmmoId(stack)).orElse(null);
                if(index!=null){var model=index.getAmmoModel();var bounds=PreviewGeometry.bounds(model);if(bounds!=null){pose.scale(-1,-1,1);PreviewGeometry.fit(pose,bounds);model.render(pose,ItemDisplayContext.FIXED,net.minecraft.client.renderer.RenderType.entityCutout(index.getModelTextureLocation()),light,overlay);return true;}}
            }
            // Generated fragments and the original workbench block use baked FIXED models.
            pose.scale(.65f,.65f,.65f);REENTER.set(true);
            try{renderer.render(stack,ItemDisplayContext.FIXED,left,pose,buffers,light,overlay,baked);}finally{REENTER.set(false);}
            return true;
        }finally{pose.popPose();}
    }
}
