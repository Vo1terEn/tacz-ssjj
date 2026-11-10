package cn.fusionarsenal.modules;

import cn.fusionarsenal.api.WeaponModule;
import java.util.Set;

/** Skins remain display selectors of the one original gun identity. */
public final class PunishmentModule implements WeaponModule {
    public String id() { return "eyeofpunishment:notdistinguish"; }
    public Set<String> gunIds() { return Set.of("eyeofpunishment:notdistinguish"); }
    public void initialize() { new cn.punishmenteye.EyeCrosshair(); }
}
