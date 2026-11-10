package cn.punishmenteye;

import net.minecraft.resources.ResourceLocation;

/** Exact weapon and installed attachment identity; loose inventory grips never grant power. */
public final class GripRequirement {
    public static final ResourceLocation WEAPON=new ResourceLocation("eyeofpunishment","notdistinguish");
    public static final ResourceLocation POWER=new ResourceLocation("eyeofpunishment","punishment_power");
    public static boolean matches(ResourceLocation gun,ResourceLocation installedGrip){
        return WEAPON.equals(gun)&&POWER.equals(installedGrip);
    }
}
