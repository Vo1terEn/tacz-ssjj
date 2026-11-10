package ssjj.dark;

import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** CPU preparation is covered by Minecraft's loading/reload completion barrier.
 * No GL calls or mutable renderer state are touched by the preparation worker. */
@Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DarkPerformanceReload {
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, resources, preparationProfiler, applyProfiler, preparationExecutor, applyExecutor) ->
            CompletableFuture.supplyAsync(() -> DarkRuntimeAssets.decode(name -> resources.m_215595_(
                new ResourceLocation(DarkCrystalMod.ID, "runtime/" + name))), preparationExecutor)
                .thenCompose(barrier::m_6769_)
                .thenComposeAsync(data -> {
                    var textures = new LinkedHashSet<ResourceLocation>();
                    for (var material : data.preparedMaterials)
                        for (String texture : material.textures()) textures.add(new ResourceLocation(texture));
                    var manager = Minecraft.m_91087_().m_91097_();
                    CompletableFuture<?>[] preloads = textures.stream()
                        .map(texture -> manager.m_118501_(texture, preparationExecutor)).toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(preloads).thenRunAsync(() -> {
                        DarkPresentation.invalidateRuntimeCaches();
                        DarkRuntimeAssets.publish(data);
                    }, applyExecutor);
                }, applyExecutor));
    }
    private DarkPerformanceReload() {}
}
