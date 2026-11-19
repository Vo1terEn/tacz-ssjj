package cn.ssjjhyper.client;

import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjhyper.HyperReloadProtection;
import cn.ssjjzeus.client.ZeusNativeHands;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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

/** Wraps the existing native player-skin hand callbacks with this weapon's calibration. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperNativeHands {
    private static volatile Map<ResourceLocation,ZeusNativeHands.Calibration> calibrations=Map.of();
    private HyperNativeHands() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperHandCalibration(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)HyperNativeHands::read);
        }
    }
    public static void read(ResourceManager resources) {
        calibrations=Map.of();var loaded=new LinkedHashMap<ResourceLocation,ZeusNativeHands.Calibration>();
        for(ResourceLocation gun:HyperReloadProtection.GUNS) {
            var resource=new ResourceLocation("ssjj_hyper","view_calibration_"+gun.getPath()+".json");
            try(var stream=resources.open(resource);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var calibration=new Gson().fromJson(reader,ZeusNativeHands.Calibration.class);
                ZeusNativeHands.validate(calibration);loaded.put(gun,calibration);
            } catch(Exception failure) {LogUtils.getLogger().warn("Cannot load Hyper Revolver hand calibration "+gun+"; retaining native hand width and FOV",failure);}
        }
        calibrations=Map.copyOf(loaded);
    }
    public static void install(BedrockAnimatedModel model,ResourceLocation gun) {
        if(!HyperReloadProtection.family(gun))throw new IllegalArgumentException("Unknown Hyper Revolver hand profile "+gun);
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
        HandFactory(Function<BedrockPart,IFunctionalRenderer> original,boolean left,ResourceLocation gun) {
            this.original=original;this.left=left;this.gun=gun;
        }
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            var nativeHand=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                var mc=Minecraft.getInstance();var calibration=calibrations.get(gun);
                boolean slim=mc!=null && mc.player!=null && "slim".equals(mc.player.getModelName());
                pose.pushPose();
                try {
                    if(slim && calibration!=null)pose.translate(left?calibration.left_slim_x:calibration.right_slim_x,0,0);
                    nativeHand.render(pose,consumer,context,light,overlay);
                } finally {pose.popPose();}
            };
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov event) {
        if(event.usedConfiguredFov())return;
        var mc=Minecraft.getInstance();if(mc==null || mc.player==null || event.getCamera().getEntity()!=mc.player)return;
        var stack=TaCZClientCompat.currentRenderedItem();if(stack==null)return;
        var gun=IGun.getIGunOrNull(stack);var calibration=gun==null?null:calibrations.get(gun.getGunId(stack));
        if(calibration!=null && calibration.fitted_vertical_fov_degrees!=null)event.setFOV(calibration.fitted_vertical_fov_degrees);
    }
}
