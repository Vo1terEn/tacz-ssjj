package cn.ssjjcompat;

import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;

/** The TaCZ 1.2 sound result changed type; preserve each version's native playback. */
public final class TaCZSounds {
    private static final MethodHandle SOUNDS;
    private static final MethodHandle PLAY;
    static {
        try {
            var lookup=MethodHandles.publicLookup();
            var sounds=GunDisplayInstance.class.getMethod("getSounds",String.class);
            SOUNDS=lookup.unreflect(sounds).asType(MethodType.methodType(Object.class,GunDisplayInstance.class,String.class));
            var play=SoundPlayManager.class.getMethod("playClientSound",Entity.class,sounds.getReturnType(),float.class,float.class,int.class);
            PLAY=lookup.unreflect(play).asType(MethodType.methodType(GunSoundInstance.class,Entity.class,Object.class,float.class,float.class,int.class));
        } catch(ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
    private TaCZSounds() {}

    public static Object getSounds(GunDisplayInstance display,String key) {
        try { return (Object)SOUNDS.invokeExact(display,key); }
        catch(RuntimeException|Error failure) { throw failure; }
        catch(Throwable failure) { throw new IllegalStateException("Cannot resolve TaCZ sound",failure); }
    }

    public static GunSoundInstance playClientSound(Entity entity,Object sound,float volume,float pitch,int distance) {
        try { return (GunSoundInstance)PLAY.invokeExact(entity,sound,volume,pitch,distance); }
        catch(RuntimeException|Error failure) { throw failure; }
        catch(Throwable failure) { throw new IllegalStateException("Cannot play TaCZ sound",failure); }
    }
}
