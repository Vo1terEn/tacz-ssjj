package cn.ssjjdimension;

import com.google.common.collect.MapMaker;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** One outgoing-damage multiplier for native attacks and owner-attributed skills. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class OverloadDamage {
    private static final String SHOT="SSJJDimensionOverloadShot", V2="SSJJDimensionOverloadDamageV2";
    private static final UUID LEGACY_ATTRIBUTE=UUID.fromString("b6733532-68fb-40ca-9ec4-e94cd5750110");
    private static final Map<DamageSource,Double> SOURCES=new MapMaker().weakKeys().makeMap();
    private OverloadDamage() {}

    private static boolean valid(double value) {return Double.isFinite(value)&&value>0&&value<=1;}
    private static double bounded(double value) {
        return Math.max(DimensionDecoyRules.minimumDamage(),Math.min(1,value));
    }
    /** Old saves/active entities may still contain the former attribute modifier. */
    public static void clearAttribute(LivingEntity owner) {
        var attribute=owner.getAttribute(Attributes.ATTACK_DAMAGE);
        if(attribute!=null)attribute.removeModifier(LEGACY_ATTRIBUTE);
    }
    /** Persist only a multiplier; new arrow base damage and all item data stay intact. */
    public static void launched(Entity entity) {
        if(!(entity.level() instanceof ServerLevel)||!(entity instanceof Projectile projectile))return;
        var owner=projectile.getOwner() instanceof LivingEntity living?living:null;
        var tag=entity.getPersistentData();
        if(tag.getBoolean(V2))return;
        double saved=tag.contains(SHOT)?tag.getDouble(SHOT):Double.NaN;
        if(!valid(saved)&&owner==null)return;
        // Older versions pre-scaled arrow base damage before saving this tag.
        // Undo that one legacy operation before the new complete-impact handler.
        if(valid(saved)&&entity instanceof AbstractArrow arrow&&!(arrow instanceof ThrownTrident))
            arrow.setBaseDamage(arrow.getBaseDamage()/saved);
        double value=valid(saved)?saved:DimensionOverload.factor(owner);
        tag.putDouble(SHOT,bounded(value));tag.putBoolean(V2,true);
    }
    private static double projectileFactor(Entity entity,LivingEntity owner) {
        if(entity instanceof Projectile) {
            var tag=entity.getPersistentData();
            double value=tag.contains(SHOT)?tag.getDouble(SHOT):Double.NaN;
            if(valid(value))return bounded(value);
        }
        return DimensionOverload.factor(owner);
    }
    /** TaCZ may deliberately replace its projectile direct entity with the shooter. */
    public static void gun(EntityHurtByGunEvent.Pre event) {
        if(event.isCanceled()||event.getLogicalSide()!=LogicalSide.SERVER||event.getAttacker()==null)return;
        double value=projectileFactor(event.getBullet(),event.getAttacker());
        for(var part:GunDamageSourcePart.values()) {
            var source=event.getDamageSource(part);
            if(source!=null)SOURCES.put(source,value);
        }
    }
    /** Preserve the captured gun source when the death-message facade wraps it. */
    public static void forward(DamageSource original,DamageSource wrapped) {
        Double captured=SOURCES.get(original);
        if(captured!=null)SOURCES.put(wrapped,captured);
    }
    public static double factor(DamageSource source) {
        if(source==null)return 1;
        Double captured=SOURCES.get(source);
        if(captured!=null)return captured;
        // Saved projectiles keep their launch penalty even if the owner unloaded.
        if(source.getDirectEntity() instanceof Projectile projectile&&projectile.level() instanceof ServerLevel) {
            double saved=projectile.getPersistentData().getDouble(SHOT);
            if(valid(saved))return bounded(saved);
        }
        var owner=source.getEntity() instanceof LivingEntity living?living:
                source.getDirectEntity() instanceof Projectile projectile&&projectile.getOwner() instanceof LivingEntity living?living:null;
        if(owner==null||!(owner.level() instanceof ServerLevel))return 1;
        return projectileFactor(source.getDirectEntity(),owner);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void damage(LivingHurtEvent event) {
        float amount=event.getAmount();
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel)
                ||!(amount>0)||!Float.isFinite(amount))return;
        double value=factor(event.getSource());
        if(value<1)event.setAmount((float)(amount*value));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {SOURCES.clear();}
}
