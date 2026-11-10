package cn.ssjjparasite2.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** One confirmed shot, two exclusive render routes. No damage or packet changes. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ParasiteFirstPersonShockwave {
    private static final class Flash {
        final ParasiteShockwave.Pulse pulse;
        final ParasiteShockwaveClient.Profile profile;
        final ItemStack source;
        final float born;
        Matrix4d worldPose;
        float length;
        Flash(ParasiteShockwave.Pulse pulse,ParasiteShockwaveClient.Profile profile,ItemStack source,float born) {
            this.pulse=pulse;this.profile=profile;this.source=source;this.born=born;
        }
    }
    private static final ArrayDeque<Flash> flashes=new ArrayDeque<>();
    private static Map<String,EffectGeometry.Data> attachments=Map.of();
    private static ClientLevel level;
    private static ClientLevel renderedLevel;
    private static Vec3 renderedCamera;
    private static Matrix4f worldView,worldProjection;
    private ParasiteFirstPersonShockwave() {}

    public static boolean worldVisible(int shooter,int localPlayer,boolean firstPerson) {
        return shooter!=localPlayer||!firstPerson;
    }
    public static boolean worldVisible(ParasiteShockwave.Pulse pulse) {
        var mc=Minecraft.getInstance();
        return mc.player==null||worldVisible(pulse.shooter(),mc.player.getId(),mc.options.getCameraType().isFirstPerson());
    }
    public static float age(float now,float born) {float elapsed=now-born;return elapsed<0?elapsed+12000:elapsed;}
    private static float now() {
        var mc=Minecraft.getInstance();
        return mc.level==null?0:(mc.level.getGameTime()%240000L+mc.getFrameTime())/20f;
    }
    public static void read(ResourceManager resources) {
        flashes.clear();attachments=Map.of();renderedLevel=null;worldView=null;worldProjection=null;
        try(var stream=resources.open(new ResourceLocation("ssjjparasite2","effects/family.json"));
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var family=new Gson().fromJson(reader,ParasiteEffectsClient.Family.class);
            if(family==null||family.profiles==null||!family.profiles.keySet().equals(ParasiteEffectProfiles.KEYS))
                throw new IllegalArgumentException("Missing muzzle attachment profiles");
            for(String key:ParasiteShockwaveClient.KEYS)EffectGeometry.validate(family.profiles.get(key));
            attachments=Map.copyOf(family.profiles);
        }catch(Exception error){LogUtils.getLogger().error("Cannot load Parasite first-person shockwave attachment",error);}
    }
    /** Called only after the existing authoritative pulse has passed its original checks. */
    public static void accept(ParasiteShockwave.Pulse pulse,ParasiteShockwaveClient.Profile profile) {
        var mc=Minecraft.getInstance();
        if(mc.level!=level){flashes.clear();level=mc.level;}
        if(mc.level==null||mc.player==null||pulse==null||profile==null||pulse.shooter()!=mc.player.getId()||!mc.options.getCameraType().isFirstPerson())return;
        ItemStack source=mc.player.getMainHandItem();
        if(!pulse.profile().equals(ParasiteEffectProfiles.key(source))||!ParasiteCannon.active(source)||!attachments.containsKey(pulse.profile()))return;
        enqueue(pulse,profile,source,now());
    }
    private static void enqueue(ParasiteShockwave.Pulse pulse,ParasiteShockwaveClient.Profile profile,ItemStack source,float clock) {
        for(var flash:flashes)if(flash.pulse.equals(pulse))return;
        while(flashes.size()>=32)flashes.removeFirst();
        flashes.addLast(new Flash(pulse,profile,source,clock));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=level){flashes.clear();level=mc.level;renderedLevel=null;worldView=null;worldProjection=null;}
        if(mc.level==null||mc.player==null){flashes.clear();return;}
        perspective(mc.options.getCameraType().isFirstPerson());
        ItemStack held=mc.player.getMainHandItem();
        expire(held,ParasiteCannon.active(held),ParasiteEffectProfiles.key(held),now());
    }
    private static void expire(ItemStack held,boolean active,String key,float clock) {
        flashes.removeIf(flash->!alive(flash.source,held,active,key,flash.pulse.profile(),age(clock,flash.born),flash.profile.duration));
    }
    private static void perspective(boolean firstPerson) {if(!firstPerson)flashes.clear();}
    public static boolean alive(ItemStack source,ItemStack held,boolean active,String heldProfile,String shotProfile,float elapsed,float duration) {
        return source==held&&active&&shotProfile.equals(heldProfile)&&elapsed>=0&&elapsed<duration;
    }
    /** Capture the real world camera matrices before TaCZ replaces the projection
     * for the first-person gun. Includes world FOV, bob/hurt/zoom transforms.
     */
    @SubscribeEvent public static void worldStage(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();renderedLevel=mc.level;
        perspective(mc.options.getCameraType().isFirstPerson());
        renderedCamera=event.getCamera().getPosition();
        worldView=new Matrix4f(event.getPoseStack().last().pose());
        worldProjection=new Matrix4f(event.getProjectionMatrix());
    }
    /** Wrap the existing root renderer. It still runs unchanged, including its ADS hiding. */
    public static Function<BedrockPart,IFunctionalRenderer> wrapRoot(Function<BedrockPart,IFunctionalRenderer> original,BedrockAnimatedModel model) {
        return node->{var renderer=original.apply(node);return (pose,consumer,context,light,overlay)->{
            deferRoot(model,pose,context);
            renderer.render(pose,consumer,context,light,overlay);
        };};
    }
    /** Native root pose already includes view, camera animation and root animation.
     * Walk only the descendants to the same muzzle joint as the original gun FX.
     * This also works when the normal scoped renderer hides the body children.
     */
    public static PoseStack muzzlePose(BedrockAnimatedModel model,EffectGeometry.Data data,PoseStack rootPose) {
        BedrockPart joint=model.getNode(data.bone),root=model.getRootNode();
        if(joint==null||root==null)return null;
        List<BedrockPart> path=new ArrayList<>();
        for(BedrockPart node=joint;node!=root;node=node.getParent()) {
            if(node==null||path.size()>=128)return null;
            path.add(node);
        }
        PoseStack result=new PoseStack();result.last().pose().set(rootPose.last().pose());result.last().normal().set(rootPose.last().normal());
        for(int i=path.size()-1;i>=0;i--)path.get(i).translateAndRotateAndScale(result);
        result.translate(data.muzzleLocal[0],data.muzzleLocal[1],data.muzzleLocal[2]);
        // Source effect's +Z axis follows this source model's animated +X barrel.
        result.mulPose(new org.joml.Quaternionf().rotationY((float)(Math.PI*.5)));
        return result;
    }
    public static List<EffectGeometry.Batch> sample(ParasiteShockwaveClient.Profile profile,float elapsed,int seed,float length,float radius,PoseStack muzzlePose) {
        Matrix3f inverse=new Matrix3f(muzzlePose.last().pose()).invert();
        Vector3f right=inverse.transform(new Vector3f(1,0,0)).normalize(),up=inverse.transform(new Vector3f(0,1,0)).normalize();
        return ParasiteShockwaveClient.sample(profile,elapsed,seed,length,right,up,true,radius);
    }
    /** Freeze the animated muzzle's world origin once. Later view changes only
     * reproject it; they never drag an emitted wave along the new gun pose.
     */
    public static Vector3f unprojectMuzzle(PoseStack muzzle,Matrix4f weaponProjection,Matrix4f worldProjection) {
        Vector4f clip=weaponProjection.transform(new Vector4f(muzzle.last().pose().getTranslation(new Vector3f()),1));
        Vector4f view=new Matrix4f(worldProjection).invert().transform(clip);
        return new Vector3f(view.x/view.w,view.y/view.w,view.z/view.w);
    }
    public static Matrix4d worldPose(PoseStack muzzle,Vec3 camera,Matrix4f view,Matrix4f weaponProjection,Matrix4f worldProjection,Vec3 endpoint) {
        Vector3f origin=new Matrix4f(view).invert().transformPosition(unprojectMuzzle(muzzle,weaponProjection,worldProjection));
        Vec3 direction=endpoint.subtract(camera.add(origin.x,origin.y,origin.z));
        if(direction.lengthSqr()<1e-12)return new Matrix4d().translation(camera.x+origin.x,camera.y+origin.y,camera.z+origin.z);
        var rotation=new org.joml.Quaternionf().rotationTo(new Vector3f(0,0,1),new Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize());
        return new Matrix4d().translation(camera.x+origin.x,camera.y+origin.y,camera.z+origin.z).rotate(rotation);
    }
    public static PoseStack cameraPose(Matrix4d world,Vec3 camera,Matrix4f worldView) {
        Matrix4f matrix=new Matrix4f(new Matrix4d(worldView).translate(-camera.x,-camera.y,-camera.z).mul(world));
        PoseStack pose=new PoseStack();pose.last().pose().set(matrix);matrix.normal(pose.last().normal());return pose;
    }
    private static List<Flash> visible(ItemStack held,String key,float clock,PoseStack muzzle,Vec3 cameraPosition,Matrix4f view,Matrix4f weaponProjection,Matrix4f projection) {
        List<Flash> visible=new ArrayList<>();
        for(var flash:flashes)if(alive(flash.source,held,true,key,flash.pulse.profile(),age(clock,flash.born),flash.profile.duration)) {
            if(flash.worldPose==null) {
                flash.worldPose=worldPose(muzzle,cameraPosition,view,weaponProjection,projection,flash.pulse.end());
                flash.length=(float)flash.pulse.end().distanceTo(new Vec3(flash.worldPose.m30(),flash.worldPose.m31(),flash.worldPose.m32()));
            }
            visible.add(flash);
        }
        return visible;
    }
    /** Flush before and after changing projection. Restoration survives a draw
     * or trailing buffer flush failure and never relies on deferred submission.
     */
    public static void drawProjected(Matrix4f projection,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,Runnable draw) {
        Matrix4f previousProjection=new Matrix4f(RenderSystem.getProjectionMatrix());var previousSorting=RenderSystem.getVertexSorting();
        buffers.endBatch();
        try {RenderSystem.setProjectionMatrix(projection,previousSorting);draw.run();}
        finally {
            try {buffers.endBatch();}
            finally {RenderSystem.setProjectionMatrix(previousProjection,previousSorting);}
        }
    }
    public static void deferRoot(BedrockAnimatedModel model,PoseStack rootPose,ItemDisplayContext context) {
        if(!context.firstPerson()||flashes.isEmpty()||!(model instanceof BedrockGunModel gun))return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=level||mc.level!=renderedLevel||worldView==null||worldProjection==null||mc.player==null||!mc.options.getCameraType().isFirstPerson())return;
        ItemStack held=mc.player.getMainHandItem();
        if(gun.getCurrentGunItem()!=held)return;
        String key=ParasiteEffectProfiles.key(held);EffectGeometry.Data data=key==null?null:attachments.get(key);
        if(data==null||!ParasiteCannon.active(held))return;
        PoseStack muzzle=muzzlePose(model,data,rootPose);if(muzzle==null)return;
        List<Flash> visible=visible(held,key,now(),muzzle,renderedCamera,worldView,RenderSystem.getProjectionMatrix(),worldProjection);
        if(visible.isEmpty())return;
        model.delegateRender((unused,consumer,display,light,overlay)->{
            // A deferred draw may be abandoned by a view/item/world change.
            var current=Minecraft.getInstance();
            if(current.level!=level||current.level!=renderedLevel||worldView==null||worldProjection==null||current.player==null||current.player.getMainHandItem()!=held||!current.options.getCameraType().isFirstPerson()||!ParasiteCannon.active(held))return;
            var buffers=current.renderBuffers().bufferSource();
            drawProjected(worldProjection,buffers,()->{
                for(var flash:visible) {
                    float elapsed=age(now(),flash.born);
                    if(elapsed<0||elapsed>=flash.profile.duration||flash.length<.001f)continue;
                    PoseStack pose=cameraPose(flash.worldPose,renderedCamera,worldView);
                    ParasiteEffectsClient.draw(sample(flash.profile,elapsed,flash.pulse.seed(),flash.length,(float)flash.pulse.radius(),pose),pose,buffers);
                }
            });
        });
    }
}
