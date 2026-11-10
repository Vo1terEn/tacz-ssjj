package cn.ssjjdimension.client;

import java.util.Locale;
import cn.judgfire.BurstHudState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Original Chinese skill-card artwork, with only its baked key glyph changed to V. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionFieldHud {
    public static final int SOURCE_WIDTH=210,SOURCE_HEIGHT=90,BAR_X=77,BAR_Y=61,BAR_WIDTH=125,BAR_HEIGHT=7;
    private DimensionFieldHud() {}
    private static ResourceLocation texture(int tier,String name) {
        return new ResourceLocation("ssjj_dimension","textures/gui/field/tier"+tier+"_"+name.toLowerCase(Locale.ROOT)+".png");
    }
    public static float ratio(int active,int cooldown) {
        return Math.max(0,Math.min(1,active>0?active/(float)cn.ssjjdimension.DimensionFieldRules.activeDuration():cooldown>0?1-cooldown/(float)cn.ssjjdimension.DimensionFieldRules.recharge():1));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderGuiEvent.Post event) {
        var minecraft=Minecraft.getInstance();int tier=DimensionFieldClient.heldTier();
        if(tier<1||minecraft.player==null||minecraft.level==null||minecraft.options.hideGui
            ||!RenderConfig.GUN_HUD_ENABLE.get()||!minecraft.player.isAlive()||minecraft.player.isSpectator())return;
        GuiGraphics gui=event.getGuiGraphics();int active=DimensionFieldClient.activeTicks(),cooldown=DimensionFieldClient.cooldownTicks();
        // Share the exact existing Judgment Flame card size and anchor.
        var layout=BurstHudState.layout(event.getWindow().getGuiScaledWidth(),event.getWindow().getGuiScaledHeight());
        gui.pose().pushPose();gui.pose().translate(layout.x(),layout.y(),500);gui.pose().scale(layout.scale(),layout.scale(),1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        // Preserve all source Chinese wording and art. The baker replaces only
        // the E key glyph and its one-pixel fringe; no runtime text or masking is needed.
        gui.blit(texture(tier,active>0?"activeBg":"unactiveBg"),0,0,0,0,SOURCE_WIDTH,SOURCE_HEIGHT,SOURCE_WIDTH,SOURCE_HEIGHT);
        int filled=Math.round(BAR_WIDTH*ratio(active,cooldown));
        if(filled>0)gui.blit(texture(tier,"normalProgress"),BAR_X,BAR_Y,0,0,filled,BAR_HEIGHT,BAR_WIDTH,BAR_HEIGHT);
        gui.pose().popPose();RenderSystem.setShaderColor(1,1,1,1);
    }
}
