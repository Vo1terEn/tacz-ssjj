package cn.fusionarsenal.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The optional Cloth class is reached only after the CLIENT side and mod-presence checks. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ClientConfigRegistration {
    private ClientConfigRegistration() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("cloth_config")) return;
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ClothSettingsScreen.create(parent)));
    }
}
