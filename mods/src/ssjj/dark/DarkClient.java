package ssjj.dark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** 技能按键、源UI与动画后刀刃轨迹；不在客户端决定伤害。 */
@Mod.EventBusSubscriber(modid=DarkCrystalMod.ID, value=Dist.CLIENT)
public final class DarkClient {
    private static final KeyMapping KEY = new KeyMapping("key.ssjj_dark.skill", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, 86, "key.categories.ssjj_dark");
    private static final ResourceLocation[] TRAILS = { texture("blade_trail_0"), texture("blade_trail_1"), texture("blade_trail_2") };
    private static BedrockGunModel model;
    private static boolean queued;
    private static double attackStart = -100;
    private static double attackEnd = -100;
    private static Object level;
    private static String heldId = "";
    private static boolean chargePlaying;
    private static final DarkHeldInput INPUT = new DarkHeldInput();
    private static final Set<Integer> SCANCODES = new HashSet<>();
    private static final Set<Integer> BLOCKED_SCANCODES = new HashSet<>();
    private static Object heldStack;
    private static int heldSlot = -1;
    private static String bindings = "";
    private static double lastStrike=-100;
    private static double inspectReadyAt;
    private static int nextCombo;
    private static final List<Sample> SAMPLES = new ArrayList<>();
    private record Sample(double time, Vector3f base, Vector3f tip) {}

