package cn.ssjjares.client;

import cn.ssjjares.AresAccessories;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Small baked accessories follow existing head/chest bones. No viewhand hooks. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AresAccessoryVisuals {
    static final ModelPart ROOT=AresAccessoryModels.bake();
    static final ModelPart[] PIECES={null,ROOT.getChild("mask"),ROOT.getChild("cannons"),ROOT.getChild("wings")};
    static final ResourceLocation[] TEXTURES={new ResourceLocation("ssjj_ares","textures/models/accessory/ares_accessories_base.png"),new ResourceLocation("ssjj_ares","textures/models/accessory/ares_accessories_absolute.png")};
    private AresAccessoryVisuals(){}
    public static boolean ysmPresent(){var list=ModList.get();return list!=null&&list.isLoaded("yes_steve_model");}
    /** Curios-only accessories stay pixel-sized on an untransformed player. */
    public static boolean vanillaUsesOriginalMesh(boolean ysmInstalled,boolean nativeAccessory){return ysmInstalled&&nativeAccessory;}
    public static EquipmentSlot nativeSlot(int kind){return kind==1?EquipmentSlot.HEAD:kind==2?EquipmentSlot.CHEST:EquipmentSlot.LEGS;}
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->AresAccessoryMesh.reload());}
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
        for(String skin:event.getSkins()){
            PlayerRenderer player=event.getSkin(skin);
            if(player!=null)player.addLayer(new VanillaLayer<>(player));
        }
        ArmorStandRenderer stand=event.getRenderer(EntityType.ARMOR_STAND);
        if(stand!=null)stand.addLayer(new VanillaLayer<>(stand));
    }
    static void draw(PoseStack pose,MultiBufferSource buffers,int light,int kind,int tier){
        PIECES[kind].render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[tier-1])),light,OverlayTexture.NO_OVERLAY);
    }
    public static final class VanillaLayer<T extends LivingEntity,M extends HumanoidModel<T>> extends RenderLayer<T,M> {
        public VanillaLayer(LivingEntityRenderer<T,M> renderer){super(renderer);}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float walk,float speed,float partial,float age,float yaw,float pitch){
            if(entity instanceof AbstractClientPlayer player&&AresWeightedHands.modelTier(player)>0)return;
            for(int kind=1;kind<=3;kind++){
                ItemStack item=AresAccessories.equipped(entity,kind);int tier=AresAccessories.tier(item);
                if(tier==0)continue;
                pose.pushPose();
                try{
                    (kind==1?getParentModel().head:getParentModel().body).translateAndRotate(pose);
                    boolean nativeAccessory=AresAccessories.kind(entity.getItemBySlot(nativeSlot(kind)))==kind;
                    var mesh=vanillaUsesOriginalMesh(ysmPresent(),nativeAccessory)?AresAccessoryMesh.get(kind,tier):null;
                    if(mesh!=null){mesh.vanillaMount(pose);mesh.render(pose,buffers,light,kind,tier);}
                    else draw(pose,buffers,light,kind,tier);
                }
                finally{pose.popPose();}
            }
        }
    }
    /** Called once by our source renderer's constructor, after its held item layer. */
    public static void addSourceLayer(AresSourceBodyVisuals.SourceRenderer renderer){renderer.addLayer(new SourceLayer(renderer));}
    public static final class SourceLayer extends RenderLayer<AbstractClientPlayer,AresSourceBodyVisuals.SourceModel> {
        final SourceFrames frames;final Matrix4f matrix=new Matrix4f();
        public SourceLayer(AresSourceBodyVisuals.SourceRenderer renderer){super(renderer);frames=new SourceFrames(renderer.getModel().mesh);}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer entity,float walk,float speed,float partial,float age,float yaw,float pitch){
            if(entity.isSpectator())return;
            for(int kind=1;kind<=3;kind++){
                // The layer belongs to the replacement character renderer;
                // vanilla player layers are cancelled for this same frame.
                ItemStack item=AresAccessories.equipped(entity,kind);int tier=AresAccessories.tier(item);
                if(tier==0)continue;
                pose.pushPose();
                try{
                    AresSourceBodyVisuals.SourceModel.sourceSpace(pose);
                    var mesh=AresAccessoryMesh.get(kind,tier);
                    if(mesh!=null)frames.delta(getParentModel(),kind,matrix);else frames.frame(getParentModel(),kind,matrix);
                    pose.mulPoseMatrix(matrix);
                    pose.last().normal().set(pose.last().pose()).invert().transpose();
                    if(mesh!=null)mesh.render(pose,buffers,light,kind,tier);else draw(pose,buffers,light,kind,tier);
                }finally{pose.popPose();}
            }
        }
    }
    /** Rigid delta from a real idle reference, never a guessed native bone buffer. */
    public static final class SourceFrames {
        final Matrix4f[] referenceInverse={new Matrix4f(),new Matrix4f()};
        final float[] origins=new float[6];
        public SourceFrames(AresWeightedBodyMesh mesh){
            var reference=mesh.scratch();mesh.pose(reference,"idle",0,null,0,1,null,0,0);
            var head=new Matrix4f();var neck=new Matrix4f();var left=new Matrix4f();var right=new Matrix4f();
            joint(mesh,reference,"Bip01_Head",head);joint(mesh,reference,"Bip01_Neck",neck);
            joint(mesh,reference,"Bip01_L_Clavicle",left);joint(mesh,reference,"Bip01_R_Clavicle",right);
            origins[0]=head.m30();origins[1]=neck.m31();origins[2]=head.m32();
            origins[3]=(left.m30()+right.m30())*.5f;origins[4]=(left.m31()+right.m31())*.5f;origins[5]=(left.m32()+right.m32())*.5f;
            referenceInverse[0].set(head).invert();joint(mesh,reference,"Bip01_Spine2",referenceInverse[1]);referenceInverse[1].invert();
        }
        static Matrix4f joint(AresWeightedBodyMesh mesh,AresWeightedBodyMesh.Scratch s,String name,Matrix4f out){
            mesh.jointPose(s,name,out);float x=out.m30(),y=out.m31(),z=out.m32();out.normalize3x3();return out.setTranslation(x,y,z);
        }
        public Matrix4f frame(AresSourceBodyVisuals.SourceModel model,int kind,Matrix4f out){
            int index=kind==1?0:1,o=index*3;
            delta(model,kind,out);
            // Source world is X-right/Y-up/Z-back. Baked vanilla box coordinates
            // use the inverted X/Y frame; retain the ordinary pixel model size.
            float scale=sourceScale(kind);
            return out.translate(origins[o],origins[o+1],origins[o+2]).scale(-scale,-scale,scale);
        }
        public Matrix4f delta(AresSourceBodyVisuals.SourceModel model,int kind,Matrix4f out){int index=kind==1?0:1;return model.attachment(index==0?"Bip01_Head":"Bip01_Spine2",out).mul(referenceInverse[index]);}
        public static float sourceScale(int kind){return kind==1?.65f:kind==2?.82f:.8f;}
    }
}
