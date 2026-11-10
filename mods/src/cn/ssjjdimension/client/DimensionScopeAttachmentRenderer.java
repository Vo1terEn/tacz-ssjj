package cn.ssjjdimension.client;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** The gun skin contains the assembled sight; the item card is standalone only. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionScopeAttachmentRenderer {
    private static final Set<BedrockAttachmentModel> DONE=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Field CURRENT;
    static {try {CURRENT=BedrockAttachmentModel.class.getDeclaredField("currentGunItem");CURRENT.setAccessible(true);}catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}}
    @SubscribeEvent public static void tickDimensionScopeCards(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(String skin:cn.ssjjdimension.DimensionScopes.SKINS) TimelessAPI.getClientAttachmentIndex(new ResourceLocation("ssjj_dimension",skin+"_scope")).ifPresent(index->{
            var model=index.getAttachmentModel();if(model==null||DONE.contains(model))return;
            model.setFunctionalRenderer("item_extract",node->{try{node.visible=CURRENT.get(model)==null;}catch(IllegalAccessException e){throw new IllegalStateException(e);}return null;});
            DONE.add(model);
        });
    }
}
