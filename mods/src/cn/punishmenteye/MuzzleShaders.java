package cn.punishmenteye;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Explicit shader blend descriptors avoid ShaderInstance overriding the source material blend. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MuzzleShaders {
 public static ShaderInstance alpha,additive;
 @SubscribeEvent public static void register(RegisterShadersEvent event)throws java.io.IOException{
  event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("eyeofpunishment","muzzle_alpha"),DefaultVertexFormat.NEW_ENTITY),s->alpha=s);
  event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("eyeofpunishment","muzzle_additive"),DefaultVertexFormat.NEW_ENTITY),s->additive=s);
 }
}
