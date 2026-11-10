package cn.ssjjdimension;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import cn.ssjjparasite2.client.ParasiteDummyDamage;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Server authority; no gun damage events are emitted by the mark detonation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionMarks {
    private static final Map<LivingEntity,List<DimensionMarkRules.Mark>> MARKS=new WeakHashMap<>();
    private static final Map<EntityKineticBullet,Map<UUID,Hit>> SHOTS=new WeakHashMap<>();
    private static final Map<EntityKineticBullet,String> SHOT_PROFILES=new WeakHashMap<>();
    private static final class Hit {
        final ParasiteDummyDamage.Snapshot before;
        boolean done;
        final String profile;
        final DimensionAttachments.Snapshot equipment;
        final float abilityFactor;
        Hit(LivingEntity t,String profile,DimensionAttachments.Snapshot equipment,float abilityFactor) { before=ParasiteDummyDamage.capture(t);this.profile=profile;this.equipment=equipment;this.abilityFactor=abilityFactor; }
    }
    private DimensionMarks() {}
    private static LivingEntity core(Entity e) {
        if(e==null)return null;
        return EntityKineticBullet.MaybeMultipartEntity.of(e).core() instanceof LivingEntity t ? t : null;
    }
    private static int seconds(EntityHurtByGunEvent e) {
        var id=e.getGunId();
        return id==null?0:DimensionMarkRules.seconds(id.getNamespace(),id.getPath());
    }
    private static String bulletProfile(EntityKineticBullet bullet) {
        var id=bullet.getGunId();int seconds=id==null?0:DimensionMarkRules.seconds(id.getNamespace(),id.getPath());
        if(seconds==0)return "tier2";
        String skin="";var display=bullet.getGunDisplayId();
        if(display!=null&&"ssjj_dimension".equals(display.getNamespace()))for(String candidate:DimensionVisualScope.SKINS) {
            String name=id.getPath()+(candidate.isEmpty()?"":"_"+candidate)+"_display";
            if(name.equals(display.getPath())){skin=candidate;break;}
        }
        return DimensionVisualScope.profile(seconds-1,skin);
    }
    /** Snapshot the installed scope/skin before an in-flight shot can outlive a weapon switch. */
    @SubscribeEvent public static void launch(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if(!(e.getLevel() instanceof ServerLevel)||!(e.getEntity() instanceof EntityKineticBullet bullet))return;
        var id=bullet.getGunId();if(id==null||DimensionMarkRules.seconds(id.getNamespace(),id.getPath())==0)return;
        DimensionAttachments.launch(e);
        String saved=bullet.getPersistentData().getString("SSJJDimensionMarkProfile");
        if(DimensionMarkNetwork.profile(saved)){SHOT_PROFILES.putIfAbsent(bullet,saved);return;}
        String profile=bulletProfile(bullet);var owner=bullet.getOwner();
        if(owner instanceof LivingEntity shooter) {
            var stack=shooter.getMainHandItem();var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if(gun!=null&&id.equals(gun.getGunId(stack))&&DimensionVisualScope.accepts(stack))
                profile=DimensionVisualScope.profile(DimensionMarkRules.seconds(id.getNamespace(),id.getPath())-1,DimensionVisualScope.skin(stack));
        }
        SHOT_PROFILES.putIfAbsent(bullet,profile);bullet.getPersistentData().putString("SSJJDimensionMarkProfile",profile);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void before(EntityHurtByGunEvent.Pre e) {
        if(e.isCanceled() || e.getLogicalSide()!=LogicalSide.SERVER || seconds(e)==0
            || !(e.getBullet() instanceof EntityKineticBullet b) || !e.getGunId().equals(b.getGunId())
            || e.getAttacker()==null || !Float.isFinite(e.getBaseAmount()) || e.getBaseAmount()<=0)return;
        var t=core(e.getHurtEntity());
        if(t==null || t==e.getAttacker() || !t.isAlive() || !(t.level() instanceof ServerLevel))return;
        SHOTS.computeIfAbsent(b,k->new HashMap<>()).computeIfAbsent(t.getUUID(),k->new Hit(t,SHOT_PROFILES.getOrDefault(b,bulletProfile(b)),DimensionAttachments.shot(b),DimensionAttachments.gunBoost(e)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void after(EntityHurtByGunEvent.Post e) {
        if(e.getLogicalSide()!=LogicalSide.SERVER || seconds(e)==0 || e.getAttacker()==null
            || !(e.getBullet() instanceof EntityKineticBullet b))return;
        var t=core(e.getHurtEntity());if(t==null || !(t.level() instanceof ServerLevel level))return;
        var shots=SHOTS.get(b);var hit=shots==null?null:shots.get(t.getUUID());
        if(hit==null || hit.done)return;
        hit.done=true;
        // Infinite-health training dummies record accepted damage without lowering HP.
        // This optional helper uses that exact known counter, retaining normal HP/absorption
        // confirmation for every other target and failing closed on rejected damage.
        if(!t.isAlive() || !(ParasiteDummyDamage.confirmedLoss(hit.before,t)>0))return;
        // TaCZ passes the already headshot-adjusted native attack value as Post.baseAmount.
        // Post.getAmount() multiplies by the headshot multiplier again, even for body hits.
        if(DimensionMarkRules.add(MARKS.computeIfAbsent(t,k->new ArrayList<>()),e.getAttacker().getUUID(),
            e.getBaseAmount()/hit.abilityFactor,level.getGameTime(),DimensionConfig.markSeconds(seconds(e)-1)*(hit.equipment.muzzle()?DimensionConfig.integer("muzzle_mark_duration_multiplier"):1),hit.profile,hit.equipment))DimensionMarkNetwork.state(t,profiles(t));
    }
    public static int count(LivingEntity t) { var marks=MARKS.get(t);return marks==null?0:marks.size(); }
    public static List<String> profiles(LivingEntity target) { var marks=MARKS.get(target);return marks==null?List.of():marks.stream().map(DimensionMarkRules.Mark::profile).toList(); }
    /** Fields fill only missing layers; existing owners, damage and pulse timers are retained. */
    public static void fillToFive(LivingEntity target,LivingEntity shooter,float damage,int seconds) {
        fillToFive(target,shooter,damage,seconds,shooter==null?"":DimensionVisualScope.skin(shooter.getMainHandItem()));
    }
    public static void fillToFive(LivingEntity target,LivingEntity shooter,float damage,int seconds,String skin) {
        fillToFive(target,shooter,damage,seconds,skin,shooter==null?DimensionAttachments.NONE:DimensionAttachments.capture(shooter.getMainHandItem()));
    }
    public static void fillToFive(LivingEntity target,LivingEntity shooter,float damage,int seconds,String skin,DimensionAttachments.Snapshot equipment) {
        if(target==null||shooter==null||target==shooter||!target.isAlive()||!shooter.isAlive()
            ||target.level()!=shooter.level()||!Float.isFinite(damage)||damage<=0||seconds<1||seconds>3
            ||!(target.level() instanceof ServerLevel level))return;
        var marks=MARKS.computeIfAbsent(target,k->new ArrayList<>());boolean changed=false;
        while(marks.size()<DimensionMarkRules.cap()&&DimensionMarkRules.add(marks,shooter.getUUID(),damage,level.getGameTime(),DimensionConfig.markSeconds(seconds-1)*(equipment.muzzle()?DimensionConfig.integer("muzzle_mark_duration_multiplier"):1),DimensionVisualScope.profile(seconds-1,DimensionVisualScope.isSkin(skin)?skin:""),equipment))changed=true;
        if(changed)DimensionMarkNetwork.state(target,profiles(target));
    }
    static boolean detonate(LivingEntity t,LivingEntity shooter,float damage){return detonate(t,shooter,damage,DimensionAttachments.NONE);}
    static boolean detonate(LivingEntity t,LivingEntity shooter,float damage,DimensionAttachments.Snapshot equipment) {
        var before=ParasiteDummyDamage.capture(t);
        int immunity=t.invulnerableTime;t.invulnerableTime=0;
        try { t.hurt(cn.ssjjcompat.CombatMessages.remember(t.damageSources().indirectMagic(shooter,shooter),"dimension_mark"),damage*DimensionAttachments.boost(equipment,t)); }
        finally { t.invulnerableTime=immunity; }
        boolean damaged=ParasiteDummyDamage.confirmedLoss(before,t)>0;
        if(damaged) {
            shooter.heal(DimensionMarkRules.healing(shooter.getMaxHealth()));
            DimensionField.onMarkExplosion(shooter,t);
        }
        return damaged;
    }
    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.level instanceof ServerLevel level))return;
        for(var entry:new ArrayList<>(MARKS.entrySet())) {
            var t=entry.getKey();if(t==null || t.level()!=level)continue;
            if(!t.isAlive() || t.isRemoved()){MARKS.remove(t);DimensionMarkNetwork.state(t,0);continue;}
            var marks=entry.getValue();
            int oldCount=marks.size();var explosions=new ArrayList<String>();
            var due=DimensionMarkRules.due(marks,level.getGameTime());
            for(var m:due) {
                if(!t.isAlive())break;
                var owner=level.getEntity(m.owner());
                if(!(owner instanceof LivingEntity shooter) || !shooter.isAlive())continue;
                // All five marks must deal damage even in the same tick, independently of vanilla hurt immunity.
                if(detonate(t,shooter,m.damage(),m.equipment()))explosions.add(m.profile());
            }
            if(!explosions.isEmpty())DimensionMarkNetwork.burst(t,explosions);
            // A synchronous native death callback can remove the target while its
            // local timer list is still in this tick snapshot; never resync stale marks.
            if(!t.isAlive() || t.isRemoved()){MARKS.remove(t);DimensionMarkNetwork.state(t,0);continue;}
            if(marks.isEmpty())MARKS.remove(t);
            if(oldCount!=marks.size() || level.getGameTime()%10==0)DimensionMarkNetwork.state(t,profiles(t));
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if(MARKS.remove(e.getEntity())!=null)DimensionMarkNetwork.state(e.getEntity(),0);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e) {
        if(e.getEntity() instanceof LivingEntity t && MARKS.remove(t)!=null)DimensionMarkNetwork.state(t,0);
        if(e.getEntity() instanceof EntityKineticBullet b){SHOTS.remove(b);SHOT_PROFILES.remove(b);}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { MARKS.clear();SHOTS.clear();SHOT_PROFILES.clear(); }
}
