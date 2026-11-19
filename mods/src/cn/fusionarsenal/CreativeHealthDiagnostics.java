package cn.fusionarsenal;

import java.util.Map;
import cn.fusionarsenal.health.DeepHealthProbe;
import cn.fusionarsenal.health.DeathProgressProbe;
import cn.judgfire.CreativeHealthBridge;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class CreativeHealthDiagnostics {
    private CreativeHealthDiagnostics(){}
    public static String loader(){
        try{
            Class<?> bridge=Class.forName("cn.fusionarsenal.launch.HealthBytecode");
            return String.valueOf(bridge.getMethod("diagnostics").invoke(null));
        }catch(ReflectiveOperationException|LinkageError unavailable){return "加载期服务未就绪：需安装配套加载组件并完整重启游戏";}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ssjjcreative").requires(source->source.hasPermission(2)).executes(context->{
            context.getSource().sendSuccess(()->Component.literal("创造直伤加载器="+loader()+"；提交="+CreativeHealthState.COMMITS.sum()
                +"；未知提交="+CreativeHealthState.UNKNOWN_COMMITS.sum()+"；死亡入口="+CreativeHealthState.DEATHS.sum()+"；重复结算拦截="+CreativeHealthState.DUPLICATES.sum()+"；原生死亡回退="+CreativeHealthState.FALLBACKS.sum()),false);
            return 1;
        }).then(Commands.literal("probe").then(Commands.argument("target",EntityArgument.entity()).executes(context->{
            Entity entity=CreativeTargets.resolve(EntityArgument.getEntity(context,"target"));
            if(!(entity instanceof LivingEntity target)){
                context.getSource().sendFailure(Component.literal("目标或分体的主体不是 LivingEntity"));return 0;
            }
            DeepHealthProbe.Snapshot snapshot=DeepHealthProbe.inspect(target);
            String result="真实血池="+snapshot+"；返回血量="+target.getHealth()+"；原版血槽="+CreativeHealthBridge.read(target)
                +"；死亡锁定="+CreativeHealthState.terminal(target)+"；已结算="+CreativeHealthBridge.settled(target)
                +"；死亡计时="+DeathProgressProbe.sample(target);
            context.getSource().sendSuccess(()->Component.literal(result),false);return 1;
        }))));
    }
}
