package cn.ssjjzeus.client;

import com.google.gson.Gson;
import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjzeus.ZeusReloadProtection;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.Map;
import java.util.LinkedHashMap;
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
public final class ZeusNativeHands {
    public static final class Calibration {
        public Double fitted_vertical_fov_degrees;
        public float left_slim_x, right_slim_x;
    }
    private static volatile Map<ResourceLocation,Calibration> calibrations=Map.of();
    private ZeusNativeHands() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerZeusHandCalibration(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusNativeHands::read);
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
        calibrations=Map.of();Map<ResourceLocation,Calibration> freshProfiles=new LinkedHashMap<>();
        for(ResourceLocation gun:ZeusReloadProtection.GUNS) {
            ResourceLocation resource=new ResourceLocation("ssjj_zeus",gun.equals(ZeusMeshRenderer.GUN)?"view_calibration.json":"view_calibration_"+gun.getPath()+".json");
            try(var stream=resources.open(resource);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                Calibration fresh=new Gson().fromJson(reader,Calibration.class);validate(fresh);freshProfiles.put(gun,fresh);
            } catch(Exception failure) {LogUtils.getLogger().warn("Cannot load Zeus view calibration "+gun+"; retaining native FOV and arm widths",failure);}
        }
        calibrations=Map.copyOf(freshProfiles);
    }
    public static void install(BedrockAnimatedModel model) {
        install(model,ZeusMeshRenderer.GUN);
    }
    public static void install(BedrockAnimatedModel model,ResourceLocation gun) {
        if(!ZeusReloadProtection.GUNS.contains(gun))throw new IllegalArgumentException("Unknown Zeus profile "+gun);
        install(model,"lefthand_pos",true,gun);install(model,"righthand_pos",false,gun);
    }
    private static void install(BedrockAnimatedModel model,String name,boolean left,ResourceLocation gun) {
        if(!(model.getNode(name) instanceof FunctionalBedrockPart node))return;
        var original=node.functionalRenderer;
        if(original!=null && !(original instanceof HandFactory))node.functionalRenderer=new HandFactory(original,left,gun);
    }
    private static final class HandFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final boolean left;
        private final ResourceLocation gun;
        HandFactory(Function<BedrockPart,IFunctionalRenderer> original,boolean left,ResourceLocation gun) {this.original=original;this.left=left;this.gun=gun;}
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            IFunctionalRenderer nativeHand=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                var mc=Minecraft.getInstance();
                boolean slim=mc!=null && mc.player!=null && "slim".equals(mc.player.getModelName());
                pose.pushPose();
                try {
                    Calibration calibration=calibrations.get(gun);
                    if(slim && calibration!=null)pose.translate(left?calibration.left_slim_x:calibration.right_slim_x,0,0);
                    nativeHand.render(pose,consumer,context,light,overlay);
                } finally {pose.popPose();}
            };
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov event) {
        if(event.usedConfiguredFov())return;
        var mc=Minecraft.getInstance();
        if(mc==null || mc.player==null || event.getCamera().getEntity()!=mc.player)return;
        var renderer=TaCZClientCompat.retainedRenderer();if(renderer==null)return;
        var stack=TaCZClientCompat.retainedItem(renderer);if(stack==null)return;
        var gun=IGun.getIGunOrNull(stack);
        ResourceLocation id=gun==null?null:gun.getGunId(stack);
        Calibration calibration=id==null?null:calibrations.get(id);
        if(calibration!=null && calibration.fitted_vertical_fov_degrees!=null)event.setFOV(calibration.fitted_vertical_fov_degrees);
    }
}
