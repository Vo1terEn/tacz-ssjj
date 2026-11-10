package cn.fusionarsenal.modules;

import cn.fusionarsenal.api.ModuleRegistry;

/** Add one explicit register call here when adding another WeaponModule; no runtime scanning. */
public final class BuiltinModules {
    private BuiltinModules() {}
    public static ModuleRegistry create() {
        return new ModuleRegistry()
                .register(new JudgmentModule())
                .register(new PunishmentModule());
    }
}
