package cn.fusionarsenal;

import cn.fusionarsenal.api.ModuleRegistry;
import cn.fusionarsenal.modules.BuiltinModules;
import java.util.List;
import java.util.Optional;
import net.minecraftforge.fml.common.Mod;

/** One Forge container; each original runtime retains its resource and protocol identities. */
@Mod(FusionArsenal.MOD_ID)
public final class FusionArsenal {
    public static final String MOD_ID = "eye_judgment_arsenal";
    private static final ModuleRegistry MODULES = BuiltinModules.create();

    public FusionArsenal() {
        MODULES.initialize();
    }

    public static List<ModuleRegistry.ModuleInfo> modules() { return MODULES.snapshot(); }
    public static Optional<ModuleRegistry.ModuleInfo> ownerOf(String gunId) { return MODULES.ownerOf(gunId); }
}
