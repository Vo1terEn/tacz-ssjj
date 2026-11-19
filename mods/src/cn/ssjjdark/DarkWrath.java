package cn.ssjjdark;

import cn.ssjjparasite2.client.ParasiteDestructionDamage;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Inventory-carried Absolute Wrath; every hit reads current HP and the actual legal equipment. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkWrath {
    public static final ResourceLocation EFFECT_ID=new ResourceLocation("ssjj_dark","absolute_wrath");
    public static final ResourceLocation BLADE_ID=new ResourceLocation("ssjj_dark","crystal_blade");
    /** Registry tombstone for saved pre20 potion instances; never grants or represents this passive. */
    @Deprecated public static final MobEffect EFFECT=new WrathEffect();
    public static final Snapshot NONE=new Snapshot(0,0,0);
    public record Snapshot(int layers,double damageBonus,double damageReduction){
        public Snapshot {damageReduction=Double.isFinite(damageReduction)?Math.max(0,Math.min(1,damageReduction)):0;}
        public double damageMultiplier(){return 1+damageBonus;}
        public double incomingMultiplier(){return 1-damageReduction;}
    }
    private static final class WrathEffect extends MobEffect {
        WrathEffect(){super(MobEffectCategory.BENEFICIAL,0x6D24B1);}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientMobEffectExtensions(){
                @Override public boolean isVisibleInInventory(MobEffectInstance ignored){return false;}
                @Override public boolean isVisibleInGui(MobEffectInstance ignored){return false;}
            });
        }
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerAbsoluteWrath(RegisterEvent event){
            event.register(Registries.MOB_EFFECT,helper->helper.register(EFFECT_ID,EFFECT));
        }
    }
    private DarkWrath(){}
    public static int tier(ItemStack stack){
        if(stack==null||stack.isEmpty())return 0;
        var gun=IGun.getIGunOrNull(stack);if(gun==null)return 0;
        var id=gun.getGunId(stack);return id==null?0:DarkWrathRules.tier(id.getNamespace(),id.getPath());
    }
    /** The attachment must belong to this exact eligible weapon and match both slot and allow-tag. */
    public static boolean enhanced(ItemStack stack){
        if(tier(stack)==0)return false;
        var gun=IGun.getIGunOrNull(stack);
        return gun!=null&&BLADE_ID.equals(gun.getAttachmentId(stack,AttachmentType.MUZZLE))
            &&gun.allowAttachmentType(stack,AttachmentType.MUZZLE)
            &&AllowAttachmentTagMatcher.match(gun.getGunId(stack),BLADE_ID);
    }
    public static Snapshot snapshot(ServerPlayer player){
        if(player==null||!player.isAlive()||player.isRemoved()||player.isSpectator())return NONE;
        boolean ultimate=DarkLiberation.ultimateActive(player);
        int layers=DarkWrathRules.layers(player.getHealth(),player.getMaxHealth());
        if(layers==0&&!ultimate)return NONE;
        double damage=0,reduction=0;
        // Only the ordinary36 item slots and offhand. Nested bags and external containers do not qualify.
        for(var stack:player.getInventory().items){
            int tier=tier(stack);if(tier==0)continue;
            boolean blade=enhanced(stack);
            damage=Math.max(damage,DarkWrathRules.damagePerLayer(tier,blade));
            reduction=Math.max(reduction,DarkWrathRules.reductionPerLayer(tier,blade));
        }
        for(var stack:player.getInventory().offhand){
            int tier=tier(stack);if(tier==0)continue;
            boolean blade=enhanced(stack);
            damage=Math.max(damage,DarkWrathRules.damagePerLayer(tier,blade));
            reduction=Math.max(reduction,DarkWrathRules.reductionPerLayer(tier,blade));
        }
        if(ultimate)return new Snapshot(Math.max(1,layers),DarkCrystalConfig.number("ultimate_wrath_bonus"),layers*reduction*DarkLiberation.wrathReductionMultiplier(player));
        return damage>0||reduction>0?new Snapshot(layers,layers*damage,layers*reduction*DarkLiberation.wrathReductionMultiplier(player)):NONE;
    }
    private static ServerPlayer attacker(DamageSource source){
        if(source.getEntity() instanceof ServerPlayer player)return player;
        Entity entity=source.getDirectEntity();
        for(int depth=0;entity!=null&&depth<4;depth++){
            if(entity instanceof ServerPlayer player)return player;
            if(entity instanceof Projectile projectile){var owner=projectile.getOwner();if(owner==entity)break;entity=owner;}
            else break;
        }
        return null;
    }
    /** Native hurts only: never amplify or defend a replay whose constituent losses were already settled. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event){
        float amount=event.getAmount();var source=event.getSource();
        if(event.isCanceled()||event.getEntity().level().isClientSide||!(amount>0)||!Float.isFinite(amount)
            ||source==null||source instanceof ParasiteDestructionDamage)return;
        double scaled=amount;var owner=attacker(source);
        if(owner!=null)scaled*=snapshot(owner).damageMultiplier();
        if(event.getEntity() instanceof ServerPlayer target)
            scaled*=snapshot(target).incomingMultiplier();
        event.setAmount((float)Math.max(0,Math.min(Float.MAX_VALUE,scaled)));
    }
    /** Old callers remain binary-compatible; this only removes obsolete save data. */
    public static void refresh(ServerPlayer player){removeLegacy(player);}
    public static void removeLegacy(Player player){
        if(player!=null&&player.hasEffect(EFFECT))player.removeEffect(EFFECT);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event){
        // Both sides remove any migrated instance, including its particles. No duration or periodic buff refresh.
        if(event.phase==TickEvent.Phase.END)removeLegacy(event.player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){removeLegacy(event.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){removeLegacy(event.getOriginal());removeLegacy(event.getEntity());}
}
