package cn.ssjjparasite2.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Preserve TaCZ player skins and projection while applying this gun's calibration. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class NativeHandView {
    private static final ResourceLocation GUN=new ResourceLocation("ssjjparasite2", "parasitic_sniper2");
    private static final ResourceLocation CALIBRATION=new ResourceLocation("ssjjparasite2", "view_calibration.json");
    private static volatile double hipFov=70;
    private NativeHandView() {}

    private static final class Calibration { double fitted_vertical_fov_degrees; }

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientResources {
        @SubscribeEvent public static void registerHandCalibration(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)NativeHandView::read);
        }
    }

    public static void read(ResourceManager resources) {
        hipFov=70;
        try (var stream=resources.open(CALIBRATION);
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Calibration data=new Gson().fromJson(reader,Calibration.class);
            if (data==null || !Double.isFinite(data.fitted_vertical_fov_degrees)
                    || data.fitted_vertical_fov_degrees<=1 || data.fitted_vertical_fov_degrees>=179)
                throw new IllegalArgumentException("Invalid fitted vertical FOV");
            hipFov=data.fitted_vertical_fov_degrees;
            LogUtils.getLogger().info("PARASITE2_VIEW_CALIBRATION vertical FOV {}",hipFov);
        } catch (Exception failure) {
            LogUtils.getLogger().warn("Cannot read Parasite II view calibration; using 70 degree handheld FOV",failure);
        }
    }

    /** Wrap the existing factory so the native skin, sleeves and deferred draw remain intact. */
    public static void installHandFits(BedrockAnimatedModel model) {
        install(model,"lefthand_pos",-.03125f);
        install(model,"righthand_pos",.03125f);
    }

    private static void install(BedrockAnimatedModel model,String name,float slimX) {
        if (!(model.getNode(name) instanceof FunctionalBedrockPart node)) return;
        Function<BedrockPart,IFunctionalRenderer> original=node.functionalRenderer;
        if (original==null || original instanceof HandFactory) return;
        node.functionalRenderer=new HandFactory(original,slimX);
    }

    private static final class HandFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final float slimX;
        HandFactory(Function<BedrockPart,IFunctionalRenderer> original,float slimX) {
            this.original=original; this.slimX=slimX;
        }
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            IFunctionalRenderer nativeHand=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                Minecraft client=Minecraft.getInstance();
                boolean slim=client!=null && client.player!=null && "slim".equals(client.player.getModelName());
                renderFitted(nativeHand,pose,consumer,context,light,overlay,slim,slimX);
            };
        }
    }

    static void renderFitted(IFunctionalRenderer nativeHand,PoseStack pose,VertexConsumer consumer,
            ItemDisplayContext context,int light,int overlay,boolean slim,float slimX) {
        pose.pushPose();
        try {
            // Input already includes animated node TRS. Native callback appends its own Rz(180).
            if (slim) pose.translate(slimX,0,0);
            nativeHand.render(pose,consumer,context,light,overlay);
        } finally { pose.popPose(); }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) return;
        Minecraft client=Minecraft.getInstance();
        if (client==null || client.player==null || event.getCamera().getEntity()!=client.player) return;
        KeepingItemRenderer renderer=KeepingItemRenderer.getRenderer();
        if (renderer==null) return;
        ItemStack stack=renderer.getCurrentItem();
        if (stack==null) return;
        IGun gun=IGun.getIGunOrNull(stack);
        if (gun!=null) applyHipCalibration(event,true,gun.getGunId(stack));
    }

    static void applyHipCalibration(ViewportEvent.ComputeFov event,boolean localCamera,ResourceLocation gun) {
        if (!event.usedConfiguredFov() && localCamera && ParasiteScopes.family(gun)) event.setFOV(hipFov);
    }
}
