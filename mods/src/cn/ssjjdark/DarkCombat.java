package cn.ssjjdark;

import cn.judgfire.BurstStun;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.*;
import java.lang.reflect.Field;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Clients send only slot and action; authenticated server state owns target, damage and timing. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkCombat {
    private static final Map<UUID,State> STATES=new HashMap<>();
    private static final Field LAST_HURT=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(LivingEntity.class,"f_20898_");
    private static final SimpleChannel CHANNEL=createChannel();
    private DarkCombat(){}
    private static final class State {
        final DarkDamageRules.Sequence sequence=new DarkDamageRules.Sequence();
        Pending pending;
    }
    private record Pending(ServerLevel level,ItemStack stack,int slot,ResourceLocation id,boolean heavy){}
    public static ResourceLocation gunId(ItemStack stack){
        if(stack==null||stack.isEmpty()||!(stack.getItem() instanceof IGun gun))return null;
        var tag=stack.getTag();if(tag==null||!tag.contains("GunId",8))return null;
        var id=gun.getGunId(stack);
        return id!=null&&DarkDamageRules.gun(id.getNamespace(),id.getPath())?id:null;
    }
    public static boolean matches(ItemStack stack){return gunId(stack)!=null;}
    /** Migrate existing semi-auto carrier stacks when they are held on the server. */
    public static void ensureAutomatic(ItemStack stack){
        if(matches(stack)&&stack.getItem() instanceof IGun gun&&gun.getFireMode(stack)!=FireMode.AUTO)
            gun.setFireMode(stack,FireMode.AUTO);
    }
    public static void init(){Objects.requireNonNull(CHANNEL);DarkDragonNetwork.init();DarkLiberationNetwork.init();}
    private static SimpleChannel createChannel(){
        var channel=NetworkRegistry.newSimpleChannel(new ResourceLocation("ssjj_dark","basic_melee"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Action.class,Action::encode,Action::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{
                var player=context.getSender();if(player!=null&&DarkDamageRules.validSlot(message.slot()))accept(player,message.slot(),message.heavy());
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(1,Animation.class,Animation::encode,Animation::decode,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{
                if(message.valid())DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.ssjjdark.client.DarkCrystalAnimations.play(message.owner(),message.slot(),message.id(),message.heavy()));
            });context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return channel;
    }
    public record Action(int slot,boolean heavy){
        public static void encode(Action message,FriendlyByteBuf buf){buf.writeVarInt(message.slot);buf.writeBoolean(message.heavy);}
        public static Action decode(FriendlyByteBuf buf){return new Action(buf.readVarInt(),buf.readBoolean());}
    }
    public record Animation(UUID owner,int slot,ResourceLocation id,boolean heavy){
        public boolean valid(){return owner!=null&&DarkDamageRules.validSlot(slot)&&id!=null&&DarkDamageRules.gun(id.getNamespace(),id.getPath());}
        public static void encode(Animation message,FriendlyByteBuf buf){buf.writeUUID(message.owner);buf.writeVarInt(message.slot);buf.writeResourceLocation(message.id);buf.writeBoolean(message.heavy);}
        public static Animation decode(FriendlyByteBuf buf){return new Animation(buf.readUUID(),buf.readVarInt(),buf.readResourceLocation(),buf.readBoolean());}
    }
    public static void request(int slot,boolean heavy){if(DarkDamageRules.validSlot(slot))CHANNEL.sendToServer(new Action(slot,heavy));}
    private static boolean available(ServerPlayer player){
        return player.isAlive()&&!player.isSpectator()&&!player.isRemoved()&&!BurstStun.stunned(player)&&!DarkLiberation.charging(player)
            &&player.containerMenu==player.inventoryMenu;
    }
    public static boolean accept(ServerPlayer player,int slot,boolean heavy){
        if(!DarkDamageRules.validSlot(slot)||!available(player)||player.getInventory().selected!=slot)return false;
        var stack=player.getMainHandItem();var id=gunId(stack);if(id==null)return false;
        ensureAutomatic(stack);
        DarkCore.prepare(stack);if(!DarkCore.ready(stack))return false;
        var operator=IGunOperator.fromLivingEntity(player);if(operator==null)return false;
        var reload=operator.getSynReloadState();
        if(operator.getSynDrawCoolDown()>0||operator.getSynIsBolting()||operator.getSynMeleeCoolDown()>0
            ||reload!=null&&reload.getStateType().isReloading())return false;
        var state=STATES.computeIfAbsent(player.getUUID(),key->new State());long now=player.serverLevel().getGameTime();
        if(!state.sequence.begin(now,heavy))return false;
        state.pending=new Pending(player.serverLevel(),stack,slot,id,heavy);
        player.swing(InteractionHand.MAIN_HAND,true);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Animation(player.getUUID(),slot,id,heavy));
        return true;
    }
    private static boolean bound(ServerPlayer player,Pending pending){
        return pending!=null&&available(player)&&pending.level==player.serverLevel()
            &&player.getInventory().selected==pending.slot&&player.getMainHandItem()==pending.stack
            &&DarkCore.ready(pending.stack)
            &&pending.id.equals(gunId(player.getMainHandItem()));
    }
    @SubscribeEvent public static void tickDarkCrystalCombat(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        ensureAutomatic(player.getMainHandItem());
        DarkCore.prepare(player.getMainHandItem());
        var state=STATES.get(player.getUUID());if(state==null||state.pending==null)return;
        if(!bound(player,state.pending)){state.sequence.cancel();state.pending=null;return;}
        var strike=state.sequence.consume(player.serverLevel().getGameTime());if(strike==null)return;
        var pending=state.pending;state.pending=null;hit(player,strike);
        DarkDragon.launch(player,pending.id);
    }
    /** Exactly one nearest living target along the current view; blocks truncate the trace first. */
    private static void hit(ServerPlayer player,DarkDamageRules.Strike strike){
        var level=player.serverLevel();Vec3 origin=player.getEyePosition(),direction=player.getLookAngle();
        Vec3 end=origin.add(direction.scale(DarkDamageRules.REACH));
        var block=level.clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double limit=block.getType()==HitResult.Type.MISS?DarkDamageRules.REACH*DarkDamageRules.REACH:origin.distanceToSqr(block.getLocation());
        LivingEntity nearest=null;double nearestDistance=Double.POSITIVE_INFINITY;
        var bounds=player.getBoundingBox().expandTowards(direction.scale(DarkDamageRules.REACH)).inflate(1);
        // AABB.clip excludes its endpoint; extend only the intersection query,
        // then enforce the original squared reach and block distance below.
        var entityEnd=end.add(direction.scale(1e-6));
        for(var target:level.getEntitiesOfClass(LivingEntity.class,bounds,target->target!=player&&target.isAlive()&&!target.isSpectator()&&target.isPickable())){
            if(target instanceof Player other&&(player.getServer()==null||!player.getServer().isPvpAllowed()||!player.canHarmPlayer(other)))continue;
            var box=target.getBoundingBox();var intersection=box.clip(origin,entityEnd);
            Vec3 point=box.contains(origin)?origin:intersection.orElse(null);if(point==null)continue;
            double distance=origin.distanceToSqr(point);
            if(!DarkDamageRules.withinReach(distance)||distance>limit||block.getType()!=HitResult.Type.MISS&&distance>=limit
                ||distance>=nearestDistance||!player.hasLineOfSight(target))continue;
            nearest=target;nearestDistance=distance;
        }
        if(nearest!=null)damage(player,nearest,strike.damage());
    }
    /** The server's eight-tick combo has its own cadence; retain unrelated hurt immunity. */
    static void damage(ServerPlayer player,LivingEntity target,float amount){
        var source=player.damageSources().playerAttack(player);
        int immunity=target.invulnerableTime;
        if(immunity<=10){target.hurt(source,amount);return;}
        try {
            float previous=LAST_HURT.getFloat(target);
            target.invulnerableTime=0;
            try {target.hurt(source,amount);}
            finally {target.invulnerableTime=immunity;LAST_HURT.setFloat(target,previous);}
        } catch(IllegalAccessException failure){throw new IllegalStateException("Cannot preserve melee hurt window",failure);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preventDarkCrystalProjectile(GunShootEvent event){if(matches(event.getGunItemStack()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preventDarkCrystalReload(GunReloadEvent event){
        if(DarkCore.ready(event.getGunItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preventDarkCrystalNativeMelee(GunMeleeEvent event){if(matches(event.getGunItemStack()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void preventDarkCrystalVanillaAttack(AttackEntityEvent event){if(matches(event.getEntity().getMainHandItem()))event.setCanceled(true);}
    @SubscribeEvent public static void logoutDarkCrystal(PlayerEvent.PlayerLoggedOutEvent event){STATES.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimensionDarkCrystal(PlayerEvent.PlayerChangedDimensionEvent event){cancel(event.getEntity());}
    @SubscribeEvent public static void respawnDarkCrystal(PlayerEvent.PlayerRespawnEvent event){cancel(event.getEntity());}
    private static void cancel(Player player){var state=STATES.get(player.getUUID());if(state!=null){state.sequence.cancel();state.pending=null;}}
    @SubscribeEvent public static void stopDarkCrystal(ServerStoppedEvent event){STATES.clear();}
}
