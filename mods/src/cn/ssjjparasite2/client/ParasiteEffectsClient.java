package cn.ssjjparasite2.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ParasiteEffectsClient {
    private static final ResourceLocation ID=new ResourceLocation("ssjjparasite2","parasitic_sniper2");
    private static final ResourceLocation DATA=new ResourceLocation("ssjjparasite2","effects/family.json");
    public static final class Family { public Map<String,EffectGeometry.Data> profiles; }
    private static volatile Map<String,EffectGeometry.Data> data;
    private static volatile Map<String,EffectGeometry.Data> cannonData;
    private static final Map<ItemStack,Float> shots=new WeakHashMap<>();
    private static final ArrayDeque<Impact> impacts=new ArrayDeque<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private record Impact(Vec3 position,Vec3 normal,float start,int seed,String profile) {}
    private ParasiteEffectsClient(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event)throws java.io.IOException {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),new ResourceLocation("ssjjparasite2","parasite_effect"),com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY),EffectRenderType::shader);
        }
        @SubscribeEvent public static void registerParasiteEffects(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ParasiteEffectsClient::read);
        }
    }
    public static void read(ResourceManager resources) {
        data=null;cannonData=null;impacts.clear();shots.clear();
        try(var stream=resources.open(DATA);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Family fresh=new Gson().fromJson(reader,Family.class);if(fresh==null||fresh.profiles==null||!fresh.profiles.keySet().equals(ParasiteEffectProfiles.KEYS))throw new IllegalArgumentException("Missing family effect profile");
            for(var profile:fresh.profiles.values()){EffectGeometry.validate(profile);
            for(EffectGeometry.Effect effect:new EffectGeometry.Effect[]{profile.body,profile.muzzle,profile.impact}) {
                for(EffectGeometry.Layer layer:effect.layers)if(resources.getResource(new ResourceLocation(layer.material.texture)).isEmpty())throw new IllegalArgumentException("Missing FX texture");
                for(EffectGeometry.Emitter emitter:effect.emitters)if(resources.getResource(new ResourceLocation(emitter.material.texture)).isEmpty())throw new IllegalArgumentException("Missing particle texture");
            }}
            data=Map.copyOf(fresh.profiles);LogUtils.getLogger().info("PARASITE original effects loaded for {} tier/skin profiles",data.size());
        }catch(Exception e){LogUtils.getLogger().error("Cannot load original Parasite II effects; effect rendering disabled",e);}
        try(var stream=resources.open(new ResourceLocation("ssjjparasite2","effects/cannon.json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            Family fresh=new Gson().fromJson(reader,Family.class);
            if(fresh==null||fresh.profiles==null||!fresh.profiles.keySet().equals(java.util.Set.of("parasitic_sniper2","parasitic_sniper2_frost","parasitic_sniper2_ship")))throw new IllegalArgumentException("Invalid cannon effect profiles");
            for(var profile:fresh.profiles.values()){EffectGeometry.validate(profile);
                for(var layer:profile.body.layers)if(resources.getResource(new ResourceLocation(layer.material.texture)).isEmpty())throw new IllegalArgumentException("Missing high-energy texture");
                for(var emitter:profile.body.emitters)if(resources.getResource(new ResourceLocation(emitter.material.texture)).isEmpty())throw new IllegalArgumentException("Missing high-energy particle texture");
            }
            cannonData=Map.copyOf(fresh.profiles);
        }catch(Exception e){LogUtils.getLogger().error("Cannot load Parasite high-energy body effects",e);}
    }
    public static boolean matches(ItemStack stack) {
        return ParasiteEffectProfiles.key(stack)!=null;
    }
    public static boolean baseSkin(ItemStack stack) {
        IGun gun=IGun.getIGunOrNull(stack);if(gun==null)return false;
        ResourceLocation display=gun.getGunDisplayId(stack);return display==null||display.equals(com.tacz.guns.api.DefaultAssets.DEFAULT_GUN_DISPLAY_ID)||display.equals(new ResourceLocation("ssjjparasite2","parasitic_sniper2_display"));
    }
    private static float now() {var mc=Minecraft.getInstance();return mc.level==null?0:(mc.level.getGameTime()%240000L+mc.getFrameTime())/20f;}
    private static float age(float now,float then){float a=now-then;return a<0?a+12000:a;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fire(GunFireEvent e) {
        if(!e.getLogicalSide().isClient()||!matches(e.getGunItemStack()))return;
        var mc=Minecraft.getInstance();if(mc.level==null||e.getShooter()==null)return;
        // Fire is the accepted shot notification, unlike cancellable Shoot.
        // TaCZ may pass a serialized stack copy: bind to the actual held stack.
        ItemStack held=e.getShooter().getMainHandItem();if(matches(held))shots.put(held,now());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){lastLevel=mc.level;impacts.clear();shots.clear();}
        if(mc.level==null)return;float clock=now();shots.entrySet().removeIf(p->age(clock,p.getValue())>2);
        impacts.removeIf(p->age(clock,p.start)>3);
    }
    /** Called at the actual animated joint. TaCZ flushes its body before delegates. */
    public static void defer(BedrockAnimatedModel model,String bone,PoseStack pose,ItemDisplayContext context) {
        var profiles=data;if(profiles==null||!(model instanceof BedrockGunModel gun))return;
        ItemStack stack=gun.getCurrentGunItem();String key=ParasiteEffectProfiles.key(stack);EffectGeometry.Data current=key==null?null:profiles.get(key);
        if(current==null||!current.bone.equals(bone)||context==ItemDisplayContext.GUI)return;
        var highProfiles=cannonData;EffectGeometry.Data high=ParasiteCannon.active(stack)&&highProfiles!=null?highProfiles.get(key):null;
        boolean body=current.body.layers.length>0||current.body.emitters.length>0;
        Float shot=shots.get(stack);float clock=now();float elapsed=shot==null?-1:age(clock,shot);
        boolean muzzle=elapsed>=0&&elapsed<EffectGeometry.lifetime(current.muzzle)
            &&(context.firstPerson()||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        if(!body&&!muzzle)return;
        Matrix4f captured=new Matrix4f(pose.last().pose());Matrix3f normal=new Matrix3f(pose.last().normal());
        model.delegateRender((unused,consumer,display,light,overlay)->{
            PoseStack p=new PoseStack();p.last().pose().set(captured);p.last().normal().set(normal);
            Matrix3f inverse=new Matrix3f(captured).invert();Vector3f right=inverse.transform(new Vector3f(1,0,0)).normalize(),up=inverse.transform(new Vector3f(0,1,0)).normalize();
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            if(body)draw(EffectGeometry.sample(current.body,clock,42,true,right,up),p,buffers);
            if(high!=null)draw(EffectGeometry.sample(high.body,clock,42,true,right,up),p,buffers);
            if(muzzle){p.pushPose();p.translate(current.muzzleLocal[0],current.muzzleLocal[1],current.muzzleLocal[2]);draw(EffectGeometry.sample(current.muzzle,elapsed,Float.floatToIntBits(shot),false,right,up),p,buffers);p.popPose();}
        });
    }
    /** Server-authoritative hit location; never guessed with the view ray. */
    public static void receive(ParasiteEffectsNetwork.Hit hit) {
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||data==null||!hit.valid())return;
        Vec3 pos=new Vec3(hit.x(),hit.y(),hit.z());if(mc.player.position().distanceToSqr(pos)>64*64)return;
        var profile=data.get(hit.profile());if(profile==null||EffectGeometry.lifetime(profile.impact)<=0)return;
        while(impacts.size()>=48)impacts.removeFirst();impacts.addLast(new Impact(pos,new Vec3(hit.nx(),hit.ny(),hit.nz()),now(),hit.seed(),hit.profile()));
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e) {
        var current=data;if(current==null||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||impacts.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;float clock=now();Vec3 camera=e.getCamera().getPosition();
        impacts.removeIf(i->!current.containsKey(i.profile)||age(clock,i.start)>=Math.min(3,EffectGeometry.lifetime(current.get(i.profile).impact)));
        var buffers=mc.renderBuffers().bufferSource();
        for(Impact i:impacts){if(camera.distanceToSqr(i.position)>64*64)continue;PoseStack p=e.getPoseStack();p.pushPose();p.translate(i.position.x-camera.x,i.position.y-camera.y,i.position.z-camera.z);
            // Impact billboards use the actual camera basis; the mesh ring keeps
            // the source prefab's original orientation.
            Vector3f right=e.getCamera().getLeftVector().negate(new Vector3f()),up=new Vector3f(e.getCamera().getUpVector());
            draw(EffectGeometry.sample(current.get(i.profile).impact,age(clock,i.start),i.seed,false,right,up),p,buffers);p.popPose();
        }
    }
    public static void draw(List<EffectGeometry.Batch> batches,PoseStack pose,MultiBufferSource.BufferSource buffers) {
        java.util.Map<String,List<EffectGeometry.Batch>> groups=new java.util.LinkedHashMap<>();
        for(var b:batches)groups.computeIfAbsent(b.material().texture,k->new java.util.ArrayList<>()).add(b);
        for(var group:groups.entrySet()){RenderType type=EffectRenderType.of(new ResourceLocation(group.getKey()));VertexConsumer out=buffers.getBuffer(type);for(var b:group.getValue())emit(b,pose,out);buffers.endBatch(type);}
    }
    public static void emit(EffectGeometry.Batch batch,PoseStack pose,VertexConsumer out) {
        EffectGeometry.Mesh mesh=batch.mesh();float[] color=batch.color(),st=batch.uvST();
        for(int[] f:mesh.triangles)for(int k=0;k<4;k++){
            int i=f[Math.min(k,2)];float[] p=mesh.positions[i],uv=mesh.uv[i];
            out.vertex(pose.last().pose(),p[0],p[1],p[2]).color(Math.min(1,color[0]),Math.min(1,color[1]),Math.min(1,color[2]),Math.min(1,color[3]))
                .uv(uv[0]*st[0]+st[2],uv[1]*st[1]-st[3]).overlayCoords(0).uv2(15728880).normal(pose.last().normal(),0,1,0).endVertex();
        }
    }
}
