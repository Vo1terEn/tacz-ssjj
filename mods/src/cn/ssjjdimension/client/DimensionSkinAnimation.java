package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionScopes;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The installed SimpleBedrock adapter calls tryInit only once per item
 * instance. A same-item skin change retains that adapter's drawn=true while
 * rendering a new display with a cold state machine. Initialize that display
 * before its first hand render; never restart warm or outgoing controllers.
 */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionSkinAnimation {
    private DimensionSkinAnimation() {}

    public static boolean prepare(ItemStack rendered,ItemStack held,GunItemRendererWrapper renderer,Player player,float partialTick) {
        if(rendered==null || held==null || player==null)return false;
        var gun=IGun.getIGunOrNull(rendered);var heldGun=IGun.getIGunOrNull(held);
        if(gun==null || heldGun==null || !DimensionScopes.family(gun.getGunId(rendered))
            || !gun.getGunId(rendered).equals(heldGun.getGunId(held))
            || !gun.getGunDisplayId(rendered).equals(heldGun.getGunDisplayId(held))
            || !gun.getAttachmentId(rendered,AttachmentType.SCOPE).equals(heldGun.getAttachmentId(held,AttachmentType.SCOPE)))return false;
        if(!renderer.needReInit(rendered))return false;
        renderer.tryInit(rendered,player,partialTick);
        LogUtils.getLogger().debug("DIMENSION_SKIN initialized cold display retained by drawn item adapter: {}",gun.getGunDisplayId(rendered));
        return true;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void prepareDimensionSkinHandFrame(RenderHandEvent event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var player=Minecraft.getInstance().player;if(player==null)return;
        // Use exactly the item the installed handler will render, including
        // its previous-item transition, rather than the vanilla event snapshot.
        var instance=FirstPersonRenderHandler.getActiveAnimationInstance();if(instance==null)return;
        var stack=instance.currentItem();var gun=IGun.getIGunOrNull(stack);
        if(gun==null || !DimensionScopes.family(gun.getGunId(stack)))return;
        var renderer=IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
        if(renderer instanceof GunItemRendererWrapper nativeRenderer)prepare(stack,player.getMainHandItem(),nativeRenderer,player,event.getPartialTick());
    }
}
