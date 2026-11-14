package cn.fusionarsenal.modules;

import cn.fusionarsenal.api.WeaponModule;
import java.util.Set;

/** Dark crystal shares the main module lifecycle and retains its saved resource IDs. */
public final class DarkCrystalModule implements WeaponModule {
    public String id() { return "ssjj_dark:darkdiamond"; }
    public Set<String> gunIds() {
        return Set.of("ssjj_dark:darkdiamond", "ssjj_dark:darkdiamond1", "ssjj_dark:darkdiamond2");
    }
    public void initialize() { new ssjj.dark.DarkCrystalMod(); }
}