    @Mod.EventBusSubscriber(modid=DarkCrystalMod.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(KEY); }
    }

    private static ResourceLocation texture(String name) { return new ResourceLocation(DarkCrystalMod.ID, "textures/effect/"+name+".png"); }
    private static double time(Minecraft mc) { return mc.f_91073_ == null ? 0 : mc.f_91073_.m_46467_()+mc.m_91296_(); }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        String id = mc.f_91074_ == null ? "" : DarkSkills.id(mc.f_91074_.m_21205_());
        Object stack = mc.f_91074_ == null ? null : mc.f_91074_.m_21205_();
        int slot = mc.f_91074_ == null ? -1 : mc.f_91074_.m_150109_().f_35977_;
        if (level != mc.f_91073_ || !heldId.equals(id) || heldStack != stack || heldSlot != slot) {
            if (level != mc.f_91073_) { INPUT.resetClock(); inspectReadyAt=0; } else INPUT.reset();
            level=mc.f_91073_; heldId=id; SAMPLES.clear(); model=null; queued=false; chargePlaying=false; attackStart=-100; attackEnd=-100;
            heldStack=stack; heldSlot=slot; nextCombo=0;
            DarkClientState.accept(new DarkNetwork.State(0, 0, 0, 1, 1, 0, 0));
            if (!id.isEmpty()) TimelessAPI.getGunDisplay(mc.f_91074_.m_21205_()).ifPresent(display -> {
                display.warmUpModel(); display.warmUpRuntime();
            });
        }
        pollControls(mc);
        inspect(false);
        if (mc.f_91074_ != null && !id.isEmpty()) {
            // Native TaCZ input stays suppressed in both tick phases. Physical
            // GLFW states above remain readable after MouseButton.Pre is canceled.
            ShootKey.SHOOT_KEY.m_7249_(false); AimKey.AIM_KEY.m_7249_(false);
            var operator=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.f_91074_);
            if (operator.isAim()) operator.aim(false);
        }
        if (event.phase != TickEvent.Phase.END) return;
        if (!mc.m_91104_()) DarkClientState.tick();
        while (KEY.m_90859_()) {
            if (controlsEnabled(mc)
                && DarkSkills.grade(mc.f_91074_.m_21205_()) == 2) DarkNetwork.CHANNEL.sendToServer(new DarkNetwork.Activate());
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_==null || mc.f_91080_!=null || mc.m_91104_() || DarkSkills.grade(mc.f_91074_.m_21205_())<0) return;
        if (event.getAction()==GLFW.GLFW_PRESS && InspectKey.INSPECT_KEY.m_90830_(event.getButton())
            && InspectKey.INSPECT_KEY.getKeyModifier().isActive(InspectKey.INSPECT_KEY.getKeyConflictContext())) {
            inspect(true);
        }
        boolean light=ShootKey.SHOOT_KEY.m_90830_(event.getButton()), heavy=AimKey.AIM_KEY.m_90830_(event.getButton());
        if (!light && !heavy) return;
        event.setCanceled(true);
        pollControls(mc);
    }

    private static boolean controlsEnabled(Minecraft mc) {
        return mc.f_91074_ != null && mc.f_91073_ != null && mc.f_91074_.m_6084_()
            && !mc.f_91074_.m_5833_() && mc.f_91080_ == null && !mc.m_91104_()
            && mc.m_91396_() && mc.f_91067_.m_91600_()
            && DarkHeldInput.isDarkWeapon(DarkSkills.id(mc.f_91074_.m_21205_()));
    }

    private static String bindingId(KeyMapping mapping) {
        return mapping.getKey().m_84874_() + ":" + mapping.getKeyModifier().name();
    }

    private static boolean physicalDown(Minecraft mc, KeyMapping mapping) {
        if (!mapping.getKeyModifier().isActive(mapping.getKeyConflictContext())) return false;
        var key = mapping.getKey();
        int value = key.m_84873_();
        long window = mc.m_91268_().m_85439_();
        if (key.m_84868_() == InputConstants.Type.MOUSE)
            return value >= 0 && value <= GLFW.GLFW_MOUSE_BUTTON_LAST
                && GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
        if (key.m_84868_() == InputConstants.Type.KEYSYM)
            return value >= GLFW.GLFW_KEY_SPACE && value <= GLFW.GLFW_KEY_LAST
                && InputConstants.m_84830_(window, value);
        return key.m_84868_() == InputConstants.Type.SCANCODE
            && (SCANCODES.contains(value) || BLOCKED_SCANCODES.contains(value));
    }

    private static void pollControls(Minecraft mc) {
        boolean enabled = controlsEnabled(mc);
        if (!enabled) { BLOCKED_SCANCODES.addAll(SCANCODES); SCANCODES.clear(); }
        String currentBindings = bindingId(ShootKey.SHOOT_KEY) + "|" + bindingId(AimKey.AIM_KEY);
        if (!bindings.equals(currentBindings)) { bindings = currentBindings; INPUT.reset(); }
        if (mc.f_91074_ != null && (heldStack != mc.f_91074_.m_21205_()
            || heldSlot != mc.f_91074_.m_150109_().f_35977_ || level != mc.f_91073_)) INPUT.reset();
        boolean light = enabled && physicalDown(mc, ShootKey.SHOOT_KEY);
        boolean heavy = enabled && physicalDown(mc, AimKey.AIM_KEY);
        double tick = mc.f_91073_ == null ? 0 : mc.f_91073_.m_46467_();
        var action = INPUT.poll(enabled, DarkClientState.state.charge() > 0, light, heavy, tick);
        if (action != DarkHeldInput.Action.NONE) strike(action == DarkHeldInput.Action.HEAVY);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void keyboard(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            SCANCODES.remove(event.getScanCode()); BLOCKED_SCANCODES.remove(event.getScanCode());
        } else if (event.getAction() == GLFW.GLFW_PRESS) {
            if (controlsEnabled(Minecraft.m_91087_())) SCANCODES.add(event.getScanCode());
            else BLOCKED_SCANCODES.add(event.getScanCode());
            if (InspectKey.INSPECT_KEY.m_90832_(event.getKey(),event.getScanCode())) inspect(false);
        }
    }

    /** TaCZ's normal-priority H handler goes first; compensate only a lost input. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void inspectKey(InputEvent.Key event) {
        if (event.getAction()==GLFW.GLFW_PRESS && InspectKey.INSPECT_KEY.m_90832_(event.getKey(),event.getScanCode())
            && InspectKey.INSPECT_KEY.getKeyModifier().isActive(InspectKey.INSPECT_KEY.getKeyConflictContext())) inspect(true);
    }

    private static void inspect(boolean requested) {
        Minecraft mc=Minecraft.m_91087_();
        if (mc.f_91074_==null) return;
        var held=mc.f_91074_.m_21205_();
        var tag=held.m_41783_();
        // TaCZ's getGunId may create a tag; keep unrelated or malformed stacks
        // untouched before invoking any native display or gameplay accessor.
        if (tag==null || !tag.m_128425_("GunId",8)
            || !DarkHeldInput.isDarkWeapon(DarkMeleeRules.canonical(tag.m_128461_("GunId")))) return;
        boolean allowed=controlsEnabled(mc) && DarkClientState.state.charge()==0 && mc.f_91073_.m_46467_()>=inspectReadyAt;
        TimelessAPI.getGunDisplay(mc.f_91074_.m_21205_()).ifPresent(display -> {
            var machine=display.getAnimationStateMachine();
            if (machine==null) return;
            var context=machine.getContext();
            if (context==null || !machine.isInitialized()) context=new GunAnimationStateContext();
            context.setCurrentGunItem(mc.f_91074_.m_21205_());
            context.setPartialTicks(mc.m_91296_());
            if (requested) DarkInspect.request(machine,context,allowed);
            else DarkInspect.prepare(machine,context,allowed);
        });
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void screenOpening(ScreenEvent.Opening event) {
        INPUT.reset(); BLOCKED_SCANCODES.addAll(SCANCODES); SCANCODES.clear();
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc=Minecraft.m_91087_();
        if (mc.f_91074_!=null && DarkSkills.grade(mc.f_91074_.m_21205_())>=0 && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }

    private static void strike(boolean heavy) {
        Minecraft mc=Minecraft.m_91087_();
        if (!controlsEnabled(mc) || DarkClientState.state.charge()>0) return;
        if (heavy || time(mc)-lastStrike>40) nextCombo=0;
        boolean second=!heavy && nextCombo++%2==1;
        double now=time(mc);
        lastStrike=now;
        inspectReadyAt=mc.f_91073_.m_46467_()+(heavy?20:8);
        inspect(false);
        DarkCombat.request(heavy);
        TimelessAPI.getGunDisplay(mc.f_91074_.m_21205_()).ifPresent(display -> {
            var machine=display.getAnimationStateMachine();
            if (machine!=null && machine.isInitialized() && machine.getContext()!=null) {
                var context=machine.getContext();
                context.runAnimation(heavy ? "melee_3" : second ? "melee_2" : "melee_1", context.getTrack(0,1), false, 1, .04f);
            }
            ResourceLocation sound=display.getSounds(heavy ? "heavy" : "shoot");
            if (sound!=null) SoundPlayManager.playClientSound(mc.f_91074_,sound,1,1,16);
        });
        SAMPLES.clear();
        attackStart=now+(!heavy?(second?3:1):7);
        attackEnd=now+(!heavy?(second?10:5):11);
    }

    @SubscribeEvent public static void beforeHand(BeforeRenderHandEvent event) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || DarkSkills.grade(mc.f_91074_.m_21205_()) < 0 || mc.m_91104_()) { SAMPLES.clear(); return; }
        TimelessAPI.getGunDisplay(mc.f_91074_.m_21205_()).ifPresent(display -> {
            BedrockGunModel current = display.getGunModel();
            if (current == null) return;
            if (current != model) { model=current; DarkNativeHands.bind(current); queued=false; chargePlaying=false; SAMPLES.clear(); }
            var machine=display.getAnimationStateMachine();
            if (machine != null && machine.isInitialized() && machine.getContext() != null) {
                var context=machine.getContext();
                boolean charging=DarkClientState.state.charge()>0;
                if (charging && !chargePlaying && context.hasAnimationPrototype("skill_charge"))
                    context.runAnimation("skill_charge",context.getTrack(0,7),true,2,.08f);
                if (!charging && chargePlaying) context.stopAnimation(context.getTrack(0,7));
                chargePlaying=charging;
            }
            if (queued) return;
            queued=true;
            current.delegateRender((stack, ignored, context, light, overlay) -> {
                try {
                    if (model != current || (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) return;
                    drawTrail(mc, current, stack, overlay);
                } finally { queued=false; }
            });
        });
    }

    private static Vector3f anchor(BedrockGunModel model, String name) {
        BedrockPart part = model.getNode(name);
        if (part == null) return null;
        List<BedrockPart> chain = new ArrayList<>();
        for (BedrockPart p=part; p!=null; p=p.getParent()) chain.add(p);
        Collections.reverse(chain);
        PoseStack stack = new PoseStack();
        for (BedrockPart p : chain) p.translateAndRotateAndScale(stack);
        return stack.m_85850_().m_252922_().transformPosition(new Vector3f());
    }

    private static void drawTrail(Minecraft mc, BedrockGunModel current, PoseStack stack, int overlay) {
        double now=time(mc);
        if (now < attackStart || now > attackEnd || mc.f_91074_ == null || DarkSkills.grade(mc.f_91074_.m_21205_()) < 0) { SAMPLES.clear(); return; }
        Vector3f base=anchor(current, "blade_base"), tip=anchor(current, "blade_tip");
        if (base == null || tip == null) return;
        SAMPLES.removeIf(sample -> now-sample.time > 3 || now < sample.time);
        if (SAMPLES.isEmpty() || now-SAMPLES.get(SAMPLES.size()-1).time > .15) SAMPLES.add(new Sample(now, base, tip));
        if (SAMPLES.size() > 20) SAMPLES.remove(0);
        if (SAMPLES.size() < 2) return;
        RenderType type = RenderType.m_110473_(TRAILS[Math.max(0, Math.min(2, DarkSkills.grade(mc.f_91074_.m_21205_())))]);
        var buffers=mc.m_91269_().m_110104_();
        VertexConsumer consumer=buffers.m_6299_(type);
        Matrix4f matrix=stack.m_85850_().m_252922_();
        for (int i=1; i<SAMPLES.size(); i++) {
            Sample a=SAMPLES.get(i-1), b=SAMPLES.get(i);
            float alpha=(float)Math.max(0, Math.min(.65, 1-(now-a.time)/3));
            vertex(consumer, matrix, a.base, 0, 1, alpha, overlay);
            vertex(consumer, matrix, a.tip, 0, 0, alpha, overlay);
            vertex(consumer, matrix, b.tip, 1, 0, alpha, overlay);
            vertex(consumer, matrix, b.base, 1, 1, alpha, overlay);
        }
        buffers.m_109912_(type);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vector3f point, float u, float v, float alpha, int overlay) {
        Vector3f p=matrix.transformPosition(new Vector3f(point));
        buffer.m_5483_(p.x, p.y, p.z).m_85950_(1, 1, 1, alpha).m_7421_(u, v)
            .m_86008_(overlay).m_85969_(15728880).m_5601_(0, 1, 0).m_5752_();
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.m_91087_();
        if (mc.f_91074_ == null || DarkSkills.grade(mc.f_91074_.m_21205_()) != 2 || mc.f_91066_.f_92062_) return;
        var state=DarkClientState.state;
        int width=event.getWindow().m_85445_(), height=event.getWindow().m_85446_();
        var g=event.getGuiGraphics();
        boolean active=state.active()>0, ready=state.cooldown()==0 && state.charge()==0;
        String icon=state.charge()>0 || active ? "skill_active" : ready ? "skill_canactive" : "skill_unactive";
        float scale=Math.min(.5f, Math.max(.2f, (width-8)/260f));
        g.m_280168_().m_85836_();
        try {
            g.m_280168_().m_252880_(Math.max(4,width-260*scale-8), Math.max(4,height-110*scale-8), 400);
            g.m_280168_().m_85841_(scale,scale,1);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            g.m_280163_(texture(icon),0,0,0,0,76,90,210,90);
            String label=state.charge()>0 ? "力量解放 · 蓄力" : active ? "力量解放 · 强化" : ready ? "V · 力量解放" : "力量解放 · 冷却";
            g.m_280056_(mc.f_91062_,label,82,35,0xFFE4CBFF,false);
            float fraction=active ? state.active()/(float)Math.max(1,state.maxActive()) : ready ? 1 : 1-state.cooldown()/(float)Math.max(1,state.maxCooldown());
            fraction=Math.max(0,Math.min(1,fraction));
            g.m_280509_(82,60,240,66,0xAA261B38);
            g.m_280509_(82,60,82+Math.round(158*fraction),66,0xFFAE75F4);
            int seconds=(Math.max(state.charge(), active?state.active():state.cooldown())+19)/20;
            g.m_280056_(mc.f_91062_,ready ? "就绪" : seconds+"s",82,72,0xFFDDD2E8,false);
            if (state.shield()>0) g.m_280056_(mc.f_91062_,String.format("护盾 %.1f / %.1f",state.shield(),state.shieldMax()),82,92,0xFFBBE5FF,false);
        } finally { g.m_280168_().m_85849_(); RenderSystem.setShaderColor(1,1,1,1); }
    }
}
