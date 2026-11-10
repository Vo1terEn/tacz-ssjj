package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;

import net.minecraft.resources.ResourceLocation;

/** The electric ball and its resulting cloud are exclusive to the original second-tier gun. */
public final class NofaTierPolicy {
    private NofaTierPolicy() {}

    public static boolean spawnBall(int counter, ResourceLocation gunId) {
        int tier=EyeCombatRules.tier(gunId);
        return counter==0&&ProgressionConfig.ballAllowed(tier);
    }
}
