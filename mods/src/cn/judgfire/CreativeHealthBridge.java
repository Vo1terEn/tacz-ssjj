package cn.judgfire;

import cn.ssjjdark.DarkLiberationGuard;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Exact vanilla health storage bridge; never searches an entity's arbitrary numeric fields. */
public final class CreativeHealthBridge {
    private static final Method ITEM = itemMethod();
    private static final Field PLAYER_TIME = field("lastHurtByPlayerTime", "f_20889_");
    private static final Field LAST_SOURCE = field("lastDamageSource", "f_20958_");
    private static final Field LAST_STAMP = field("lastDamageStamp", "f_20930_");
    private static final Method TOTEM = totemMethod();
    private static final Field DEAD = field("dead", "f_20890_");
    private static final MethodHandle NATIVE_DIE = special("die", "m_6667_", MethodType.methodType(void.class,DamageSource.class));
    private static final MethodHandle NATIVE_TICK = special("tickDeath", "m_6153_", MethodType.methodType(void.class));
    private CreativeHealthBridge() {}

    private static MethodHandle special(String named,String runtime,MethodType type){
        try {
            var lookup=MethodHandles.privateLookupIn(LivingEntity.class,MethodHandles.lookup());
            for(String name:new String[]{named,runtime})try{return lookup.findSpecial(LivingEntity.class,name,type,LivingEntity.class);}
            catch(NoSuchMethodException ignored){}
        }catch(IllegalAccessException e){throw new IllegalStateException("Cannot access native death flow",e);}
        throw new IllegalStateException("Missing native death method: "+named);
    }
    public static boolean settled(LivingEntity target){
        try{return DEAD.getBoolean(target);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public static void markSettled(LivingEntity target){
        try{DEAD.setBoolean(target,true);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public static void nativeDie(LivingEntity target,DamageSource source){
        try{NATIVE_DIE.invoke(target,source);}catch(Throwable e){throw new IllegalStateException("Native death settlement failed",e);}
    }
    public static void nativeDeathTick(LivingEntity target){
        try{NATIVE_TICK.invoke(target);}catch(Throwable e){throw new IllegalStateException("Native death tick failed",e);}
    }

    private static Field field(String... names) {
        for (String name : names) try {
            Field result = LivingEntity.class.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException ignored) {}
        throw new IllegalStateException("Unsupported native combat storage: " + names[0]);
    }

    private static Method totemMethod() {
        for (String name : new String[]{"checkTotemDeathProtection", "m_21262_"}) try {
            Method result = LivingEntity.class.getDeclaredMethod(name, DamageSource.class);
            result.setAccessible(true);
            return result;
        } catch (NoSuchMethodException ignored) {}
        throw new IllegalStateException("Unsupported native death protection");
    }

    public static void remember(LivingEntity target, DamageSource source) {
        try {
            if (source.getEntity() instanceof LivingEntity owner) {
                target.setLastHurtByMob(owner);
                if (owner instanceof Player player) {
                    target.setLastHurtByPlayer(player);
                    PLAYER_TIME.setInt(target, 100);
                }
            }
            LAST_SOURCE.set(target, source);
            LAST_STAMP.setLong(target, target.level().getGameTime());
        } catch (IllegalAccessException e) { throw new IllegalStateException("Cannot record native attack", e); }
    }

    public static boolean totem(LivingEntity target, DamageSource source) {
        try { return (boolean)TOTEM.invoke(target, source); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot settle native death protection", e); }
    }

    private static Method itemMethod() {
        for (Method method : SynchedEntityData.class.getDeclaredMethods()) {
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == EntityDataAccessor.class
                && method.getReturnType() == SynchedEntityData.DataItem.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Missing vanilla entity-data lookup");
    }

    public static float read(LivingEntity target) {
        var key = BarrierHealth.key();
        if (key == null || !target.getEntityData().hasItem(key)) return Float.NaN;
        return target.getEntityData().get(key);
    }

    public static void write(LivingEntity target, float value) {
        value = DarkLiberationGuard.health(target, value);
        var key = BarrierHealth.key();
        if (key == null || !target.getEntityData().hasItem(key))
            throw new IllegalStateException("Vanilla health storage was not registered");
        try {
            Object item = ITEM.invoke(target.getEntityData(), key);
            if (!BarrierStorage.write(target.getEntityData(), item, value))
                throw new IllegalStateException("Health commit failed");
            BarrierHealth.mirrored(target, item, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access vanilla health item", e);
        }
        target.onSyncedDataUpdated(key);
    }
}
