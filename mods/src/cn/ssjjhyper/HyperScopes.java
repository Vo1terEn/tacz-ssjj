package cn.ssjjhyper;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Existing TaCZ scope NBT selects appearance; gun identity and gameplay NBT stay native. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperScopes {
    public static final List<String> SKINS=List.of("totem","star","colorful_spar");
    private HyperScopes() {}
    public static boolean family(ResourceLocation gun) {return gun!=null && HyperReloadProtection.GUNS.contains(gun);}
    public static String skin(ResourceLocation attachment) {
        for(String skin:SKINS)if(new ResourceLocation("ssjj_hyper",skin+"_scope").equals(attachment))return skin;
        return "";
    }
    public static ResourceLocation display(ResourceLocation gun,String skin) {
        if(!family(gun) || (!skin.isEmpty() && !SKINS.contains(skin)))throw new IllegalArgumentException("Unknown Hyper appearance");
        return new ResourceLocation("ssjj_hyper",gun.getPath()+(skin.isEmpty()?"":"_"+skin)+"_display");
    }
    public static void syncDisplay(CompoundTag tag,ResourceLocation attachment,ResourceLocation gun) {
        if(!family(gun))return;
        String skin=skin(attachment),key=GunItemDataAccessor.GUN_DISPLAY_ID_TAG;
        if(!skin.isEmpty())tag.putString(key,display(gun,skin).toString());
        else {
            String current=tag.getString(key);
            for(ResourceLocation tier:HyperReloadProtection.GUNS)for(String previous:SKINS)
                if(current.equals(display(tier,previous).toString())){tag.remove(key);return;}
        }
    }
    public static void sync(ItemStack stack) {
        if(stack==null || stack.isEmpty())return;
        var gun=IGun.getIGunOrNull(stack);if(gun==null || !family(gun.getGunId(stack)))return;
        syncDisplay(stack.getOrCreateTag(),gun.getAttachmentId(stack,AttachmentType.SCOPE),gun.getGunId(stack));
    }
    @SubscribeEvent public static void syncHyperServer(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.side!=LogicalSide.SERVER)return;
        for(var stack:event.player.getInventory().items)sync(stack);
        sync(event.player.getOffhandItem());
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void syncHyperClient(TickEvent.ClientTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return;
            for(var stack:player.getInventory().items)sync(stack);
            sync(player.getOffhandItem());
        }
    }
}
