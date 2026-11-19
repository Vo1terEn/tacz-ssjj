package cn.fusionarsenal;

import net.minecraft.world.entity.LivingEntity;

/** Natural Dark Crystal expiry may restore a living pool after a nonlethal
 * creative hit. A settled death is never revived by this recovery. */
public final class DarkCrystalRecovery {
    private DarkCrystalRecovery(){}
    public static void restore(LivingEntity target,float health){
        if(target==null||!Float.isFinite(health)||health<=0||CreativeHealthState.terminal(target)||!target.isAlive())return;
        if(CreativeHealthState.state(target)!=null)CreativeHealthState.revive(target,health);
        else target.setHealth(health);
    }
}
