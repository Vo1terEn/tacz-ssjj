package cn.ssjjparasite2.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Migrate pre-update base guns carrying AUTO NBT; tier I/II keep native modes. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ParasiteFireModes {
 private static final ResourceLocation BASE=new ResourceLocation("ssjjparasite2","parasitic_sniper");
 private ParasiteFireModes(){}
 public static boolean isBase(ItemStack stack){var gun=stack==null?null:IGun.getIGunOrNull(stack);return gun!=null&&!stack.isEmpty()&&BASE.equals(gun.getGunId(stack));}
 public static void normalize(ItemStack stack){if(isBase(stack)){var gun=IGun.getIGunOrNull(stack);if(gun.getFireMode(stack)!=FireMode.SEMI)gun.setFireMode(stack,FireMode.SEMI);}}
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.START)return;for(ItemStack s:e.player.getInventory().items)normalize(s);normalize(e.player.getOffhandItem());}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void shoot(GunShootEvent e){normalize(e.getGunItemStack());if(e.getShooter()!=null)normalize(e.getShooter().getMainHandItem());}
 @SubscribeEvent(priority=EventPriority.HIGHEST)public static void select(GunFireSelectEvent e){if(isBase(e.getGunItemStack())){normalize(e.getGunItemStack());if(e.getShooter()!=null)normalize(e.getShooter().getMainHandItem());e.setCanceled(true);}}
}
