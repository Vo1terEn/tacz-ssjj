package cn.ssjjdark;

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

/** Native TaCZ scope NBT chooses cosmetics; identity and combat remain untouched. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DarkSkins {
    public static final List<String> SKINS=List.of("pure","mix","nightguardian");
    public static final List<ResourceLocation> GUNS=List.of(new ResourceLocation("ssjj_dark","darkdiamond"),
        new ResourceLocation("ssjj_dark","darkdiamond1"),new ResourceLocation("ssjj_dark","darkdiamond2"));
    private DarkSkins() {}
    public static boolean family(ResourceLocation gun) {return gun!=null&&GUNS.contains(gun);}
    public static String skin(ResourceLocation attachment) {
        for(String skin:SKINS)if(new ResourceLocation("ssjj_dark",skin+"_scope").equals(attachment))return skin;
        return "";
    }
    public static ResourceLocation display(ResourceLocation gun,String skin) {
        if(!family(gun)||(!skin.isEmpty()&&!SKINS.contains(skin)))throw new IllegalArgumentException("Unknown Dark Crystal appearance");
        return new ResourceLocation("ssjj_dark",gun.getPath()+(skin.isEmpty()?"":"_"+skin)+"_display");
    }
    public static void syncDisplay(CompoundTag tag,ResourceLocation attachment,ResourceLocation gun) {
        if(!family(gun))return;
        String skin=skin(attachment),key=GunItemDataAccessor.GUN_DISPLAY_ID_TAG;
        if(!skin.isEmpty())tag.putString(key,display(gun,skin).toString());
        else {
            // Remove only a display owned by this cosmetic family. Preserve an
            // unrelated explicit display supplied by another compatible pack.
            String current=tag.getString(key);
            for(ResourceLocation tier:GUNS)for(String previous:SKINS)
                if(current.equals(display(tier,previous).toString())){tag.remove(key);return;}
        }
    }
    public static void sync(ItemStack stack) {
        if(stack==null||stack.isEmpty())return;
        var gun=IGun.getIGunOrNull(stack);if(gun==null||!family(gun.getGunId(stack)))return;
        syncDisplay(stack.getOrCreateTag(),gun.getAttachmentId(stack,AttachmentType.SCOPE),gun.getGunId(stack));
    }
    @SubscribeEvent public static void syncDarkCrystalServer(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.side!=LogicalSide.SERVER)return;
        for(var stack:event.player.getInventory().items)sync(stack);
        sync(event.player.getOffhandItem());
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void syncDarkCrystalClient(TickEvent.ClientTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return;
            for(var stack:player.getInventory().items)sync(stack);
            sync(player.getOffhandItem());
        }
    }
}
