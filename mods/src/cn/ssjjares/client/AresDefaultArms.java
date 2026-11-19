package cn.ssjjares.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keep TaCZ's prepared arm matrix and draw the player's ordinary skin/sleeve. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class AresDefaultArms {
    private static Method hand;
    private static boolean failed;
    private AresDefaultArms(){}
    private static Method hand() throws ReflectiveOperationException {
        if(hand!=null)return hand;
        for(String name:new String[]{"m_117775_","renderHand"}){
            try{hand=PlayerRenderer.class.getDeclaredMethod(name,PoseStack.class,MultiBufferSource.class,int.class,AbstractClientPlayer.class,ModelPart.class,ModelPart.class);hand.setAccessible(true);return hand;}
            catch(NoSuchMethodException ignored){}
        }
        throw new NoSuchMethodException("PlayerRenderer ordinary hand draw");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void arm(RenderArmEvent event){
        if(AresWeightedHands.modelTier(event.getPlayer())==0)return;
        try{
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(event.getPlayer());
            if(!(renderer instanceof PlayerRenderer playerRenderer))return;
            var model=playerRenderer.getModel();boolean left=event.getArm()==HumanoidArm.LEFT;
            // Public renderLeft/RightHand post RenderArmEvent again in Forge.
            // The private shared draw is exactly their ordinary non-event path.
            hand().invoke(playerRenderer,event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight(),event.getPlayer(),left?model.leftArm:model.rightArm,left?model.leftSleeve:model.rightSleeve);
            event.setCanceled(true);
        }catch(ReflectiveOperationException|RuntimeException error){
            if(!failed){failed=true;LogUtils.getLogger().error("Unable to draw ordinary Ares-player hands",error);}
        }
    }
}
