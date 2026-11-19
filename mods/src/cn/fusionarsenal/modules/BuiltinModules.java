package cn.fusionarsenal.modules;

import cn.fusionarsenal.api.ModuleRegistry;

/** Explicit ordered module registration under the single main Forge container. */
public final class BuiltinModules {
    private BuiltinModules() {}
    public static ModuleRegistry create() {
        return new ModuleRegistry()
                .register(new JudgmentModule())
                .register(new PunishmentModule());
    }
}
