package cn.punishmenteye;

import java.lang.reflect.Field;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Eye's source clip supplies recoil; expire only TaCZ's extra shot sway. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class EyeForeAftRecoil {
    private static final Field SHOT_TIMESTAMP = shotTimestamp();

    private static Field shotTimestamp() {
        try {
            Field field=FirstPersonRenderGunEvent.class.getDeclaredField("shootTimeStamp");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot locate TaCZ shooting-sway timestamp",error);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onGunFire(GunFireEvent event) {
        if (event.isCanceled() || !event.getLogicalSide().isClient()) return;
        var player=Minecraft.getInstance().player;
        if (player==null || event.getShooter()!=player) return;
        var stack=event.getGunItemStack();
        var gun=IGun.getIGunOrNull(stack);
        if (gun==null) return;
        var id=gun.getGunId(stack);
        if (id==null || !id.getNamespace().equals("eyeofpunishment")) return;
        String path=id.getPath();
        if (!path.equals("notdistinguish") && !path.equals("notdistinguish_base") && !path.equals("notdistinguish_tier1")) return;
        // TaCZ's NORMAL listener has already emitted the muzzle flash and set
        // this timestamp. Expiring its 300 ms envelope leaves all other motion.
        try { SHOT_TIMESTAMP.setLong(null,-1L); }
        catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot expire Eye shooting sway",error);
        }
    }
}
