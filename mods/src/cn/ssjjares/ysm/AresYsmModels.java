package cn.ssjjares.ysm;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipInputStream;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

/** Prepare the bundled models before the integrated or dedicated server reads the YSM catalog. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AresYsmModels {
    private AresYsmModels() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (!ModList.get().isLoaded("yes_steve_model")) return;
        Path root = FMLPaths.CONFIGDIR.get().resolve("yes_steve_model");
        install(root, AresYsmSelection.BASE);
        install(root, AresYsmSelection.ABSOLUTE);
    }

    private static boolean hasModel(Path directory, String id) {
        for (String suffix : new String[] {"", ".zip", ".ysm"}) {
            if (Files.exists(directory.resolve(id + suffix), LinkOption.NOFOLLOW_LINKS)) return true;
        }
        return false;
    }

    private static void install(Path root, String id) {
        Path custom = root.resolve("custom");
        try {
            if (hasModel(custom, id) || hasModel(root.resolve("auth"), id)) return;
            Files.createDirectories(custom);
            // Stage outside the scanned directories so YSM only sees complete models.
            Path staged = Files.createTempDirectory(root, "ssjj-ares-");
            try {
                extract(id, staged);
                if (hasModel(custom, id) || hasModel(root.resolve("auth"), id)) return;
                try {
                    publish(staged, custom.resolve(id));
                    LogUtils.getLogger().info("[SSJJRPG] Installed bundled YSM model {}", id);
                } catch (FileAlreadyExistsException alreadyInstalled) {
                    // Another startup published the same model during staging.
                }
            } finally {
                if (Files.exists(staged)) {
                    try (var files = Files.walk(staged)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                    }
                }
            }
        } catch (IOException | SecurityException failure) {
            LogUtils.getLogger().warn("[SSJJRPG] Could not install bundled YSM model {}", id, failure);
        }
    }

    private static void publish(Path staged, Path destination) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(staged, destination);
                return;
            } catch (AccessDeniedException busy) {
                if (attempt == 5) throw busy;
                // Windows file scanners can briefly hold newly extracted assets open.
                try {
                    Thread.sleep(50L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw busy;
                }
            }
        }
    }

    private static void extract(String id, Path staged) throws IOException {
        var model = AresYsmModels.class.getResourceAsStream("/ssjjrpg/ysm/" + id + ".zip");
        if (model == null) throw new IOException("Missing bundled model " + id);
        try (var archive = new ZipInputStream(model)) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                Path path = staged.resolve(entry.getName()).normalize();
                if (!path.startsWith(staged)) throw new IOException("Invalid model asset path " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    Files.createDirectories(path.getParent());
                    Files.copy(archive, path);
                }
            }
        }
        if (!Files.isRegularFile(staged.resolve("ysm.json"))) throw new IOException("Missing ysm.json for " + id);
    }
}
