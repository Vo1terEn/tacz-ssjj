package cn.judgfire;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.TacHitResult;
import com.tacz.guns.util.block.BlockRayTrace;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(JudgmentFlame.MODID)
public final class JudgmentFlame {
  public static final String MODID = "judgfire_runtime";
  public static final ResourceLocation GUN = new ResourceLocation("judgfire", "judgment_flame");
  public static final ResourceLocation MUZZLE =
      new ResourceLocation("judgfire", "dragon_breath_muzzle");
  public static final ResourceLocation GRIP = new ResourceLocation("judgfire", "red_lotus_grip");
  public static final ResourceLocation STOCK =
      new ResourceLocation("judgfire", "emperor_crown_stock");
  public static final ResourceLocation MAG =
      new ResourceLocation("judgfire", "red_lotus_burst_mag");
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          new ResourceLocation(MODID, "effects"), () -> "8", "8"::equals, "8"::equals);

  public JudgmentFlame() {
    EnergyAttributes.register(FMLJavaModLoadingContext.get().getModEventBus());
    MinecraftForge.EVENT_BUS.addListener(EnergyAttributes::itemModifiers);
    CHANNEL
        .messageBuilder(Shot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(Shot::encode)
        .decoder(Shot::decode)
        .consumerMainThread(
            (p, ctx) ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.accept(p)))
        .add();
    CHANNEL
        .messageBuilder(Mark.class, 1, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(Mark::encode)
        .decoder(Mark::decode)
        .consumerMainThread(
            (p, ctx) ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEffects.mark(p)))
        .add();
    CHANNEL
        .messageBuilder(CrownState.class, 2, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(CrownState::encode)
        .decoder(CrownState::decode)
        .consumerMainThread(
            (p, ctx) ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CrownEffects.state(p)))
        .add();
    CHANNEL
        .messageBuilder(CrownStrike.class, 3, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(CrownStrike::encode)
        .decoder(CrownStrike::decode)
        .consumerMainThread(
            (p, ctx) ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CrownEffects.strike(p)))
        .add();
    CHANNEL
        .messageBuilder(BurstRequest.class, 4, NetworkDirection.PLAY_TO_SERVER)
        .encoder(BurstRequest::encode)
        .decoder(BurstRequest::decode)
        .consumerMainThread((p, ctx) -> BurstRuntime.activate(ctx.get().getSender(), p.weapon()))
        .add();
    CHANNEL
        .messageBuilder(BurstState.class, 5, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(BurstState::encode)
        .decoder(BurstState::decode)
        .consumerMainThread(
            (p, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BurstClient.state(p)))
        .add();
    CHANNEL
        .messageBuilder(CrownToggleRequest.class, 6, NetworkDirection.PLAY_TO_SERVER)
        .encoder(CrownToggleRequest::encode)
        .decoder(CrownToggleRequest::decode)
        .consumerMainThread((p, ctx) -> CrownControl.request(ctx.get().getSender(), p.weapon()))
        .add();
    BurstStun.EFFECTS.register(FMLJavaModLoadingContext.get().getModEventBus());
    BurstStun.SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RedLotus.CONFIG);
    ModLoadingContext.get()
        .registerConfig(ModConfig.Type.COMMON, CombatConfig.SPEC, CombatConfig.FILE);
    MinecraftForge.EVENT_BUS.register(this);
  }

  public static boolean ours(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return gun != null && GUN.equals(gun.getGunId(stack));
  }

  public static boolean laser(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return ours(stack) && MUZZLE.equals(gun.getAttachmentId(stack, AttachmentType.MUZZLE));
  }

  public static boolean lotus(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return ours(stack) && GRIP.equals(gun.getAttachmentId(stack, AttachmentType.GRIP));
  }

  public static boolean crown(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return ours(stack) && STOCK.equals(gun.getAttachmentId(stack, AttachmentType.STOCK));
  }

  public static boolean burst(ItemStack stack) {
    var gun = IGun.getIGunOrNull(stack);
    return ours(stack) && MAG.equals(gun.getAttachmentId(stack, AttachmentType.EXTENDED_MAG));
  }

  @SubscribeEvent
  public void select(GunFireSelectEvent event) {
    if (ours(event.getGunItemStack()) && !laser(event.getGunItemStack())) event.setCanceled(true);
  }

  @SubscribeEvent
  public void migrate(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
    // Existing guns and inventory transfers retain the one-cell charge in their own NBT.
    for (var stack : event.player.getInventory().items) {
      if (!ours(stack)) continue;
      var gun = IGun.getIGunOrNull(stack);
      WeaponSkins.sync(stack);
      if (burst(stack)) EmperorCrown.id(stack);
      int old = gun.getCurrentAmmoCount(stack);
      boolean barrel = gun.hasBulletInBarrel(stack);
      if (old > 1 || old < 0 || barrel) {
        gun.setCurrentAmmoCount(stack, (old > 0 || barrel) ? 1 : 0);
        gun.setBulletInBarrel(stack, false);
      }
      if (!laser(stack) && gun.getFireMode(stack) != FireMode.SEMI)
        gun.setFireMode(stack, FireMode.SEMI);
    }
  }

  @SubscribeEvent
  public void hitBlock(AmmoHitBlockEvent event) {
    var bullet = event.getAmmo();
    if (GUN.equals(bullet.getGunId()) && bullet.getOwner() instanceof LivingEntity shooter)
      sendSnapshot(
          shooter,
          new Shot(
              shooter.getId(),
              false,
              true,
              event.getHitResult().getLocation(),
              event.getHitResult().getLocation(),
              WeaponSkins.DISPLAY.equals(bullet.getGunDisplayId())));
  }

  @SubscribeEvent
  public void hitEntity(EntityHurtByGunEvent.Post event) {
    if (AttackBonuses.calculating(event)) return;
    if (event.getLogicalSide() != LogicalSide.SERVER
        || !GUN.equals(event.getGunId())
        || event.getBullet() instanceof InstantShot) return;
    var shooter = event.getAttacker();
    if (shooter == null) return;
    Vec3 p = event.getHurtEntity().getBoundingBox().getCenter();
    sendSnapshot(
        shooter,
        new Shot(
            shooter.getId(),
            false,
            true,
            p,
            p,
            event.getBullet() instanceof EntityKineticBullet bullet
                && WeaponSkins.DISPLAY.equals(bullet.getGunDisplayId())));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void projectile(EntityJoinLevelEvent event) {
    if (event.getLevel().isClientSide()
        || event.isCanceled()
        || !(event.getEntity() instanceof EntityKineticBullet bullet)
        || !GUN.equals(bullet.getGunId())) return;
    if (!(bullet.getOwner() instanceof LivingEntity shooter)) return;
    ItemStack stack = shooter.getMainHandItem();
    if (!ours(stack)) return;
    bullet.getPersistentData().putBoolean(RedLotus.SHOT_TAG, lotus(stack));
    WeaponDamage.tag(bullet, stack);
    EmperorCrown.tag(bullet, stack);
    BurstRuntime.tag(bullet, stack);
    if (laser(stack) || CombatConfig.weaponThroughWalls()) {
      // Server trace with enlarged entity tolerance; normal-only targets retain TaCZ's hurt hooks.
      // Never spawn a second moving projectile; the beam and native hit agree on one server result.
      var trace = new InstantShot(bullet);
      trace.resolve();
      event.setCanceled(true);
      Vec3 forward = shooter.getViewVector(1).normalize();
      Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
      Vec3 muzzle = trace.start.add(forward.scale(1)).add(right.scale(.22)).add(0, -.25, 0);
      send(shooter, new Shot(shooter.getId(), laser(stack), trace.hit, muzzle, trace.end));
    } else {
      send(shooter, new Shot(shooter.getId(), false, false, bullet.position(), bullet.position()));
    }
  }

  private static void send(LivingEntity shooter, Shot shot) {
    sendSnapshot(
        shooter,
        new Shot(
            shot.shooter(),
            shot.laser(),
            shot.hit(),
            shot.start(),
            shot.end(),
            WeaponSkins.skin(shooter.getMainHandItem())));
  }

  private static void sendSnapshot(LivingEntity shooter, Shot shot) {
    if (shooter.level() instanceof ServerLevel level)
      CHANNEL.send(
          PacketDistributor.NEAR.with(
              () ->
                  new PacketDistributor.TargetPoint(
                      shooter.getX(), shooter.getY(), shooter.getZ(), 300, level.dimension())),
          shot);
  }

  /**
   * Transient collision adapter, not an entity registration or a copy of TaCZ's damage
   * implementation.
   */
  static final class InstantShot extends EntityKineticBullet {
    private static final List<Field> STATE = new ArrayList<>();
    private static final Field PIERCE;

    static {
      try {
        for (Field f : EntityKineticBullet.class.getDeclaredFields())
          if (!Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true);
            STATE.add(f);
          }
        PIERCE = EntityKineticBullet.class.getDeclaredField("pierce");
        PIERCE.setAccessible(true);
      } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    final Vec3 start;
    Vec3 end;
    boolean hit;

    InstantShot(EntityKineticBullet source) {
      super(EntityKineticBullet.TYPE, source.level());
      try {
        for (Field f : STATE) f.set(this, f.get(source));
        PIERCE.setInt(this, 1);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
      setOwner(source.getOwner());
      setPos(source.position());
      getPersistentData().merge(source.getPersistentData());
      setDeltaMovement(source.getDeltaMovement().normalize().scale(256));
      start = position();
      end = start.add(getDeltaMovement());
    }

    private BlockHitResult blocks(Vec3 a, Vec3 b) {
      return BlockRayTrace.rayTraceBlocks(
          level(), new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    void resolve() {
      boolean through = CombatConfig.weaponThroughWalls();
      Vec3 limit = start.add(getDeltaMovement());
      var wall = blocks(start, limit);
      if (!through && wall.getType() != HitResult.Type.MISS) limit = wall.getLocation();
      var nearest = through ? null : EntityUtil.findEntityOnPath(this, start, limit);
      double best =
          nearest == null ? Double.POSITIVE_INFINITY : start.distanceToSqr(nearest.getHitPos());
      for (var target :
          level()
              .getEntities(
                  this,
                  new AABB(start, limit).inflate(LaserCollision.RADIUS),
                  e -> e.isAlive() && e.isPickable() && !e.isSpectator())) {
        if (target == getOwner()
            || getOwner() != null && target.isPassengerOfSameVehicle(getOwner())) continue;
        var point = LaserCollision.contact(target.getBoundingBox(), start, limit);
        if (point.isEmpty()) continue;
        double distance = start.distanceToSqr(point.get());
        if (distance >= best) continue;
        // Inflating a box must not allow a target behind a wall to leak through it.
        if (!through) {
          Vec3 body = LaserCollision.onBody(target.getBoundingBox(), point.get());
          var occlusion = blocks(start, body);
          if (occlusion.getType() != HitResult.Type.MISS
              && start.distanceToSqr(occlusion.getLocation()) + 1e-6 < start.distanceToSqr(body))
            continue;
        }
        nearest = new EntityKineticBullet.EntityResult(target, point.get(), false);
        best = distance;
      }
      if (nearest != null) onHitEntity(new TacHitResult(nearest), start, limit);
      else if (through) {
        end = limit;
        hit = false;
      } else onHitBlock(wall, start, limit);
      discard();
    }

    @Override
    public float getDamage(Vec3 hitPosition) {
      return super.getDamage(hitPosition);
    }

    @Override
    protected void onHitEntity(TacHitResult result, Vec3 a, Vec3 b) {
      end = result.getLocation();
      hit = true;
      super.onHitEntity(result, a, b);
    }

    @Override
    protected void onHitBlock(BlockHitResult result, Vec3 a, Vec3 b) {
      // Use the weapon's original impact sprite instead of a generic bullet-hole particle for laser
      // shots.
      if (result.getType() != HitResult.Type.MISS) {
        end = result.getLocation();
        hit = true;
      }
    }
  }

  public record Shot(int shooter, boolean laser, boolean hit, Vec3 start, Vec3 end, int skin) {
    public Shot {
      if (skin < 0 || skin > 3) throw new IllegalArgumentException("Unknown effect skin: " + skin);
    }

    public Shot(int shooter, boolean laser, boolean hit, Vec3 start, Vec3 end, boolean qinglong) {
      this(shooter, laser, hit, start, end, qinglong ? 1 : 0);
    }

    public boolean qinglong() {
      return skin == 1;
    }

    public boolean frozen() {
      return skin == 2;
    }

    public Shot(int shooter, boolean laser, boolean hit, Vec3 start, Vec3 end) {
      this(shooter, laser, hit, start, end, false);
    }

    static void encode(Shot p, FriendlyByteBuf b) {
      b.writeInt(p.shooter);
      b.writeBoolean(p.laser);
      b.writeBoolean(p.hit);
      for (Vec3 v : new Vec3[] {p.start, p.end}) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
      }
      b.writeByte(p.skin);
    }

    static Shot decode(FriendlyByteBuf b) {
      return new Shot(
          b.readInt(),
          b.readBoolean(),
          b.readBoolean(),
          new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
          new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
          b.readUnsignedByte());
    }
  }

  public record Mark(int target, int duration, int skin) {
    public Mark {
      if (skin < 0 || skin > 3) throw new IllegalArgumentException("Unknown effect skin: " + skin);
    }

    public Mark(int target, int duration, boolean qinglong) {
      this(target, duration, qinglong ? 1 : 0);
    }

    public boolean qinglong() {
      return skin == 1;
    }

    public boolean frozen() {
      return skin == 2;
    }

    public Mark(int target, int duration) {
      this(target, duration, false);
    }

    static void encode(Mark p, FriendlyByteBuf b) {
      b.writeInt(p.target);
      b.writeVarInt(p.duration);
      b.writeByte(p.skin);
    }

    static Mark decode(FriendlyByteBuf b) {
      return new Mark(b.readInt(), b.readVarInt(), b.readUnsignedByte());
    }
  }

  public record CrownState(java.util.UUID weapon, double charge, int tier) {
    static void encode(CrownState p, FriendlyByteBuf b) {
      b.writeUUID(p.weapon);
      b.writeDouble(p.charge);
      b.writeVarInt(p.tier);
    }

    static CrownState decode(FriendlyByteBuf b) {
      return new CrownState(b.readUUID(), b.readDouble(), b.readVarInt());
    }
  }

  public record CrownStrike(Vec3 start, Vec3 end, int tier, int skin) {
    public CrownStrike {
      if (skin < 0 || skin > 3) throw new IllegalArgumentException("Unknown effect skin: " + skin);
    }

    public CrownStrike(Vec3 start, Vec3 end, int tier, boolean qinglong) {
      this(start, end, tier, qinglong ? 1 : 0);
    }

    public boolean qinglong() {
      return skin == 1;
    }

    public boolean frozen() {
      return skin == 2;
    }

    public CrownStrike(Vec3 start, Vec3 end, int tier) {
      this(start, end, tier, false);
    }

    static void encode(CrownStrike p, FriendlyByteBuf b) {
      for (Vec3 v : new Vec3[] {p.start, p.end}) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
      }
      b.writeVarInt(p.tier);
      b.writeByte(p.skin);
    }

    static CrownStrike decode(FriendlyByteBuf b) {
      return new CrownStrike(
          new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
          new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
          b.readVarInt(),
          b.readUnsignedByte());
    }
  }

  public record BurstRequest(java.util.UUID weapon) {
    static void encode(BurstRequest p, FriendlyByteBuf b) {
      b.writeUUID(p.weapon);
    }

    static BurstRequest decode(FriendlyByteBuf b) {
      return new BurstRequest(b.readUUID());
    }
  }

  public record CrownToggleRequest(java.util.UUID weapon) {
    static void encode(CrownToggleRequest p, FriendlyByteBuf b) {
      b.writeUUID(p.weapon);
    }

    static CrownToggleRequest decode(FriendlyByteBuf b) {
      return new CrownToggleRequest(b.readUUID());
    }
  }

  public record BurstState(
      int shooter, java.util.UUID weapon, int remaining, int cooldown, boolean started, int skin) {
    public BurstState {
      if (skin < 0 || skin > 3) throw new IllegalArgumentException("Unknown effect skin: " + skin);
    }

    public BurstState(
        int shooter,
        java.util.UUID weapon,
        int remaining,
        int cooldown,
        boolean started,
        boolean qinglong) {
      this(shooter, weapon, remaining, cooldown, started, qinglong ? 1 : 0);
    }

    public boolean qinglong() {
      return skin == 1;
    }

    public boolean frozen() {
      return skin == 2;
    }

    public BurstState(
        int shooter, java.util.UUID weapon, int remaining, int cooldown, boolean started) {
      this(shooter, weapon, remaining, cooldown, started, false);
    }

    static void encode(BurstState p, FriendlyByteBuf b) {
      b.writeVarInt(p.shooter);
      b.writeUUID(p.weapon);
      b.writeVarInt(p.remaining);
      b.writeVarInt(p.cooldown);
      b.writeBoolean(p.started);
      b.writeByte(p.skin);
    }

    static BurstState decode(FriendlyByteBuf b) {
      return new BurstState(
          b.readVarInt(),
          b.readUUID(),
          b.readVarInt(),
          b.readVarInt(),
          b.readBoolean(),
          b.readUnsignedByte());
    }
  }
}
