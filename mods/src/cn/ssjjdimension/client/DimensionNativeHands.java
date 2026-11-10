package cn.ssjjdimension.client;

import com.google.gson.Gson;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native player skin and sleeve callbacks, with calibration local to this gun. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class DimensionNativeHands {
    private static final ResourceLocation CALIBRATION=new ResourceLocation("ssjj_dimension","view_calibration.json");
    public static final class Calibration {
        public Double fitted_vertical_fov_degrees;
        public float left_slim_x, right_slim_x;
    }
    private static volatile Calibration calibration=new Calibration();
    private DimensionNativeHands() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        // Forge 6.2.33 names ModLauncher wrappers from package + SIMPLE class
        // name + method + event. Both hand and particle nested classes were
        // Resources.register in this package, so the particle wrapper won.
        @SubscribeEvent public static void registerDimensionHandCalibration(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DimensionNativeHands::read);
        }
    }
    public static void validate(Calibration data) {
        if(data==null || !Float.isFinite(data.left_slim_x) || !Float.isFinite(data.right_slim_x)
            || Math.abs(data.left_slim_x)>.25f || Math.abs(data.right_slim_x)>.25f)
            throw new IllegalArgumentException("Invalid native arm calibration");
        if(data.fitted_vertical_fov_degrees!=null && (!Double.isFinite(data.fitted_vertical_fov_degrees)
            || data.fitted_vertical_fov_degrees<=1 || data.fitted_vertical_fov_degrees>=179))
            throw new IllegalArgumentException("Invalid weapon-local vertical FOV");
    }
    public static void read(ResourceManager resources) {
        calibration=new Calibration();
        try(var stream=resources.open(CALIBRATION);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Calibration fresh=new Gson().fromJson(reader,Calibration.class);validate(fresh);calibration=fresh;
            LogUtils.getLogger().info("DIMENSION_STAR2_VIEW_CALIBRATION vertical FOV {}, slim offsets {} / {}",fresh.fitted_vertical_fov_degrees,fresh.left_slim_x,fresh.right_slim_x);
        } catch(Exception failure) {LogUtils.getLogger().warn("Cannot load Dimension Star view calibration; retaining native FOV and arm widths",failure);}
    }
    public static void install(BedrockAnimatedModel model) {
        install(model,"lefthand_pos",true);install(model,"righthand_pos",false);
    }
    private static void install(BedrockAnimatedModel model,String name,boolean left) {
        if(!(model.getNode(name) instanceof FunctionalBedrockPart node))return;
        var original=node.functionalRenderer;
        if(original!=null && !(original instanceof HandFactory))node.functionalRenderer=new HandFactory(original,left);
    }
    private static final class HandFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final boolean left;
        HandFactory(Function<BedrockPart,IFunctionalRenderer> original,boolean left) {this.original=original;this.left=left;}
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            IFunctionalRenderer nativeHand=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                var mc=Minecraft.getInstance();
                boolean slim=mc!=null && mc.player!=null && "slim".equals(mc.player.getModelName());
                pose.pushPose();
                try {
                    if(slim)pose.translate(left?calibration.left_slim_x:calibration.right_slim_x,0,0);
                    nativeHand.render(pose,consumer,context,light,overlay);
                } finally {pose.popPose();}
            };
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov event) {
        if(event.usedConfiguredFov() || calibration.fitted_vertical_fov_degrees==null)return;
        var mc=Minecraft.getInstance();
        if(mc==null || mc.player==null || event.getCamera().getEntity()!=mc.player)return;
        var renderer=KeepingItemRenderer.getRenderer();if(renderer==null)return;
        var stack=renderer.getCurrentItem();if(stack==null)return;
        var gun=IGun.getIGunOrNull(stack);
        if(gun!=null && cn.ssjjdimension.DimensionScopes.family(gun.getGunId(stack)))event.setFOV(calibration.fitted_vertical_fov_degrees);
    }
}
