package cn.ssjjdark;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Narrow guards for the pack's known direct-health commit APIs, not a global Unsafe hook. */
public final class DarkLiberationGuard {
    private DarkLiberationGuard(){}

    /** No health getter here: direct commit hooks can run underneath health getter hooks. */
    public static boolean blocked(LivingEntity target){
        return target instanceof ServerPlayer player&&!DarkLiberationBypass.allowed(target)
            &&(DarkLiberation.invulnerable(player)||DarkLiberation.healthLocked(player));
    }

    /**
     * Clamp a known raw-commit API parameter to the authoritative cached HP while
     * invulnerable. The ordinary restore flag deliberately grants no exemption.
     * The explicit bypass records its permitted lower HP so later guards cannot
     * restore health removed by a trusted write.
     */
    public static float health(LivingEntity target,float proposed){
        if(!(target instanceof ServerPlayer player))return proposed;
        if(DarkLiberationBypass.allowed(target)){
            DarkLiberation.allowBypassHealth(player,proposed);
            return proposed;
        }
        return blocked(target)?DarkLiberation.protectedHealth(player):proposed;
    }
}
