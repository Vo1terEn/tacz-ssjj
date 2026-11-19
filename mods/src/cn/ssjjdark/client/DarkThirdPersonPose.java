package cn.ssjjdark.client;

import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Vanilla two-handed outward hold; custom Ares/YSM locators stay independent. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DarkThirdPersonPose implements IThirdPersonAnimation {
    public static final String KEY="ssjj_dark_melee";
    private static final DarkThirdPersonPose INSTANCE=new DarkThirdPersonPose();
    private DarkThirdPersonPose() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->ThirdPersonManager.register(KEY,INSTANCE));
    }
    @Override public void animateGunHold(LivingEntity entity,ModelPart right,ModelPart left,ModelPart body,ModelPart head) {
        boolean leftMain=entity.getMainArm()==HumanoidArm.LEFT;
        ModelPart held=leftMain?left:right,assist=leftMain?right:left;
        // The standard locator is fitted to these finite Steve/Alex palms.
        // Keeping the hold relative to the body prevents a freely turning
        // head from sweeping the long blade through the head or helmet.
        held.xRot=-.80000f;
        held.yRot=leftMain?.89056f:-.89056f;
        held.zRot=leftMain?-.10864f:.10864f;
        assist.xRot=-.90000f;
        assist.yRot=leftMain?-1.32591f:1.32591f;
        assist.zRot=leftMain?.22051f:-.22051f;
    }
    @Override public void animateGunAim(LivingEntity entity,ModelPart right,ModelPart left,ModelPart body,ModelPart head,float progress) {
        animateGunHold(entity,right,left,body,head);
    }
}
