package cn.ssjjparasite2.client;

/** Register all Parasite channels during the owning mod's construction stage. */
public final class ParasiteNetworkBootstrap {
    private ParasiteNetworkBootstrap() {}

    public static void init() {
        // Each owner publishes one fully configured static-final channel. Repeated
        // construction/setup calls only read those instances, never register again.
        ParasiteCannon.initNetwork();
        ParasiteEffectsNetwork.initNetwork();
        ParasiteShockwave.initNetwork();
    }
}
