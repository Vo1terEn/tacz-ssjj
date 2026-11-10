package cn.ssjjdimension;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Original weapon recordings, emitted once after the server accepts an action. */
public final class DimensionFieldSounds {
    private static final SoundEvent ACTIVATE=SoundEvent.createVariableRangeEvent(new ResourceLocation("ssjj_dimension","field_activate"));
    private static final SoundEvent FIRE=SoundEvent.createVariableRangeEvent(new ResourceLocation("ssjj_dimension","field_high_energy_fire"));
    private static final SoundEvent IMPACT=SoundEvent.createFixedRangeEvent(new ResourceLocation("ssjj_dimension","field_impact"),96);
    private DimensionFieldSounds() {}
    public static void activate(ServerPlayer player) {
        if(!DimensionVisualScope.defaultTier2(player.getMainHandItem())||!(player.level() instanceof ServerLevel level))return;
        play(level,player.getX(),player.getY(),player.getZ(),ACTIVATE);
    }
    public static void cast(ServerPlayer player,DimensionField.Area area) {
        if(!(player.level() instanceof ServerLevel level))return;
        play(level,player.getX(),player.getY(),player.getZ(),FIRE);
        if(!DimensionVisualScope.defaultTier2(player.getMainHandItem()))return;
        play(level,area.center().x,area.center().y,area.center().z,IMPACT);
    }
    private static void play(ServerLevel level,double x,double y,double z,SoundEvent event) {
        level.playSound(null,x,y,z,event,SoundSource.PLAYERS,1,1);
    }
}
