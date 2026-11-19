package cn.ssjjares.client;

import cn.ssjjares.AresArmorItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native PlayerRenderer still runs poses, held guns, all layers and name tags. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class AresPlayerVisuals {
    private static final ResourceLocation MESH=new ResourceLocation("ssjj_ares","models/ares_body.bin");
    private static final ResourceLocation BASE=new ResourceLocation("ssjj_ares","textures/entity/ares1.png"),ABSOLUTE=new ResourceLocation("ssjj_ares","textures/entity/ares2.png");
    private static AresBodyMesh mesh;
    private record Hidden(Object entity,ModelPart[] parts,boolean[] visible,boolean hidden){}
    private static final IdentityHashMap<PlayerModel<?>,ArrayDeque<Hidden>> HIDDEN=new IdentityHashMap<>();
    private static final ThreadLocal<AresBodyMesh.Scratch> SCRATCH=ThreadLocal.withInitial(AresBodyMesh.Scratch::new);
    private AresPlayerVisuals(){}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private static final java.util.Set<PlayerRenderer> ADDED=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
        /** YSM now owns appearance. Do not attach a second native body layer. */
        public static void layers(EntityRenderersEvent.AddLayers event){}
        public static void resources(RegisterClientReloadListenersEvent event){}
    }
    public static void reload(ResourceManager resources){restoreAll();mesh=null;SCRATCH.remove();}
    static boolean active(AbstractClientPlayer player){return false;}
    static ModelPart[] parts(PlayerModel<?> model){return new ModelPart[]{model.head,model.hat,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg,model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants,model.jacket};}
    public static void before(RenderLivingEvent.Pre<?,?> event){}
    public static void after(RenderLivingEvent.Post<?,?> event){}
    private static void restore(Hidden state){for(int i=0;i<state.parts.length;i++)state.parts[i].visible=state.visible[i];}
    private static void restoreAll(){for(var stack:HIDDEN.values())while(!stack.isEmpty())restore(stack.pop());HIDDEN.clear();}
    public static void frameEnd(TickEvent.RenderTickEvent event){}
    public static final class BodyLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
        public BodyLayer(PlayerRenderer renderer){super(renderer);}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float walk,float speed,float partial,float age,float yaw,float pitch){
            if(!active(player))return;var body=mesh;if(body==null)return;
            var scratch=SCRATCH.get();body.pose(getParentModel(),scratch);
            var texture=AresArmorItems.setTier(player)==2?ABSOLUTE:BASE;
            body.draw(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,LivingEntityRenderer.getOverlayCoords(player,0),scratch);
        }
    }
}
