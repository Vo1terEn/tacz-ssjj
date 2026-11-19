package cn.ssjjhyper.client;

import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjhyper.HyperCharge;
import cn.ssjjhyper.HyperChargeNetwork;
import cn.ssjjhyper.HyperScopes;
import cn.ssjjdimension.client.DimensionParticles;
import cn.ssjjdimension.client.DimensionEffectRenderType;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.*;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Source SPECIAL_ATTACK muzzle burst in the native animated hand/model pass. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperChargeFlash {
    private static final String BONE="muzzle_flash",PREFIX="ssjj_hyper:textures/effects/energy_flash_clean/";
    private record Atlas(int rows,int columns,int width,int height,int content_width,int content_height,int gutter) {}
    private record Profile(String key,ResourceLocation gun,ResourceLocation display,DimensionParticles.Data data,float duration,Map<String,Atlas> atlases) {}
    private record Burst(UUID id,ItemStack stack,int slot,Object display,Profile profile,long born) {}
    private record Material(String texture,String render) {}
    private static volatile Map<String,Profile> profiles=Map.of();
    private static final Map<UUID,Burst> bursts=new LinkedHashMap<>();
    private static final Set<UUID> recent=new LinkedHashSet<>();
    private static ClientLevel level;
    private HyperChargeFlash() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperChargeFlash(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)HyperChargeFlash::read);}
    }
    public static void read(ResourceManager resources) {
        profiles=Map.of();bursts.clear();recent.clear();var loaded=new LinkedHashMap<String,Profile>();var gson=new Gson();
        for(String gun:List.of("hyper_revolver1","hyper_revolver2"))for(String skin:List.of("","totem","star","colorful_spar")) {
            String key=gun+(skin.isEmpty()?"":"_"+skin);var id=new ResourceLocation("ssjj_hyper",gun);
            try(var input=resources.open(new ResourceLocation("ssjj_hyper","effects/energy_flash/"+key+".json"));var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                var root=JsonParser.parseReader(reader).getAsJsonObject();var data=gson.fromJson(root,DimensionParticles.Data.class);
                DimensionParticles.validate(data,id.toString(),BONE,PREFIX);float duration=0;var atlas=new LinkedHashMap<String,Atlas>();
                for(var item:root.getAsJsonObject("texture_atlas").entrySet()) {
                    var a=gson.fromJson(item.getValue(),Atlas.class);
                    if(a.rows<1||a.columns<1||a.width<1||a.height<1||a.content_width<1||a.content_height<1||a.gutter!=4)throw new IllegalArgumentException("Invalid flash sprite atlas");
                    atlas.put(item.getKey(),a);
                }
                for(var emitter:data.emitters) {
                    if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty()||!atlas.containsKey(emitter.texture))throw new IllegalArgumentException("Missing own-skin flash texture "+emitter.texture);
                    if(emitter.duration!=60)throw new IllegalArgumentException("Flash must be an isolated source burst");
                    for(var p:emitter.particles)duration=Math.max(duration,p.birth+p.life);
                }
                if(duration<=0||duration>=2)throw new IllegalArgumentException("Invalid flash lifetime");
                loaded.put(key,new Profile(key,id,HyperScopes.display(id,skin),data,duration,Map.copyOf(atlas)));
            } catch(Exception failure){LogUtils.getLogger().error("Cannot load Hyper special muzzle burst "+key,failure);}
        }
        profiles=Map.copyOf(loaded);
    }
    private static void world() {
        var mc=Minecraft.getInstance();var next=mc==null?null:mc.level;
        if(next!=level){level=next;bursts.clear();recent.clear();}
    }
    /** Called once per accepted server batch, after the native attack action accepts it. */
    public static void cast(HyperChargeNetwork.Visual event) {
        world();var mc=Minecraft.getInstance();if(mc==null||level==null||mc.player==null||event==null||!event.valid()||event.type()!=6)return;
        if(!event.owner().equals(mc.player.getUUID())||!event.targetUuid().equals(mc.player.getUUID())||!event.dimension().equals(level.dimension().location())||recent.contains(event.id()))return;
        var stack=mc.player.getMainHandItem();var profile=profiles.get(event.profile());var display=TimelessAPI.getGunDisplay(stack).orElse(null);long now=level.getGameTime();
        if(HyperChargeClient.heldTier()==0||profile==null||!HyperCharge.profile(stack).equals(profile.key)||display==null||display.getGunModel()==null||now>=event.tick()+event.life())return;
        install(display.getGunModel(),profile.key);remember(event.id());
        bursts.put(event.id(),new Burst(event.id(),stack,mc.player.getInventory().selected,display,profile,now));
        while(bursts.size()>4)bursts.remove(bursts.keySet().iterator().next());
    }
    private static boolean remember(UUID id){if(!recent.add(id))return false;if(recent.size()>32)recent.remove(recent.iterator().next());return true;}
    private static float age(Burst burst,long now,float partial){return (now-burst.born+partial)/20f;}
    private static boolean alive(Burst burst,long now,float partial){float age=age(burst,now,partial);return age>=0&&age<burst.profile.duration;}
    private static boolean held(Burst burst) {
        var mc=Minecraft.getInstance();return mc!=null&&mc.level==level&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&mc.player.getMainHandItem()==burst.stack&&mc.player.getInventory().selected==burst.slot
            &&HyperCharge.profile(burst.stack).equals(burst.profile.key)&&TimelessAPI.getGunDisplay(burst.stack).orElse(null)==burst.display;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();if(level!=null)bursts.values().removeIf(b->!held(b)||!alive(b,level.getGameTime(),0));
        for(var profile:profiles.values())TaCZClientCompat.forEachGunDisplay(profile.display,profile.gun,display->{var model=display.getGunModel();if(model!=null)install(model,profile.key);});
    }
    /** Wrap the existing native muzzle renderer, preserving its normal-shot behavior. */
    public static void install(BedrockAnimatedModel model,String profile) {
        var part=model.getNode(BONE);if(part==null)throw new IllegalArgumentException("Missing original muzzle_flash alias");
        Function<BedrockPart,IFunctionalRenderer> old=part instanceof FunctionalBedrockPart f?f.functionalRenderer:null;
        if(old instanceof FlashFactory current&&current.profile.equals(profile))return;
        if(old instanceof FlashFactory current)old=current.original;
        var original=old;model.setFunctionalRenderer(BONE,new FlashFactory(model,profile,original));
    }
    private static final class FlashFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final BedrockAnimatedModel model;private final String profile;private final Function<BedrockPart,IFunctionalRenderer> original;
        FlashFactory(BedrockAnimatedModel model,String profile,Function<BedrockPart,IFunctionalRenderer> original){this.model=model;this.profile=profile;this.original=original;}
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            var nativeRenderer=original==null?null:original.apply(node);
            return new IFunctionalRenderer() {
                public void render(PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,net.minecraft.world.item.ItemDisplayContext context,int light,int overlay) {
                    if(nativeRenderer!=null)nativeRenderer.render(pose,consumer,context,light,overlay);else for(var child:node.children)child.render(pose,context,consumer,light,overlay);
                    if(context!=null&&context.firstPerson())defer(model,profile,pose);
                }
                // TaCZ1.2 native muzzle implements this overload; older named APIs
                // have only the five-argument callback. Preserve both dispatches.
                public void render(PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,net.minecraft.world.item.ItemDisplayContext context,int light,int overlay,net.minecraft.client.renderer.MultiBufferSource buffers) {
                    if(nativeRenderer!=null)NativeBuffer.render(nativeRenderer,pose,consumer,context,light,overlay,buffers);else for(var child:node.children)child.render(pose,context,consumer,light,overlay);
                    if(context!=null&&context.firstPerson())defer(model,profile,pose);
                }
            };
        }
    }
    private static final class NativeBuffer {
        private static final java.lang.reflect.Method METHOD=find();
        private static java.lang.reflect.Method find(){try{return IFunctionalRenderer.class.getMethod("render",PoseStack.class,com.mojang.blaze3d.vertex.VertexConsumer.class,net.minecraft.world.item.ItemDisplayContext.class,int.class,int.class,net.minecraft.client.renderer.MultiBufferSource.class);}catch(NoSuchMethodException legacy){return null;}}
        private static void render(IFunctionalRenderer renderer,PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer consumer,net.minecraft.world.item.ItemDisplayContext context,int light,int overlay,net.minecraft.client.renderer.MultiBufferSource buffers) {
            if(METHOD==null){renderer.render(pose,consumer,context,light,overlay);return;}
            try{METHOD.invoke(renderer,pose,consumer,context,light,overlay,buffers);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot preserve native muzzle buffer callback",failure);}
        }
    }
    private static float[] atlasUV(DimensionParticles.Batch batch,Profile profile) {
        float[] st=batch.uvST().clone();var a=profile.atlases.get(batch.texture());if(a==null||a.columns*a.rows==1)return st;
        double sx=(double)(a.content_width+2*a.gutter)/a.width,sy=(double)(a.content_height+2*a.gutter)/a.height;
        double col=(st[2]-(a.gutter+.5)/a.width)/sx,row=(-st[3]-(a.gutter+.5)/a.height)/sy;
        int frame=Math.max(0,Math.min(a.columns*a.rows-1,(int)Math.floor(row*a.columns+col+1e-4)));
        st[2]=(float)(((frame%a.columns)*(a.content_width+2*a.gutter)+a.gutter+.5)/a.width);
        st[3]=(float)(-((frame/a.columns)*(a.content_height+2*a.gutter)+a.gutter+.5)/a.height);return st;
    }
    private static void defer(BedrockAnimatedModel model,String profile,PoseStack pose) {
        var mc=Minecraft.getInstance();if(mc==null||mc.player==null||mc.level==null||mc.level!=level||bursts.isEmpty())return;
        var stack=TaCZClientCompat.currentRenderedItem();if(stack==null)return;var display=TimelessAPI.getGunDisplay(stack).orElse(null);if(display==null||display.getGunModel()!=model)return;
        long now=level.getGameTime();float partial=mc.getFrameTime();var selected=bursts.values().stream().filter(b->b.display==display&&b.profile.key.equals(profile)&&held(b)&&alive(b,now,partial)).toList();if(selected.isEmpty())return;
        // Keep the actual animated bone pose and native hand projection/FOV. No world pass.
        Matrix4f captured=new Matrix4f(pose.last().pose());Matrix3f normal=new Matrix3f(pose.last().normal());
        model.delegateRender((unused,consumer,context,light,overlay)->{
            Matrix3f inverse=new Matrix3f(captured).invert();var right=inverse.transform(new Vector3f(1,0,0)).normalize();var up=inverse.transform(new Vector3f(0,1,0)).normalize();
            var eye=new Matrix4f(captured).invert().transformPosition(new Vector3f());PoseStack local=new PoseStack();local.last().pose().set(captured);local.last().normal().set(normal);
            var groups=new LinkedHashMap<Material,List<DimensionParticles.Batch>>();
            for(var burst:selected)for(var batch:DimensionParticles.sample(burst.profile.data,age(burst,now,partial),right,up,eye)) {
                var fixed=new DimensionParticles.Batch(batch.texture(),batch.render(),batch.mesh(),batch.color(),atlasUV(batch,burst.profile));
                groups.computeIfAbsent(new Material(batch.texture(),batch.render()),key->new ArrayList<>()).add(fixed);
            }
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            for(var entry:groups.entrySet()) {
                boolean additive="additive".equals(entry.getKey().render);var type=DimensionEffectRenderType.of(new ResourceLocation(entry.getKey().texture),additive);var out=buffers.getBuffer(type);
                for(var batch:entry.getValue()) {
                    var mesh=batch.mesh();float[] c=batch.color(),st=batch.uvST();float rgb=Math.max(1,Math.max(c[0],Math.max(c[1],c[2]))),energy=additive?Math.min(16,rgb*c[3]):Math.min(1,c[3]);
                    for(int pass=0;pass<(additive?(int)Math.ceil(energy):1);pass++)for(int[] face:mesh.triangles)for(int corner=0;corner<4;corner++) {
                        int i=face[Math.min(corner,2)];float[] p=mesh.positions[i],uv=mesh.uv[i];float alpha=additive?Math.min(1,energy-pass):energy;
                        out.vertex(local.last().pose(),p[0],p[1],p[2]).color(Math.min(1,c[0]/(additive?rgb:1)),Math.min(1,c[1]/(additive?rgb:1)),Math.min(1,c[2]/(additive?rgb:1)),alpha)
                            .uv(uv[0]*st[0]+st[2],uv[1]*st[1]-st[3]).overlayCoords(0).uv2(15728880).normal(local.last().normal(),0,0,1).endVertex();
                    }
                }buffers.endBatch(type);
            }
        });
    }
}
