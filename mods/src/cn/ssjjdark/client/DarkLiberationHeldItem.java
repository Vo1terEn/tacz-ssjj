package cn.ssjjdark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Detached source actor item: original renderer and an explicitly scoped model locator. */
public final class DarkLiberationHeldItem {
    private DarkLiberationHeldItem(){}
    public static void render(AbstractClientPlayer owner,ItemStack weapon,String locator,PoseStack pose,MultiBufferSource buffers,int light){
        Runnable draw=()->Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(owner,weapon,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,false,pose,buffers,light);
        var display=TimelessAPI.getGunDisplay(weapon).orElse(null);
        if(display==null){draw.run();return;}
        DarkAresThirdPersonBridge.privateItem(display.getGunModel(),display.getTransform().getScale(),locator,pose,draw);
    }
}
