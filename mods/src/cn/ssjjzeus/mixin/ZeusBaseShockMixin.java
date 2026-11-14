package cn.ssjjzeus.mixin;

import cn.ssjjzeus.ZeusShock;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep every native base-weapon pellet, including the native ITargetEntity early-return branch. */
@Mixin(value=EntityKineticBullet.class,remap=false)
public abstract class ZeusBaseShockMixin {
    @Redirect(method="onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
        at=@At(value="INVOKE",target="Lcom/tacz/guns/entity/EntityKineticBullet;getDamage(Lnet/minecraft/world/phys/Vec3;)F",remap=false),
        require=2,expect=2,allow=2,remap=false)
    private float ssjjZeus$baseShockDamage(EntityKineticBullet bullet,Vec3 point,TacHitResult hit,Vec3 start,Vec3 end) {
        float raw=bullet.getDamage(point);
        if(!"ssjj_zeus:hand_of_zeuss".equals(String.valueOf(bullet.getGunId())))return raw;
        return raw*ZeusShock.multiplier(hit.getEntity(),bullet.getGunId(),false);
    }
}
