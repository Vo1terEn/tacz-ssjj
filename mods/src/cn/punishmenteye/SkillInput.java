package cn.punishmenteye;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class SkillInput {
    static final KeyMapping KEY=new KeyMapping("key.eyeofpunishment.skill",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.eyeofpunishment");
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(KEY);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();while(KEY.consumeClick())if(mc.screen==null&&mc.player!=null&&SkillEligibility.eligible(mc.player.getMainHandItem())&&!UltimateRequirement.installed(mc.player.getMainHandItem())){SkillActionClient.requested();SkillTimer.request();}}
}
