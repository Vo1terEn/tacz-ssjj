package cn.ssjjdark.client;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import java.util.function.Function;
import net.minecraft.client.Minecraft;

/** Source-fitted native skin/sleeve arms, using the existing TaCZ hand callbacks. */
public final class DarkNativeHands {
    private DarkNativeHands() {}
    public static void install(BedrockAnimatedModel model) {
        install(model,"lefthand_pos",true);install(model,"righthand_pos",false);
    }
    private static void install(BedrockAnimatedModel model,String name,boolean left) {
        if(!(model.getNode(name) instanceof FunctionalBedrockPart node))return;
        var original=node.functionalRenderer;
        if(original!=null && !(original instanceof HandFactory))node.functionalRenderer=new HandFactory(original,left);
    }
    private static final class HandFactory implements Function<BedrockPart,IFunctionalRenderer> {
        private final Function<BedrockPart,IFunctionalRenderer> original;
        private final boolean left;
        HandFactory(Function<BedrockPart,IFunctionalRenderer> original,boolean left){this.original=original;this.left=left;}
        @Override public IFunctionalRenderer apply(BedrockPart node) {
            var hand=original.apply(node);
            return (pose,consumer,context,light,overlay)->{
                var mc=Minecraft.getInstance();
                boolean slim=mc!=null && mc.player!=null && "slim".equals(mc.player.getModelName());
                pose.pushPose();
                try {
                    if(slim)pose.translate(left?-.03125:.03125,0,0);
                    hand.render(pose,consumer,context,light,overlay);
                } finally {pose.popPose();}
            };
        }
    }
}
