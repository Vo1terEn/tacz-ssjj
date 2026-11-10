package cn.fusionarsenal.modules;

import cn.fusionarsenal.api.WeaponModule;
import java.util.Set;

/** The selected RPG runtime keeps every original public API, registry and channel ID. */
public final class JudgmentModule implements WeaponModule {
    public String id() { return "judgfire:judgment_flame"; }
    public Set<String> gunIds() { return Set.of("judgfire:judgment_flame"); }
    public void initialize() { new cn.judgfire.JudgmentFlame(); }
}
