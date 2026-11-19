package cn.ssjjares.client;

import cn.ssjjdark.DarkSkins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Original source FK owns both the skin and held items. No native bone buffer is read. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class AresSourceBodyVisuals {
    private static final ResourceLocation BODY=new ResourceLocation("ssjj_ares","models/ares_weighted_body.bin"), MOTION=new ResourceLocation("ssjj_ares","models/ares_weighted_motion.bin");
    private static final ResourceLocation[] TEXTURE={new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body1.png"),new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body2.png")};
    private static SourceRenderer renderer;private static VanillaFallback fallback;private static boolean rendering,warned,failed,fallbackRendering;
    private static volatile String lastAction="unrendered";
    private static volatile float minX,minY,minZ,maxX,maxY,maxZ;
    public static String debugSnapshot(){return lastAction+" ["+minX+","+minY+","+minZ+"] ["+maxX+","+maxY+","+maxZ+"]";}
    public static boolean isFallbackRendering(){return fallbackRendering;}
    /** Dark Crystal is a melee weapon even though its TaCZ carrier type is pistol. */
    public static String darkMeleeOverlay(ResourceLocation id,boolean crouching){return DarkSkins.family(id)?"darkcrystal_hold"+(crouching?"_crouch":""):null;}
    private AresSourceBodyVisuals(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager->{renderer=null;fallback=null;warned=failed=false;});}
    }
    private static EntityRendererProvider.Context context(){var mc=Minecraft.getInstance();var dispatcher=mc.getEntityRenderDispatcher();return new EntityRendererProvider.Context(dispatcher,mc.getItemRenderer(),mc.getBlockRenderer(),dispatcher.getItemInHandRenderer(),mc.getResourceManager(),mc.getEntityModels(),mc.font);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void player(RenderPlayerEvent.Pre event){
        if(rendering||!(event.getEntity() instanceof AbstractClientPlayer player)||AresWeightedHands.modelTier(player)==0)return;
        var mc=Minecraft.getInstance();
        if(player.isInvisible()||player==mc.player&&AresWeightedHands.selfModelHidden()||player!=mc.player&&Visibility.hideOthers())return;
        event.setCanceled(true);rendering=true;
        var stack=event.getPoseStack();
        try{
            if(failed){drawFallback(event,player,stack);return;}
            if(renderer==null){var manager=mc.getResourceManager();try(var in=manager.getResource(BODY).orElseThrow().open();var motion=manager.getResource(MOTION).orElseThrow().open()){renderer=new SourceRenderer(context(),AresWeightedBodyMesh.read(in,motion));}}
            renderer.render(player,Mth.rotLerp(event.getPartialTick(),player.yBodyRotO,player.yBodyRot),event.getPartialTick(),copy(stack),event.getMultiBufferSource(),event.getPackedLight());
        }catch(Exception error){
            failed=true;
            if(!warned){warned=true;LogUtils.getLogger().error("Ares source body failed; using normal player armor",error);}
            drawFallback(event,player,stack);
        }finally{rendering=false;}
    }
    private static PoseStack copy(PoseStack source){var target=new PoseStack();target.last().pose().set(source.last().pose());target.last().normal().set(source.last().normal());return target;}
    private static void drawFallback(RenderPlayerEvent.Pre event,AbstractClientPlayer player,PoseStack source){if(fallback==null)fallback=new VanillaFallback(context());fallbackRendering=true;try{fallback.render(player,Mth.rotLerp(event.getPartialTick(),player.yBodyRotO,player.yBodyRot),event.getPartialTick(),copy(source),event.getMultiBufferSource(),event.getPackedLight());}finally{fallbackRendering=false;}}
    private static final class Visibility {
        private static ForgeConfigSpec.BooleanValue hide;
        static boolean hideOthers(){try{if(hide==null)hide=(ForgeConfigSpec.BooleanValue)Class.forName("com.elfmcys.yesstevemodel.OoOoOOoOOoOo0OooOOoO00Oo").getField("oOOOo0OOO0ooooo0O00OO0o0").get(null);return hide.get();}catch(ReflectiveOperationException|LinkageError error){return false;}}
    }
    /** The actual vanilla living renderer handles body yaw, riding, hurt/death, sleeping and names. */
    public static final class SourceRenderer extends LivingEntityRenderer<AbstractClientPlayer,SourceModel> {
        SourceRenderer(EntityRendererProvider.Context ctx,AresWeightedBodyMesh mesh){super(ctx,new SourceModel(mesh),.5f);addLayer(new HeldItems(this,ctx.getItemInHandRenderer()));AresAccessoryVisuals.addSourceLayer(this);}
        @Override public ResourceLocation getTextureLocation(AbstractClientPlayer player){return TEXTURE[AresWeightedHands.modelTier(player)==2?1:0];}
        @Override protected void setupRotations(AbstractClientPlayer p,PoseStack stack,float age,float yaw,float partial){
            super.setupRotations(p,stack,age,yaw,partial);
            if(p.isFallFlying()){
                float t=p.getFallFlyingTicks()+partial,weight=Mth.clamp(t*t/100,0,1);
                if(!p.isAutoSpinAttack())stack.mulPose(Axis.XP.rotationDegrees(weight*(-90-p.getXRot())));
                var look=p.getViewVector(partial);var speed=p.getDeltaMovementLerped(partial);double a=speed.horizontalDistanceSqr(),b=look.horizontalDistanceSqr();
                if(a>0&&b>0){double dot=(speed.x*look.x+speed.z*look.z)/Math.sqrt(a*b),cross=speed.x*look.z-speed.z*look.x;stack.mulPose(Axis.YP.rotation((float)(Math.signum(cross)*Math.acos(Mth.clamp(dot,-1,1)))));}
            }else if(p.getSwimAmount(partial)>0){float angle=p.isInWater()?-90-p.getXRot():-90;stack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(p.getSwimAmount(partial),0,angle)));if(p.isVisuallySwimming())stack.translate(0,-1,.3);}
        }
    }
    /** Original local translations are fixed. Only source rotations are blended. */
    public static final class SourceModel extends EntityModel<AbstractClientPlayer> {
        final AresWeightedBodyMesh mesh;final AresWeightedBodyMesh.Scratch scratch;final Map<AbstractClientPlayer,State> states=new WeakHashMap<>();
        final Quaternionf qa=new Quaternionf(),qb=new Quaternionf(),parentRot=new Quaternionf(),delta=new Quaternionf();
        final Matrix4f attachment=new Matrix4f();final int head,upper;final int[] headChain,upperChain;final float[] neutral;
        State current;AbstractClientPlayer entity;
        SourceModel(AresWeightedBodyMesh mesh){
            super(RenderType::entityCutoutNoCull);this.mesh=mesh;scratch=mesh.scratch();head=find("Bip01_Head");upper=find("Bip01_Spine1");headChain=chain(head);upperChain=chain(upper);
            neutral=new float[mesh.bones*7];mesh.sample("idle",0,neutral,scratch);
            // The source idle has an asymmetric combat stance. Use connected bind
            // leg rotations for a relaxed, parallel airborne silhouette instead.
            var bind=new Matrix4f();var local=new Matrix4f();
            for(int i=0;i<mesh.bones;i++)if(lower(i)){
                bind.set(mesh.inverse[i]).invert();if(mesh.parent[i]>=0)local.set(mesh.inverse[mesh.parent[i]]).mul(bind);else local.set(bind);
                local.getNormalizedRotation(qa).normalize();int k=i*7;neutral[k+3]=qa.x;neutral[k+4]=qa.y;neutral[k+5]=qa.z;neutral[k+6]=qa.w;
            }
        }
        boolean lower(int i){String n=mesh.names[i];return n.contains("_Thigh")||n.contains("_Calf")||n.contains("_Foot")||n.contains("_Toe");}
        int find(String name){for(int i=0;i<mesh.bones;i++)if(mesh.names[i].equals(name))return i;throw new IllegalArgumentException("Missing source Ares bone "+name);}
        int[] chain(int index){int count=0;for(int p=mesh.parent[index];p>=0;p=mesh.parent[p])count++;int[] a=new int[count];for(int p=mesh.parent[index],i=count-1;p>=0;p=mesh.parent[p])a[i--]=p;return a;}
        @Override public void prepareMobModel(AbstractClientPlayer player,float walk,float speed,float partial){entity=player;current=states.computeIfAbsent(player,p->new State());current.update(player,(player.tickCount+partial)/20f,mesh);}
        @Override public void setupAnim(AbstractClientPlayer player,float walk,float speed,float age,float yaw,float pitch){
            State s=current;sampleBase(s.action,s.time,scratch.local);
            if(s.from!=null&&s.blend<1){sampleBase(s.from,s.fromTime,scratch.previous);mix(scratch.previous,scratch.local,s.blend,false);System.arraycopy(scratch.previous,0,scratch.local,0,scratch.local.length);}
            if(s.overlay!=null){mesh.sample(s.overlay,s.overlayTime,scratch.overlay,scratch);mix(scratch.local,scratch.overlay,s.overlayWeight,true);}
            AresIdlePosture.apply(this);
            if(DarkAresLiberation.normal(this)){mesh.skin(scratch);guard("darkcrystal_liberation");return;}
            float upperYaw=s.gun?Mth.clamp(yaw,-45,45):0,upperPitch=s.gun?Mth.clamp(pitch,-60,60):0;
            if(s.gun)look(upper,upperChain,upperYaw,upperPitch);
            look(head,headChain,Mth.clamp(yaw-upperYaw,-65,65),Mth.clamp(pitch-upperPitch,-70,70));
            mesh.skin(scratch);
            guard(s.action+(s.overlay==null?"":"+"+s.overlay));
        }
        void sampleBase(String action,float time,float[] out){
            boolean air=action.equals("air")||action.equals("flight")||action.equals("glide")||action.equals("swim")||action.equals("ride");
            if(air){
                System.arraycopy(neutral,0,out,0,neutral.length);
                // Fixed local rotations, never driven by vertical-velocity sign or
                // walkAnimation. Elytra/water are already oriented by the renderer.
                float knee=action.equals("ride")?-72:action.equals("air")?-15:action.equals("flight")?-8:0;
                for(int i=0;i<mesh.bones;i++){
                    String n=mesh.names[i];float angle=n.contains("_Calf")?knee:n.contains("_Thigh")&&action.equals("ride")?78:0;
                    if(angle==0)continue;int k=i*7;qa.set(out[k+3],out[k+4],out[k+5],out[k+6]).rotateZ(angle*Mth.DEG_TO_RAD).normalize();out[k+3]=qa.x;out[k+4]=qa.y;out[k+5]=qa.z;out[k+6]=qa.w;
                }
                return;
            }
            boolean run=action.equals("sprint"),landing=action.equals("soft_land");mesh.sample(run?"run":landing?"land":action,time,out,scratch);
            if(!run&&!landing)return;
            // The old sprint_loop raised a boot almost a block. A single-cycle
            // run with restrained pelvis/torso motion keeps a compact human gait.
            for(int i=0;i<mesh.bones;i++){
                int k=i*7;float w=landing?.38f:i==mesh.root?.35f:lower(i)?.80f:mesh.upper[i]?.66f:.55f;
                for(int j=0;j<3;j++)out[k+j]=neutral[k+j]+(out[k+j]-neutral[k+j])*(i==mesh.root?(landing?.38f:.25f):1);
                qa.set(neutral[k+3],neutral[k+4],neutral[k+5],neutral[k+6]);qb.set(out[k+3],out[k+4],out[k+5],out[k+6]);qa.slerp(qb,w).normalize();out[k+3]=qa.x;out[k+4]=qa.y;out[k+5]=qa.z;out[k+6]=qa.w;
            }
        }
        void guard(String action){float lx=Float.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Float.NEGATIVE_INFINITY,hy=hx,hz=hx;for(int i=0;i<scratch.position.length;i+=3){float x=scratch.position[i],y=scratch.position[i+1],z=scratch.position[i+2];if(!Float.isFinite(x+y+z)||Math.abs(x)>8||Math.abs(y)>8||Math.abs(z)>8)throw new IllegalStateException("Ares source pose bounds: "+action);lx=Math.min(lx,x);ly=Math.min(ly,y);lz=Math.min(lz,z);hx=Math.max(hx,x);hy=Math.max(hy,y);hz=Math.max(hz,z);}for(float n:scratch.normal)if(!Float.isFinite(n))throw new IllegalStateException("Ares source pose normal: "+action);minX=lx;minY=ly;minZ=lz;maxX=hx;maxY=hy;maxZ=hz;lastAction=action;}
        void mix(float[] a,float[] b,float weight,boolean onlyUpper){for(int i=0;i<mesh.bones;i++){if(onlyUpper&&!mesh.upper[i])continue;int k=i*7;for(int j=0;j<3;j++)a[k+j]+=(b[k+j]-a[k+j])*weight;qa.set(a[k+3],a[k+4],a[k+5],a[k+6]);qb.set(b[k+3],b[k+4],b[k+5],b[k+6]);qa.slerp(qb,weight).normalize();a[k+3]=qa.x;a[k+4]=qa.y;a[k+5]=qa.z;a[k+6]=qa.w;}}
        void look(int index,int[] chain,float yaw,float pitch){
            if(Math.abs(yaw)+Math.abs(pitch)<1e-5)return;parentRot.identity();for(int bone:chain){int k=bone*7;parentRot.mul(qb.set(scratch.local[k+3],scratch.local[k+4],scratch.local[k+5],scratch.local[k+6]));}
            // model +Y yaw axis = source +Y; model +X pitch axis = source +Z.
            delta.rotationY(-yaw*Mth.DEG_TO_RAD).rotateZ(-pitch*Mth.DEG_TO_RAD);qa.set(parentRot).conjugate().mul(delta).mul(parentRot);int k=index*7;qa.mul(qb.set(scratch.local[k+3],scratch.local[k+4],scratch.local[k+5],scratch.local[k+6])).normalize();scratch.local[k+3]=qa.x;scratch.local[k+4]=qa.y;scratch.local[k+5]=qa.z;scratch.local[k+6]=qa.w;
        }
        /** Cancel vanilla's fixed model-space S(-1,-1,1)*T(0,-1.501,0). */
        public static void sourceSpace(PoseStack stack){stack.translate(0,1.501,0);stack.scale(-1,-1,1);}
        @Override public void renderToBuffer(PoseStack stack,VertexConsumer out,int light,int overlay,float r,float g,float b,float alpha){stack.pushPose();try{sourceSpace(stack);mesh.draw(stack,out,light,overlay,scratch);}finally{stack.popPose();}}
        public Matrix4f attachment(String name,Matrix4f out){mesh.jointPose(scratch,name,out);float x=out.m30(),y=out.m31(),z=out.m32();out.normalize3x3();out.setTranslation(x,y,z);return out;}
    }
    public static final class HeldItems extends RenderLayer<AbstractClientPlayer,SourceModel> {
        final ItemInHandRenderer items;final Matrix4f matrix=new Matrix4f();
        HeldItems(SourceRenderer renderer,ItemInHandRenderer items){super(renderer);this.items=items;}
        @Override public void render(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,float walk,float speed,float partial,float age,float yaw,float pitch){
            if(player.isSpectator())return;boolean right=player.getMainArm()==HumanoidArm.RIGHT;draw(stack,buffers,light,player,right?player.getMainHandItem():player.getOffhandItem(),false);draw(stack,buffers,light,player,right?player.getOffhandItem():player.getMainHandItem(),true);
        }
        void draw(PoseStack stack,MultiBufferSource buffers,int light,AbstractClientPlayer player,ItemStack item,boolean left){
            if(item.isEmpty())return;SourceModel model=getParentModel();stack.pushPose();try{
                SourceModel.sourceSpace(stack);model.attachment(left?"WeaponAnchor2":"WeaponAnchor",matrix);stack.mulPoseMatrix(matrix);stack.last().normal().set(stack.last().pose()).invert().transpose();
                // Source attachment +X is barrel-forward and +Z is up. The
                // hand-item input frame uses -Z forward/+Y up: map these axes.
                stack.mulPose(Axis.ZP.rotationDegrees(-90));stack.mulPose(Axis.XP.rotationDegrees(90));
                items.renderItem(player,item,left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,left,stack,buffers,light);
            }finally{stack.popPose();}
        }
    }
    public static final class State {
        String action="idle",from,overlay;float time,fromTime,blend=1,overlayTime,overlayWeight,started,changed,lastTime=Float.NaN,shootStart=-100,reloadStart,reloadDuration,lastReload,gaitPhase,airStarted,landUntil;long lastShoot;boolean gun,reloading,naturalAir;
        /** Production entity snapshot and offline selector QA use this same path. */
        void updateMovement(float now,boolean grounded,boolean flying,boolean gliding,boolean water,boolean riding,boolean crouching,boolean sprinting,boolean moving,boolean backwards,float horizontalSpeed,AresWeightedBodyMesh mesh){
            float dt=Float.isNaN(lastTime)?.05f:Mth.clamp(now-lastTime,0,.1f);
            if(!Float.isNaN(lastTime)&&now<lastTime){started=changed=now;from=null;action="idle";naturalAir=false;landUntil=0;gaitPhase=0;}
            boolean uncontrolled=!grounded&&!flying&&!gliding&&!water&&!riding;
            if(uncontrolled&&!naturalAir)airStarted=now;
            if(grounded&&naturalAir&&now-airStarted>.12f)landUntil=now+.25f;
            if(flying||gliding||water||riding)landUntil=0;
            naturalAir=uncontrolled;
            String next=riding?"ride":gliding?"glide":flying?"flight":water?"swim":!grounded?"air":now<landUntil?"soft_land":crouching?(moving?(backwards?"sneaking_back":"sneaking"):"sneak"):sprinting&&moving&&!backwards?"sprint":moving?(backwards?"walk_back":"walk"):"idle";
            if(moving&&grounded&&!water&&!riding){float rate=Mth.clamp(horizontalSpeed*20/(next.equals("sprint")?3.1f:crouching?1.65f:2.6f),crouching?.3f:.6f,next.equals("sprint")?2f:1.5f);gaitPhase=(gaitPhase+dt*rate)%1024;}
            if(!next.equals(action)){from=action;fromTime=time;action=next;started=changed=now;}
            float t=Mth.clamp((now-changed)/.2f,0,1);blend=t*t*(3-2*t);
            time=action.equals("sprint")?gaitPhase*mesh.clip("run").duration():action.equals("walk")||action.equals("walk_back")||action.equals("sneaking")||action.equals("sneaking_back")?gaitPhase*mesh.clip(action).duration():action.equals("air")||action.equals("flight")||action.equals("glide")||action.equals("swim")||action.equals("ride")?0:action.equals("soft_land")?(now-started)/.25f*mesh.clip("land").duration():now-started;
            lastTime=now;
        }
        void update(AbstractClientPlayer p,float now,AresWeightedBodyMesh mesh){
            float dt=Float.isNaN(lastTime)?.05f:Mth.clamp(now-lastTime,0,.1f);
            boolean grounded=p.onGround(),moving=p.walkAnimation.speed()> .025f;
            boolean backwards=p.getDeltaMovement().x*(-Math.sin(p.yBodyRot*Mth.DEG_TO_RAD))+p.getDeltaMovement().z*Math.cos(p.yBodyRot*Mth.DEG_TO_RAD)<-.005;
            updateMovement(now,grounded,p.getAbilities().flying,p.isFallFlying(),p.isVisuallySwimming()||!grounded&&p.isInWater(),p.isPassenger(),p.isCrouching(),p.isSprinting(),moving,backwards,(float)Math.sqrt(p.getDeltaMovement().horizontalDistanceSqr()),mesh);
            IGun heldGun=IGun.getIGunOrNull(p.getMainHandItem());gun=heldGun!=null;
            if(gun){
                ResourceLocation id=heldGun.getGunId(p.getMainHandItem());
                if(darkMeleeOverlay(id,p.isCrouching())!=null){
                    updateGunOverlay(id,p.isCrouching(),null,false,0,0,now,dt,mesh);
                }else{
                    var op=IGunOperator.fromLivingEntity(p);var reload=op.getSynReloadState();boolean active=reload!=null&&reload.getStateType().isReloading();
                    String kind=TimelessAPI.getCommonGunIndex(id).map(index->"pistol".equals(index.getType())).orElse(false)?"pistol":"rifle";
                    updateGunOverlay(id,p.isCrouching(),kind,active,active?reload.getCountDown()/1000f:0,op.getSynShootCoolDown(),now,dt,mesh);
                }
            }else{reloading=false;overlayWeight=Math.max(0,overlayWeight-dt*10);if(overlayWeight==0)overlay=null;}
            lastTime=now;
        }
        /** Shared by the rendered entity path and the source-FK regression audit. */
        void updateGunOverlay(ResourceLocation id,boolean crouching,String kind,boolean active,float remain,long cool,float now,float dt,AresWeightedBodyMesh mesh){
            String melee=darkMeleeOverlay(id,crouching);
            if(melee!=null){
                overlay=melee;overlayTime=0;reloading=false;lastShoot=cool;shootStart=-100;reloadDuration=lastReload=0;
            }else{
                if(cool>lastShoot&&cool>0)shootStart=now;lastShoot=cool;String suffix=crouching?"_crouch":"";
                if(active){if(!reloading){reloadStart=now;reloadDuration=Math.max(.2f,remain);}else if(remain>lastReload+.15f)reloadDuration=Math.max(reloadDuration,now-reloadStart+remain);lastReload=remain;overlay=kind+"_reload"+suffix;overlayTime=Mth.clamp((now-reloadStart)/reloadDuration,0,1)*mesh.clip(overlay).duration();}
                else if(now-shootStart<.1667f){overlay=kind+"_shoot"+suffix;overlayTime=now-shootStart;}
                else{overlay=kind+"_hold"+suffix;overlayTime=0;}
                reloading=active;
            }
            overlayWeight=Math.min(1,overlayWeight+dt*12);
        }
    }
    /** The last-resort failure path still has ordinary player skin and armor. */
    private static final class VanillaFallback extends LivingEntityRenderer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        VanillaFallback(EntityRendererProvider.Context ctx){super(ctx,new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER),false),.5f);addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),ctx.getModelManager()));}
        @Override public ResourceLocation getTextureLocation(AbstractClientPlayer player){return player.getSkinTextureLocation();}
    }
}
