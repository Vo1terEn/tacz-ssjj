package cn.fusionarsenal.launch;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.niofs.union.UnionFileSystem;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;

/** Presents this early SERVICE archive to Forge as a normal, filtered GAME mod. */
public final class SelfModLocator extends AbstractJarFileModProvider implements IModLocator {
    private static final String LAUNCH = "cn/fusionarsenal/launch/";
    private static final String TRANSFORM_SERVICE = "META-INF/services/cpw.mods.modlauncher.api.ITransformationService";
    private static final String LOCATOR_SERVICE = "META-INF/services/net.minecraftforge.forgespi.locating.IModLocator";
    private List<ModFileOrException> result;

    @Override public String name() { return "ssjjrpg_self"; }
    @Override public void initArguments(Map<String, ?> arguments) { }

    @Override public synchronized List<ModFileOrException> scanMods() {
        if (result != null) return result;
        try {
            Path archive = ownArchive();
            // Forge's metadata creates the ordinary GAME module name from
            // mods.toml. Its package-private constructor is open in the actual
            // Forge 47 fmlloader automatic module; do not invent another name.
            var constructor = ModJarMetadata.class.getDeclaredConstructor();
            if (!constructor.trySetAccessible())
                throw new IllegalStateException("Forge ModJarMetadata constructor is inaccessible");
            ModJarMetadata metadata = constructor.newInstance();
            SecureJar gameView = SecureJar.from(() -> ModFile.DEFAULTMANIFEST,
                ignored -> metadata, SelfModLocator::includeGameEntry, archive);
            if (gameView.moduleDataProvider().findFile("META-INF/mods.toml").isEmpty())
                throw new IllegalStateException("Self GAME view has no META-INF/mods.toml");
            ModFile file = new ModFile(gameView, this, ModFileParser::modsTomlParser);
            metadata.setModFile(file);
            result = List.of(new ModFileOrException(file, null));
            System.err.println("[SSJJRPG] Registered normal GAME mod view from " + archive.getFileName());
        } catch (Exception failure) {
            ModFileLoadingException error = new ModFileLoadingException(
                "Cannot register SSJJRPG GAME view: " + failure);
            error.initCause(failure);
            result = List.of(new ModFileOrException(null, error));
            System.err.println("[SSJJRPG] Self mod discovery failed: " + failure);
        }
        return result;
    }

    private static boolean includeGameEntry(String resource, String base) {
        String path = resource.replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        // The SERVICE descriptor and providers belong only to the parent layer.
        // Keeping the launch package out of GAME preserves its single cache.
        return !path.startsWith(LAUNCH)
            && !path.equals("module-info.class")
            && !(path.startsWith("META-INF/versions/") && path.endsWith("/module-info.class"))
            && !path.equals(TRANSFORM_SERVICE)
            && !path.equals(LOCATOR_SERVICE);
    }

    private static Path ownArchive() throws Exception {
        var source = SelfModLocator.class.getProtectionDomain().getCodeSource();
        if (source == null) throw new IllegalStateException("Self locator has no code source");
        URI location = source.getLocation().toURI();
        Path path = Path.of(location);
        // ModLauncher defines SERVICE classes from a union filesystem. The
        // public SecureJar filesystem API returns its real underlying archive.
        if (path.getFileSystem() instanceof UnionFileSystem union) path = union.getPrimaryPath();
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Self code source is not a JAR: " + path);
        return path;
    }
}
