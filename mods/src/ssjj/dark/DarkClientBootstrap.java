package ssjj.dark;
/** Explicit constructor-time client event wiring; no dependency on nested subscriber scanning. */
public final class DarkClientBootstrap {
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
            net.minecraftforge.client.event.RegisterShadersEvent.class, event -> {
                try { DarkBladePass.Shaders.register(event); }
                catch (java.io.IOException error) { throw new IllegalStateException("Cannot load blade shaders", error); }
            });
        com.mojang.logging.LogUtils.getLogger().info("[DarkBladeFX] Shader listener wired to ssjj_dark mod bus");
    }
    private DarkClientBootstrap() {}
}
