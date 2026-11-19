package cn.ssjjdark.client;

import cn.ssjjdark.DarkSkins;
import cn.ssjjdimension.client.DimensionEffectRenderType;
import cn.ssjjdimension.client.DimensionParticles;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Scoped original skin effects, rendered inside the corrected source WeaponAnchor callback. */
public final class DarkSkinEffects {
    public static final class Index {public int schema;public Map<String,Profile> profiles;}
    public static final class Profile {
        public String body;
        public Map<String,Event> events;
        public transient DarkSkinParticles.Data bodyData;
    }
    public static final class Event {
        public String effect;
        public float delay;
        public transient DarkSkinParticles.Data data;
    }
    private record Material(String texture,String render,boolean depthWrite) {}
    private static final Map<Material,net.minecraft.client.renderer.RenderType> MATERIALS=new java.util.HashMap<>();
    private static final Map<DarkSkinParticles.Emitter,net.minecraft.client.renderer.RenderType> TYPES=new IdentityHashMap<>();
    private static final class SourceType extends net.minecraft.client.renderer.RenderType {
        SourceType(net.minecraft.client.renderer.RenderType shared,boolean depthWrite) {
            super("dark_crystal_source_effect",com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,65536,false,false,()->{
                shared.setupRenderState();
                com.mojang.blaze3d.systems.RenderSystem.depthMask(depthWrite);
                com.mojang.blaze3d.systems.RenderSystem.colorMask(true,true,true,false);
                com.mojang.blaze3d.systems.RenderSystem.polygonOffset(-1,-1);
            },()->{
                try {shared.clearRenderState();}
                finally {
                    com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
                    com.mojang.blaze3d.systems.RenderSystem.colorMask(true,true,true,true);
                }
            });
        }
    }
    public static net.minecraft.client.renderer.RenderType type(DarkSkinParticles.Emitter emitter) {
        return TYPES.computeIfAbsent(emitter,e->MATERIALS.computeIfAbsent(new Material(e.texture,e.render,e.depthWrite),m->
            new SourceType(DimensionEffectRenderType.of(new ResourceLocation(m.texture),"additive".equals(m.render)),m.depthWrite)));
    }
    private static final class Work {
        final DarkSkinParticles.Frame frame=new DarkSkinParticles.Frame();
        final Matrix3f inverse=new Matrix3f();
        final Matrix4f cameraInverse=new Matrix4f();
        final Vector3f right=new Vector3f(),up=new Vector3f(),camera=new Vector3f();
    }
    private static final ThreadLocal<ArrayDeque<Work>> WORK=ThreadLocal.withInitial(ArrayDeque::new);
    private static final long START=System.nanoTime();
    private static volatile Map<ResourceLocation,Profile> profiles=Map.of();
    private DarkSkinEffects() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDarkCrystalSkinEffects(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DarkSkinEffects::read);
        }
    }

    public static void read(ResourceManager resources) {
        profiles=Map.of();TYPES.clear();MATERIALS.clear();WORK.remove();var loaded=new LinkedHashMap<ResourceLocation,Profile>();
        try(var stream=resources.open(new ResourceLocation("ssjj_dark","effects/skins/profiles.json"));
            var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var index=new Gson().fromJson(reader,Index.class);
            if(index==null||index.schema!=1||index.profiles==null||index.profiles.size()!=12)throw new IllegalArgumentException("Expected twelve source weapon profiles");
            var skins=new ArrayList<String>();skins.add("");skins.addAll(DarkSkins.SKINS);
            for(var gun:DarkSkins.GUNS)for(String skin:skins) {
                String key=gun.getPath()+(skin.isEmpty()?"":"_"+skin);var profile=index.profiles.get(key);
                if(profile==null)throw new IllegalArgumentException("Missing source skin profile "+key);
                profile.bodyData=readData(resources,profile.body,"ssjj_dark:"+key);
                for(var emitter:profile.bodyData.emitters)if(!emitter.loop)throw new IllegalArgumentException("Source body must preserve authored loop: "+key);
                if(profile.events!=null)for(var entry:profile.events.entrySet()) {
                    var event=entry.getValue();
                    if(!List.of("31","170","20").contains(entry.getKey())||event==null||!Float.isFinite(event.delay)||event.delay<0||event.delay>10)
                        throw new IllegalArgumentException("Invalid source skin animation event");
                    event.data=readData(resources,event.effect,"ssjj_dark:"+key);
                    for(var emitter:event.data.emitters)if(emitter.loop)throw new IllegalArgumentException("Attack event must be one source activation");
                }
                loaded.put(DarkSkins.display(gun,skin),profile);
            }
            profiles=Map.copyOf(loaded);
            LogUtils.getLogger().info("DARK_CRYSTAL source skin effect profiles loaded: {}",profiles.size());
        } catch(Exception failure) {LogUtils.getLogger().error("Cannot load Dark Crystal source skin effects",failure);}
    }

    private static DarkSkinParticles.Data readData(ResourceManager resources,String name,String owner)throws Exception {
        if(name==null||!name.startsWith("ssjj_dark:effects/skins/"))throw new IllegalArgumentException("Foreign source effect resource");
        try(var stream=resources.open(new ResourceLocation(name));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var data=new Gson().fromJson(reader,DarkSkinParticles.Data.class);DarkSkinParticles.validate(data,owner);
            for(var emitter:data.emitters) {
                if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing source skin texture "+emitter.texture);
                type(emitter);
            }
            return data;
        }
    }

    private static ObjectAnimationRunner selected(ObjectAnimationRunner runner) {
        while(runner!=null&&runner.isTransitioning())runner=runner.getTransitionTo();return runner;
    }
    /** The native acknowledged action track is the clock; key presses do not create visual attacks. */
    public static List<DimensionParticles.Batch> sample(Profile profile,float bodyClock,AnimationStateMachine<?> machine,Vector3f right,Vector3f up,Vector3f camera) {
        var batches=new ArrayList<>(DarkSkinParticles.sample(profile.bodyData,bodyClock,right,up,camera));
        if(profile.events==null||profile.events.isEmpty()||machine==null||!machine.isInitialized()||machine.getContext()==null)return batches;
        var context=machine.getContext();var runner=selected(machine.getAnimationController().getAnimation(context.getTrack(0,1)));
        if(runner==null||!runner.isRunning())return batches;
        String event=switch(runner.getAnimation().name){case "dark_light"->"31";case "dark_light_alt"->"170";case "dark_heavy"->"20";default->null;};
        var chosen=event==null?null:profile.events.get(event);if(chosen==null)return batches;
        float age=runner.getProgressNs()/1e9f-chosen.delay;
        batches.addAll(DarkSkinParticles.sample(chosen.data,age,right,up,camera));return batches;
    }

    public static void defer(ResourceLocation display,BedrockAnimatedModel model,String bone,PoseStack pose,AnimationStateMachine<?> machine) {
        var selected=profiles.get(display);if(selected==null||!"WeaponAnchor".equals(bone))return;
        Matrix4f captured=new Matrix4f(pose.last().pose());Matrix3f normal=new Matrix3f(pose.last().normal());
        float clock=(System.nanoTime()-START)/1e9f;
        model.delegateRender((unused,consumer,context,light,overlay)->{
            var pool=WORK.get();var work=pool.pollFirst();if(work==null)work=new Work();
            try {
                work.inverse.set(captured).invert();work.inverse.transform(work.right.set(1,0,0)).normalize();work.inverse.transform(work.up.set(0,1,0)).normalize();
                work.cameraInverse.set(captured).invert().transformPosition(work.camera.zero());
                var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
                draw(selected.bodyData,clock,work,captured,normal,buffers);
                if(selected.events!=null&&!selected.events.isEmpty()&&machine!=null&&machine.isInitialized()&&machine.getContext()!=null) {
                    var runner=selected(machine.getAnimationController().getAnimation(machine.getContext().getTrack(0,1)));
                    if(runner!=null&&runner.isRunning()) {
                        String event=switch(runner.getAnimation().name){case "dark_light"->"31";case "dark_light_alt"->"170";case "dark_heavy"->"20";default->null;};
                        var effect=event==null?null:selected.events.get(event);if(effect!=null)draw(effect.data,runner.getProgressNs()/1e9f-effect.delay,work,captured,normal,buffers);
                    }
                }
            } finally {pool.addFirst(work);}
        });
    }
    private static void draw(DarkSkinParticles.Data data,float clock,Work work,Matrix4f pose,Matrix3f normal,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
        DarkSkinParticles.fill(data,clock,work.right,work.up,work.camera,work.frame);
        net.minecraft.client.renderer.RenderType active=null;com.mojang.blaze3d.vertex.VertexConsumer out=null;
        for(int i=0;i<work.frame.count;i++) {
            var draw=work.frame.get(i);var type=type(draw.emitter);
            if(type!=active){if(active!=null)buffers.endBatch(active);active=type;out=buffers.getBuffer(type);}
            DarkSkinParticles.emit(draw,pose,normal,out);
        }
        if(active!=null)buffers.endBatch(active);
    }
}
