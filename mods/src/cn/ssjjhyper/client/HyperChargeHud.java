package cn.ssjjhyper.client;

import cn.judgfire.BurstHudState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original Hyper skill-card art; energy and remaining duration use separate bars. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperChargeHud {
    public static final int CARD_WIDTH=210,CARD_HEIGHT=90,BAR_X=76,BAR_Y=62,BAR_WIDTH=125,BAR_HEIGHT=7;
    public static final int CENTER_WIDTH=157,CENTER_HEIGHT=29,CENTER_BAR_X=27,CENTER_BAR_Y=15,CENTER_BAR_WIDTH=126;
    private HyperChargeHud() {}
    private static ResourceLocation texture(int tier,String name){return new ResourceLocation("ssjj_hyper","textures/gui/charge/tier"+tier+"_"+name+".png");}
    /** Authored activebg is the grey card; unactivebg is the gold ready/active artwork. */
    public static String backgroundName(int active,int recovery){return HyperChargeClock.bright(active,recovery)?"unactivebg":"activebg";}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();int tier=HyperChargeClient.heldTier();
        if(tier==0||mc.player==null||mc.level==null||mc.options.hideGui||!RenderConfig.GUN_HUD_ENABLE.get()||!mc.player.isAlive()||mc.player.isSpectator())return;
        var gui=event.getGuiGraphics();int width=event.getWindow().getGuiScaledWidth(),height=event.getWindow().getGuiScaledHeight();
        int active=HyperChargeClient.activeTicks(),cooldown=HyperChargeClient.cooldownTicks();
        var card=BurstHudState.layout(width,height);
        gui.pose().pushPose();gui.pose().translate(card.x(),card.y(),500);gui.pose().scale(card.scale(),card.scale(),1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        gui.blit(texture(tier,backgroundName(active,cooldown)),0,0,0,0,CARD_WIDTH,CARD_HEIGHT,CARD_WIDTH,CARD_HEIGHT);
        // Remaining charge stays visible and recovers from that amount; only cooldown art is grey.
        float ratio=HyperChargeClient.energyRatio(event.getPartialTick());
        int fill=Math.round(BAR_WIDTH*ratio);
        if(fill>0)gui.blit(texture(tier,cooldown>0?"recoverprogress":"normalprogress"),BAR_X,BAR_Y,0,0,fill,BAR_HEIGHT,BAR_WIDTH,BAR_HEIGHT);
        gui.pose().popPose();
        if(active>0) {
            var center=HyperChargeClock.center(width,height,tier);
            gui.pose().pushPose();gui.pose().translate(center.x(),center.y(),500);gui.pose().scale(center.scale(),center.scale(),1);
            gui.blit(texture(tier,"crossprogressbg"),0,0,0,0,CENTER_WIDTH,CENTER_HEIGHT,CENTER_WIDTH,CENTER_HEIGHT);
            int remaining=Math.round(CENTER_BAR_WIDTH*HyperChargeClient.durationRatio(event.getPartialTick()));
            if(remaining>0)gui.blit(texture(tier,"crossprogress"),CENTER_BAR_X,CENTER_BAR_Y,0,0,remaining,BAR_HEIGHT,CENTER_BAR_WIDTH,BAR_HEIGHT);
            gui.pose().popPose();
        }
        RenderSystem.setShaderColor(1,1,1,1);
    }
}
