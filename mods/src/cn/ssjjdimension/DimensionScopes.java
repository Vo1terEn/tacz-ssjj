package cn.ssjjdimension;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Native attachment NBT selects a display; ammunition and other NBT are preserved. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionScopes {
    public static final String[] GUNS={"dimension_star","dimension_star1","dimension_star2"};
    public static final String[] SKINS={"frozen","neon","royal"};
    private DimensionScopes() {}
    public static boolean family(ResourceLocation id) {
        return id!=null && "ssjj_dimension".equals(id.getNamespace())
            && java.util.List.of(GUNS).contains(id.getPath());
    }
    public static String skin(ResourceLocation attachment) {
        for(String skin:SKINS)if(new ResourceLocation("ssjj_dimension",skin+"_scope").equals(attachment))return skin;
        return "";
    }
    public static String profile(ResourceLocation gun,String skin) {
        if(!family(gun))throw new IllegalArgumentException("Foreign gun");
        return gun.getPath()+(skin.isEmpty()?"":"_"+skin);
    }
    public static void syncDisplay(CompoundTag tag,ResourceLocation scope,ResourceLocation gunId) {
        if(!family(gunId))return;
        String skin=skin(scope),key=GunItemDataAccessor.GUN_DISPLAY_ID_TAG;
        if(!skin.isEmpty())tag.putString(key,"ssjj_dimension:"+profile(gunId,skin)+"_display");
        else {
            String current=tag.getString(key);
            for(String gun:GUNS)for(String old:SKINS)
                if(current.equals("ssjj_dimension:"+gun+"_"+old+"_display")){tag.remove(key);return;}
        }
    }
    public static void sync(ItemStack stack) {
        if(stack==null || stack.isEmpty())return;
        var gun=IGun.getIGunOrNull(stack);if(gun==null || !family(gun.getGunId(stack)))return;
        syncDisplay(stack.getOrCreateTag(),gun.getAttachmentId(stack,AttachmentType.SCOPE),gun.getGunId(stack));
    }
    @SubscribeEvent public static void syncDimensionServer(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.side!=LogicalSide.SERVER)return;
        for(var stack:event.player.getInventory().items)sync(stack);
        sync(event.player.getOffhandItem());
    }
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void syncDimensionClient(TickEvent.ClientTickEvent event) {
            if(event.phase!=TickEvent.Phase.END)return;
            var player=net.minecraft.client.Minecraft.getInstance().player;if(player==null)return;
            for(var stack:player.getInventory().items)sync(stack);
            sync(player.getOffhandItem());
        }
    }
}
