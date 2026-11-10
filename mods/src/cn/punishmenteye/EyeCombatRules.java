package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Stage scaling uses the weapon that dealt the hit, rather than inventory's highest stage. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class EyeCombatRules {
    private static final ResourceLocation GRIP = new ResourceLocation("eyeofpunishment:punishment_power");
    private static final java.util.Map<Entity,Boolean> SHOT_ENHANCED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private EyeCombatRules() {}

    public static int tier(ResourceLocation id) {
        if(id==null||!id.getNamespace().equals("eyeofpunishment"))return -1;
        return switch(id.getPath()){case "notdistinguish_base"->0;case "notdistinguish_tier1"->1;case "notdistinguish"->2;default->-1;};
    }
    public static float stage(ResourceLocation id){int t=tier(id);return t<0?0:(float)ProgressionConfig.lightningScale(t);}
    public static boolean hasPassivePower(ItemStack stack){return ProgressionConfig.enabled("eye.passive_enabled")&&isEye(stack);}
    private static float gazeBonus(ResourceLocation id,boolean enhanced){int t=tier(id);return !ProgressionConfig.enabled("eye.passive_enabled")||t<0?0:(float)(ProgressionConfig.gazeBonus(t)*(enhanced?ProgressionConfig.number("eye.grip_gaze_multiplier"):1));}
    public static boolean isEye(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IGun gun && tier(gun.getGunId(stack)) >= 0;
    }

    public static boolean enhanced(ResourceLocation hitGun, ItemStack stack) {
        return isEye(stack) && stack.getItem() instanceof IGun gun
            && gun.getGunId(stack).equals(hitGun) && GRIP.equals(gun.getAttachmentId(stack, AttachmentType.GRIP));
    }

    public static int speedLevel(Player player) {
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            var stack=player.getInventory().getItem(i);
            if(isEye(stack)&&enhanced(((IGun)stack.getItem()).getGunId(stack),stack))return ProgressionConfig.integer("eye.grip_speed_level");
        }
        return ProgressionConfig.integer("eye.speed_level");
    }
    public static int speedAmplifier(Player player){return Math.max(0,speedLevel(player)-1);}

    public static void refreshSpeed(ServerPlayer player) {
        int level=speedLevel(player);
        if(!ProgressionConfig.enabled("eye.passive_enabled")||level<=0)return;
        int amplifier=level-1;
        MobEffectInstance existing = player.getEffect(MobEffects.MOVEMENT_SPEED);
        // Let Minecraft preserve hidden/longer external effects and never downgrade stronger buffs.
        if (existing == null || existing.getAmplifier() < amplifier
                || (existing.getAmplifier() == amplifier && existing.getDuration() <= 20)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, amplifier, true, false, true));
        }
    }

    public static float gazeMultiplier(ResourceLocation hitGun, ItemStack stack) {
        return 1f + gazeBonus(hitGun,enhanced(hitGun, stack));
    }

    public static float gazeMultiplier(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IGun gun ? gazeMultiplier(gun.getGunId(stack), stack) : 1f;
    }

    public static float gazeDamage(float amount, boolean marked, EntityHurtByGunEvent.Pre event) {
        if (!marked) return amount;
        Boolean captured = SHOT_ENHANCED.get(event.getBullet());
        boolean grip = captured != null ? captured : enhanced(event.getGunId(), event.getAttacker().getMainHandItem());
        return amount * (1f + gazeBonus(event.getGunId(),grip));
    }

    /** Retain the fired gun's grip state if the player switches equipment before projectile impact. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void captureShot(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityKineticBullet bullet)
                || tier(bullet.getGunId()) < 0 || !(bullet.getOwner() instanceof Player owner)) return;
        SHOT_ENHANCED.put(bullet, enhanced(bullet.getGunId(), owner.getMainHandItem()));
    }

    public static float scaleLightning(float amount, ItemStack stack) {
        if (stack != null && stack.getItem() instanceof IGun gun) {
            float factor = stage(gun.getGunId(stack));
            if (tier(gun.getGunId(stack)) >= 0) return amount * factor;
        }
        return amount;
    }
}
