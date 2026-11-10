package cn.ssjjcompat;

import cn.judgfire.JudgmentSkillKey;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.client.input.ZoomKey;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.InputEvent;

/** Preserve the original keyboard-only melee/zoom reservation in TaCZ's new router. */
public final class SkillInputCompat {
    private SkillInputCompat() {}
    public static void run(Runnable action,InputConstants.Key key,KeyMapping binding) {
        if(binding==MeleeKey.MELEE_KEY || binding==ZoomKey.ZOOM_KEY) {
            var type=key.getType();
            if(type==InputConstants.Type.KEYSYM || type==InputConstants.Type.SCANCODE) {
                int keyCode=type==InputConstants.Type.KEYSYM?key.getValue():-1;
                int scanCode=type==InputConstants.Type.SCANCODE?key.getValue():0;
                if(JudgmentSkillKey.reserved(new InputEvent.Key(keyCode,scanCode,1,0)))return;
            }
        }
        action.run();
    }
}
