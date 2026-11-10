package cn.ssjjparasite2.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ParasiteScopes {
    public static final ResourceLocation GUN=new ResourceLocation("ssjjparasite2","parasitic_sniper2");
    public static boolean family(ResourceLocation id) {
        return id!=null && id.getNamespace().equals("ssjjparasite2") && java.util.Set.of("parasitic_sniper","parasitic_sniper1","parasitic_sniper2").contains(id.getPath());
    }
    public static boolean ours(ItemStack stack) {
        var gun=IGun.getIGunOrNull(stack);
        return gun!=null && family(gun.getGunId(stack));
    }
    public static String skin(ResourceLocation scope) {
        if(new ResourceLocation("ssjjparasite2","frost_scope").equals(scope)) return "frost";
        if(new ResourceLocation("ssjjparasite2","ship_scope").equals(scope)) return "ship";
        return "";
    }
    public static void sync(ItemStack stack) {
        if(!ours(stack)) return;
        var gun=IGun.getIGunOrNull(stack);
        syncDisplay(stack.getOrCreateTag(),gun.getAttachmentId(stack,AttachmentType.SCOPE),gun.getGunId(stack));
    }
    public static void syncDisplay(net.minecraft.nbt.CompoundTag tag,ResourceLocation scope) {
        syncDisplay(tag,scope,GUN);
    }
    public static void syncDisplay(net.minecraft.nbt.CompoundTag tag,ResourceLocation scope,ResourceLocation gunId) {
        if(!family(gunId))return;
        String prefix="ssjjparasite2:"+gunId.getPath();
        String skin=skin(scope);
        String key=GunItemDataAccessor.GUN_DISPLAY_ID_TAG;
        if(!skin.isEmpty()) tag.putString(key,prefix+"_"+skin+"_display");
        else {
            String current=tag.getString(key);
            if(current.equals(prefix+"_frost_display") || current.equals(prefix+"_ship_display")) tag.remove(key);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.side!=LogicalSide.SERVER) return;
        for(var stack:event.player.getInventory().items) sync(stack);
        sync(event.player.getOffhandItem());
    }
}
