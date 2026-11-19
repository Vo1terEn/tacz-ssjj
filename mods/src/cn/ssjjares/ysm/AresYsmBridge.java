package cn.ssjjares.ysm;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

/** Optional, version-checked bridge to the methods verified in the installed YSM 2.6.5. */
public final class AresYsmBridge {
    private static final String MOD_ID = "yes_steve_model";
    private static final String SUPPORTED_VERSION = "2.6.5";
    private static final String PREFIX="com.elfmcys.yesstevemodel.";
    private static final String A="Oo0Oo0o00O00Oo0OOoOOoooo";
    private static volatile boolean initialized;
    private static boolean reported;
    private static Binding binding;
    private AresYsmBridge(){}

    public static Binding binding() {
        if (!initialized) initialize();
        return binding;
    }

    private static synchronized void initialize() {
        if (initialized) return;
        var list = ModList.get();
        if (list == null) return;
        var mod = list.getModContainerById(MOD_ID);
        if (mod.isPresent()) {
            String version = mod.get().getModInfo().getVersion().toString();
            if (version.equals(SUPPORTED_VERSION) || version.startsWith(SUPPORTED_VERSION + "-")) {
                try {
                    binding = new Binding();
                } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                    warn("YSM appearance adapter unavailable", failure);
                }
            } else {
                warn("Unsupported Yes Steve Model version " + version, null);
            }
        }
        initialized = true;
    }

    static synchronized void warn(String message, Throwable failure) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("ARES_YSM " + message, failure);
    }
    public static final class Binding {
        private final Capability<Object> capability;
        private final Method available,registered,remotePresent,model,texture,disabled,mandatory,select,setDisabled,setMandatory;
        @SuppressWarnings("unchecked")
        public Binding() throws ReflectiveOperationException {
            var loader=AresYsmBridge.class.getClassLoader();
            var provider=Class.forName(PREFIX+"Oo00o0OooOOo0ooOoo0oO0o0",true,loader);
            capability=(Capability<Object>)provider.getField(A).get(null);
            var data=Class.forName(PREFIX+"oooooooOOoOOoO00OooOo00O",false,loader);
            available=Class.forName(PREFIX+"YesSteveModel",false,loader).getMethod("isAvailable");
            registered=Class.forName(PREFIX+"OoOoOoooO0O00oOoO00OOo00",false,loader).getMethod(A,String.class);
            remotePresent=Class.forName(PREFIX+"OO00OoOOOOooO0ooOoOoOooO",false,loader).getMethod(A,ServerPlayer.class);
            model=data.getMethod("o0OOooo0o0OO00OoOOOo0o0O");texture=data.getMethod("O00OOOooOoooOoo0o0o0oO0O");
            disabled=data.getMethod("Ooooo0oooO0oooOOOoO0000O");mandatory=data.getMethod("o0OOO0o0o0OOo000oO00o00O");
            select=data.getMethod(A,String.class,String.class);setDisabled=data.getMethod(A,boolean.class);
            setMandatory=data.getMethod("o0OOooo0o0OO00OoOOOo0o0O",boolean.class);
        }
        public boolean available() throws ReflectiveOperationException{return (boolean)available.invoke(null);}
        public boolean registered(String id) throws ReflectiveOperationException{return ((Optional<?>)registered.invoke(null,id)).isPresent();}
        public boolean remotePresent(ServerPlayer player) throws ReflectiveOperationException{return (boolean)remotePresent.invoke(null,player);}
        public AresYsmSelection.ModelAccess player(Player player){return player.getCapability(capability).resolve().map(this::access).orElse(null);}
        public AresYsmSelection.ModelAccess access(Object data){return new AresYsmSelection.ModelAccess(){
            public String model() throws ReflectiveOperationException{return (String)model.invoke(data);}
            public String texture() throws ReflectiveOperationException{return (String)texture.invoke(data);}
            public boolean disabled() throws ReflectiveOperationException{return (boolean)disabled.invoke(data);}
            public boolean mandatory() throws ReflectiveOperationException{return (boolean)mandatory.invoke(data);}
            public void select(String id,String tex) throws ReflectiveOperationException{select.invoke(data,id,tex);}
            public void disabled(boolean value) throws ReflectiveOperationException{setDisabled.invoke(data,value);}
            public void mandatory(boolean value) throws ReflectiveOperationException{setMandatory.invoke(data,value);}
        };}
    }
}
