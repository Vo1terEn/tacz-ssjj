package ssjj.dark;

/** 只存网络状态值，不引用客户端类，专用服务器可安全加载网络注册代码。 */
public final class DarkClientState {
    public static volatile DarkNetwork.State state = new DarkNetwork.State(0, 0, 0, 1, 1, 0, 0);
    public static void accept(DarkNetwork.State next) { state = next; }
    public static void tick() {
        DarkNetwork.State s=state;
        if (s.charge()==0 && s.active()==0 && s.cooldown()==0) return;
        state=new DarkNetwork.State(Math.max(0,s.charge()-1),Math.max(0,s.active()-1),
            Math.max(0,s.cooldown()-1),s.maxActive(),s.maxCooldown(),s.shield(),s.shieldMax());
    }
    private DarkClientState() {}
}
