package cn.ssjjzeus;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Per-weapon energy. A charged orb starts one independently visited chain per accepted victim. */
public final class ZeusEnergy {
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_zeus","hand_of_zeuss2");
    public static final ResourceLocation MAGAZINE=new ResourceLocation("ssjj_zeus","thunder_energy_mag");
    public static final String ENERGY="ssjj_zeus_thunder_energy";
    /** Gameplay adaptation where no original targeting range was supplied. Measured per hop. */
    public static final double CHAIN_RANGE=3;
    public record Anchor(Entity target,Entity part,Vec3 position,Vec3 damagePoint,float damage) {}
    public record Charge(int percent,boolean release) {}
    private record Hop(Entity core,Entity part,Vec3 position,double distance) {}
    private ZeusEnergy() {}
    public static double chainRange(){return ZeusConfig.number("chain_range");}
    public static boolean tierII(ItemStack stack) {
        IGun gun=IGun.getIGunOrNull(stack);return gun!=null && GUN.equals(gun.getGunId(stack));
    }
    public static boolean magazine(ItemStack stack) {
        IGun gun=IGun.getIGunOrNull(stack);
        return ZeusConfig.enabled("magazine_enabled") && gun!=null && GUN.equals(gun.getGunId(stack)) && MAGAZINE.equals(gun.getAttachmentId(stack,AttachmentType.EXTENDED_MAG));
    }
    public static int percent(ItemStack stack) {
        return stack!=null && stack.hasTag()?Math.max(0,Math.min(99,stack.getTag().getInt(ENERGY))):0;
    }
    public static Charge advance(int current,boolean magazine) {
        int next=Math.max(0,Math.min(99,current))+ZeusConfig.integer(magazine?"magazine_energy_per_orb":"energy_per_orb");
        return new Charge(next>=100?0:next,next>=100);
    }
    private static boolean carried(LivingEntity owner,ItemStack weapon) {
        if(!owner.isAlive() || owner.isRemoved() || !tierII(weapon))return false;
        if(owner instanceof Player player) {
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot)==weapon)return true;
            return false;
        }
        return owner.getMainHandItem()==weapon;
    }
    private static void sync(LivingEntity owner) {
        if(owner instanceof ServerPlayer player) {
            player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();
        }
    }
    static void impact(ServerLevel level,LivingEntity owner,ItemStack weapon,List<Anchor> anchors,
                       ZeusThunder.NativeBlast source,boolean magazine,String profile) {
        if(!ZeusConfig.enabled("energy_enabled") || anchors.isEmpty() || owner.level()!=level || !GUN.equals(source.getGunId()) || !carried(owner,weapon))return;
        // Exactly one increase for the whole orb, irrespective of how many blast victims it had.
        Charge charge=advance(percent(weapon),magazine);
        weapon.getOrCreateTag().putInt(ENERGY,charge.percent);sync(owner);
        if(!charge.release)return;
        // Consume before any native callbacks. Each chain owns its own visited set.
        ZeusThunderNetwork.release(level,anchors.get(0).position,profile);
        for(Anchor anchor:anchors)resolve(level,owner,anchor,source,magazine,profile);
    }
    private static Hop next(ServerLevel level,LivingEntity owner,Vec3 from,Set<UUID> visited) {
        Map<UUID,Hop> candidates=new HashMap<>();
        for(Entity part:level.getEntities(owner,new AABB(from,from).inflate(chainRange()),e->ZeusThunder.enemy(owner,ZeusThunder.core(e)))) {
            Entity core=ZeusThunder.core(part);if(visited.contains(core.getUUID()))continue;
            Vec3 position=part.getBoundingBox().getCenter();double distance=from.distanceToSqr(position);
            if(distance>chainRange()*chainRange())continue;
            if(level.clip(new ClipContext(from,position,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS)continue;
            Hop previous=candidates.get(core.getUUID());
            if(previous==null || distance<previous.distance)candidates.put(core.getUUID(),new Hop(core,part,position,distance));
        }
        return candidates.values().stream().min(Comparator.comparingDouble(Hop::distance).thenComparingInt(h->h.core.getId())).orElse(null);
    }
    static void resolve(ServerLevel level,LivingEntity owner,Anchor anchor,ZeusThunder.NativeBlast source,boolean magazine,String profile) {
        if(!(anchor.damage>0) || !Float.isFinite(anchor.damage))return;
        float amount=anchor.damage*(float)ZeusConfig.number(magazine?"magazine_chain_damage_fraction":"chain_damage_fraction");
        if(!Float.isFinite(amount) || amount<=0)return;
        var trace=source.chain(amount);Set<UUID> visited=new HashSet<>();visited.add(anchor.target.getUUID());
        Vec3 from=anchor.position;
        try {
            // A victim killed by the orb can still originate a chain from its captured position.
            if(ZeusThunder.enemy(owner,anchor.target))ZeusShock.chainHit(level,owner,anchor.part,trace,anchor.damagePoint,from,from.add(0,0,.001));
            for(int step=0;step<ZeusConfig.integer(magazine?"magazine_chain_hops":"chain_hops");step++) {
                Hop target=next(level,owner,from,visited);if(target==null)break;
                visited.add(target.core.getUUID());
                ZeusThunderNetwork.chain(level,from,target.position,profile);
                ZeusShock.chainHit(level,owner,target.part,trace,target.position,from,target.position);from=target.position;
            }
        } finally {trace.discard();}
    }
}
