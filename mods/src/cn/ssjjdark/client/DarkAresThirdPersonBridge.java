package cn.ssjjdark.client;

import cn.ssjjdark.DarkSkins;
import cn.ssjjares.client.AresSourceBodyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.TransformScale;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Selects an Ares hand locator per draw without changing shared model nodes. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkAresThirdPersonBridge {
    private static final String YSM_RENDERER="com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo";
    private record Frame(AbstractClientPlayer owner,ItemStack held,ResourceLocation gun,BedrockGunModel model,String locator) {}
    private static final class Scope {
        private final LivingEntity entity;
        private Frame frame;
        private Scope(LivingEntity entity){this.entity=entity;}
    }
    private static final Map<PoseStack,ArrayDeque<Scope>> frames=new IdentityHashMap<>();
    private record PrivateDraw(BedrockGunModel model,String locator) {}
    private static final Map<PoseStack,ArrayDeque<PrivateDraw>> privateDraws=new IdentityHashMap<>();
    private static boolean warned;
    private DarkAresThirdPersonBridge() {}

    /** A detached actor has a copied item and no live RenderLiving scope. */
    public static void privateItem(BedrockGunModel model,TransformScale scale,String locator,PoseStack pose,Runnable draw) {
        if(!"thirdperson_hand_ares".equals(locator)&&!"thirdperson_hand_ysm".equals(locator))throw new IllegalArgumentException("Foreign private actor locator");
        if(model.getNode("WeaponAnchor") instanceof FunctionalBedrockPart anchor&&anchor.functionalRenderer!=null&&model.getNode(locator)!=null) {
            if(!(anchor.functionalRenderer instanceof SourceFactory))anchor.functionalRenderer=new SourceFactory(anchor.functionalRenderer,model,scale);
        }
        var stack=privateDraws.computeIfAbsent(pose,p->new ArrayDeque<>());var scope=new PrivateDraw(model,locator);stack.addFirst(scope);
        try{draw.run();}finally{stack.removeFirstOccurrence(scope);if(stack.isEmpty())privateDraws.remove(pose);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void beforeLiving(RenderLivingEvent.Pre<?,?> event) {
        // Both the YSM renderer and the Ares source renderer emit this around
        // their held-item layers. Source FK never has a native YSM pose cache.
        if(event.isCanceled())return;
        var scope=new Scope(event.getEntity());
        frames.computeIfAbsent(event.getPoseStack(),pose->new ArrayDeque<>()).addFirst(scope);
        boolean source=sourceRenderer(event.getRenderer());
        if((!source&&!ysmRenderer(event.getRenderer())) || !(event.getEntity() instanceof AbstractClientPlayer player))return;
        try {
            if(!source&&!DarkYsmMelee.isReady(player))return;
            boolean ares=source||DarkYsmMelee.isAres(player);
            var held=player.getMainHandItem();var gun=IGun.getIGunOrNull(held);
            if(gun==null || !DarkSkins.family(gun.getGunId(held)))return;
            var display=TimelessAPI.getGunDisplay(held).orElse(null);if(display==null)return;
            var model=display.getGunModel();
            if(!(model.getNode("WeaponAnchor") instanceof FunctionalBedrockPart anchor)
                || anchor.functionalRenderer==null)return;
            if(!(anchor.functionalRenderer instanceof SourceFactory))
                anchor.functionalRenderer=new SourceFactory(anchor.functionalRenderer,model,display.getTransform().getScale());
            scope.frame=new Frame(player,held,gun.getGunId(held),model,ares?"thirdperson_hand_ares":"thirdperson_hand_ysm");
        } catch(RuntimeException|LinkageError failure) {warn(failure);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void afterLiving(RenderLivingEvent.Post<?,?> event) {
        var scopes=frames.get(event.getPoseStack());
        if(scopes==null || scopes.stream().noneMatch(scope->scope.entity==event.getEntity()))return;
        // Also discard an inner scope whose Post was skipped by late cancellation.
        while(!scopes.isEmpty())if(scopes.removeFirst().entity==event.getEntity())break;
        if(scopes.isEmpty())frames.remove(event.getPoseStack());
    }
    @SubscribeEvent
    public static void afterEntities(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES)frames.clear();
    }
    private static boolean ysmRenderer(Object renderer) {
        for(Class<?> type=renderer.getClass();type!=null;type=type.getSuperclass())
            if(YSM_RENDERER.equals(type.getName()))return true;
        return false;
    }
    private static boolean sourceRenderer(Object renderer) {return renderer instanceof AresSourceBodyVisuals.SourceRenderer;}
    private static boolean selected(Frame frame,BedrockGunModel model) {
        if(frame==null || frame.model!=model || frame.owner.getMainHandItem()!=frame.held)return false;
        var gun=IGun.getIGunOrNull(frame.held);
        return gun!=null && frame.gun.equals(gun.getGunId(frame.held))
            && TimelessAPI.getGunDisplay(frame.held).map(display->display.getGunModel()==model).orElse(false);
    }
    private static Frame current(PoseStack pose) {
        var scopes=frames.get(pose);return scopes==null || scopes.isEmpty()?null:scopes.getFirst().frame;
    }
    private static void warn(Throwable failure) {
        if(!warned){warned=true;LogUtils.getLogger().warn("Cannot select Dark Crystal Ares hand locator; using native hand locator",failure);}
    }
    private static final class SourceFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final BedrockGunModel model;
        private final TransformScale scale;
        private SourceFactory(Function<BedrockPart,IFunctionalRenderer> original,BedrockGunModel model,TransformScale scale) {
            this.original=original;this.model=model;this.scale=scale;
        }
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            var source=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                Matrix4f change=null;
                if(context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)try {
                    var stack=privateDraws.get(pose);
                    if(stack!=null&&!stack.isEmpty()){
                        var detached=stack.getFirst();if(detached.model==model)change=correction(model,scale,detached.locator);
                    }else{var frame=current(pose);if(selected(frame,model))change=correction(model,scale,frame.locator);}
                } catch(RuntimeException|LinkageError failure){warn(failure);}
                if(change==null){source.render(pose,consumer,context,light,overlay);return;}
                pose.pushPose();
                try {
                    pose.mulPoseMatrix(change);
                    pose.last().normal().mul(new Matrix3f(change).invert().transpose());
                    source.render(pose,consumer,context,light,overlay);
                } finally {pose.popPose();}
            };
        }
    }
    private static List<BedrockPart> path(BedrockPart node) {
        if(node==null)throw new IllegalStateException("Missing Dark Crystal Ares locator/source anchor");
        var result=new ArrayList<BedrockPart>();
        for(var part=node;part!=null;part=part.getParent())result.add(part);
        Collections.reverse(result);return result;
    }
    /** Relative native locator transform at the already transformed source anchor. */
    public static Matrix4f correction(BedrockGunModel model,TransformScale scale) {
        return correction(model,scale,"thirdperson_hand_ares");
    }
    public static Matrix4f correction(BedrockGunModel model,TransformScale scale,String locator) {
        try {
            var normal=model.getThirdPersonHandOriginPath();
            if(normal==null || normal.isEmpty())throw new IllegalStateException("Missing native third person locator");
            var ares=path(model.getNode(locator));
            var anchor=path(model.getNode("WeaponAnchor"));
            Matrix4f from=position(normal,anchor,scale),to=position(ares,anchor,scale);
            if(!from.isFinite() || !to.isFinite() || Math.abs(from.determinant())<1e-8f)
                throw new IllegalStateException("Invalid Dark Crystal hand locator matrix");
            var change=from.invert().mul(to);
            if(!change.isFinite() || Math.abs(change.determinant())<1e-8f)
                throw new IllegalStateException("Invalid Dark Crystal Ares correction");
            return change;
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Unsupported native gun item transforms",failure);}
    }
    private static Matrix4f position(List<BedrockPart> locator,List<BedrockPart> anchor,TransformScale scale) throws ReflectiveOperationException {
        var pose=new PoseStack();
        Native.POSITION.invoke(null,locator,pose,scale.getThirdPerson());
        Native.SCALE.invoke(null,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,scale,pose);
        for(var part:anchor)part.translateAndRotateAndScale(pose);
        return new Matrix4f(pose.last().pose());
    }
    private static final class Native {
        private static final Method POSITION, SCALE;
        static {
            try {
                String prefix="com.tacz.guns.client.renderer.item.";
                Class<?> type;
                String positionName, scaleName;
                try {
                    type=Class.forName(prefix+"GunItemTransforms");
                    positionName="applyPositioningNode";
                    scaleName="applyScale";
                } catch(ClassNotFoundException legacy) {
                    type=Class.forName(prefix+"GunItemRendererWrapper");
                    positionName="applyPositioningNodeTransform";
                    scaleName="applyScaleTransform";
                }
                POSITION=type.getDeclaredMethod(positionName,List.class,PoseStack.class,Vector3f.class);
                SCALE=type.getDeclaredMethod(scaleName,ItemDisplayContext.class,TransformScale.class,PoseStack.class);
                POSITION.setAccessible(true);SCALE.setAccessible(true);
            } catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
        }
    }
}
