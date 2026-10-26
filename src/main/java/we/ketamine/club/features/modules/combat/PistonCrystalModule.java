package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.PistonCrystalCalculation;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.traits.Util;

public class PistonCrystalModule extends Module {
   private static final String ROTATION_ID = "PistonCrystal";
   private static final String BREAK_ROTATION_ID = "PistonCrystal_break";
   private static final int CONTRAPTION_PROTECT_TICKS = 60;
   private static final double TARGET_RANGE = 10.0;
   private static final double TARGET_RANGE_SQ = 100.0;
   private static final double PLACE_RANGE = 5.0;
   private static final double PLACE_RANGE_SQ = 25.0;
   private static final double BREAK_RANGE = 4.5;
   private static final double BREAK_RANGE_SQ = 20.25;
   private static final double NUDGE = 0.0625;
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0);
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 8.0, 0.0, 36.0);
   private final Setting<Integer> delay = this.register(new Setting<>("Delay", 1, 0, 20, "Ticks between new piston setups."));
   private final Setting<Boolean> autoBase = this.bool("AutoBase", true);
   private final Setting<Boolean> chain = this.register(
      new Setting<>("Chain", true, "Push one crystal through a sealed trap's corner using two perpendicular pistons.")
   );
   private final Setting<Boolean> pauseOnEat = this.bool("PauseOnEat", true);
   private final Setting<Boolean> rotate = this.bool("Rotate", true);
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F);
   private final Setting<Color> fillColor = this.color("FillColor", 0, 160, 255, 120);
   private final Setting<Color> outlineColor = this.color("OutlineColor", 0, 160, 255, 255);
   private final Map<BlockPos, Integer> renderMap = new HashMap<>();
   private final Map<BlockPos, Integer> placedContraption = new HashMap<>();
   private int delayTicks;
   private float lastDamage;
   private BlockPos lastHead;
   private int breakScanTicks;
   private BlockPos pendingCrystalSource;
   private int pendingCrystalId = -1;
   private Level sessionLevel;
   private Player sessionPlayer;
   private PistonCrystalModule.ChainSession activeChain;
   private final PistonChainBreaker chainBreaker = new PistonChainBreaker();
   private TargetsModule targetsModule;
   private final PistonChain.World chainWorld = new PistonChain.World() {
      {
         Objects.requireNonNull(PistonCrystalModule.this);
      }

      @Override
      public PistonChain.Cell at(BlockPos pos) {
         BlockState state = Util.mc.level.getBlockState(pos);
         if (state.getBlock() instanceof PistonBaseBlock) {
            return new PistonChain.Cell(
               PistonChain.Kind.PISTON, (Direction)state.getValue(PistonBaseBlock.FACING), (Boolean)state.getValue(PistonBaseBlock.EXTENDED)
            );
         } else {
            PistonChain.Kind kind = state.isAir()
               ? PistonChain.Kind.AIR
               : (
                  state.is(Blocks.PISTON_HEAD)
                     ? PistonChain.Kind.HEAD
                     : (
                        state.is(Blocks.MOVING_PISTON)
                           ? PistonChain.Kind.MOVING
                           : (
                              state.is(Blocks.REDSTONE_BLOCK)
                                 ? PistonChain.Kind.REDSTONE
                                 : (PistonCrystalCalculation.isCrystalBase(Util.mc.level, pos) ? PistonChain.Kind.BASE : PistonChain.Kind.BLOCKED)
                           )
                     )
               );
            Direction facing = kind != PistonChain.Kind.HEAD && kind != PistonChain.Kind.MOVING ? null : (Direction)state.getValue(PistonBaseBlock.FACING);
            return new PistonChain.Cell(kind, facing, false);
         }
      }

      @Override
      public boolean canPlace(BlockPos pos) {
         return PistonCrystalModule.this.validChainCell(pos) && PlaceUtil.canPlace(pos);
      }

      @Override
      public boolean canBreakPiston(BlockPos pos) {
         return PistonCrystalModule.this.chainBreaker.canBreak(pos);
      }

      @Override
      public boolean powered(BlockPos pos, Direction facing) {
         for (Direction side : Direction.values()) {
            if (side != facing && Util.mc.level.hasSignal(pos.relative(side), side)) {
               return true;
            }

            if (side != Direction.DOWN && Util.mc.level.hasSignal(pos.above().relative(side), side)) {
               return true;
            }
         }

         return Util.mc.level.hasSignal(pos, Direction.DOWN);
      }

      @Override
      public Vec3 crystalPosition() {
         return PistonCrystalModule.this.pendingCrystalId >= 0
               && Util.mc.level.getEntity(PistonCrystalModule.this.pendingCrystalId) instanceof EndCrystal crystal
               && crystal.isAlive()
            ? crystal.position()
            : null;
      }

      @Override
      public Vec3 collide(AABB box, Vec3 movement, Set<BlockPos> plannedBlocks) {
         List<VoxelShape> extra = new ArrayList<>(plannedBlocks.size());

         for (BlockPos pos : plannedBlocks) {
            extra.add(Shapes.create(new AABB(pos)));
         }

         return Entity.collideBoundingBox(CollisionContext.empty(), movement, box, Util.mc.level, extra);
      }

      @Override
      public double placementCost(BlockPos pos) {
         return Util.mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
      }
   };
   private final MutableBlockPos rayCursor = new MutableBlockPos();

   public PistonCrystalModule() {
      super("PistonCrystal", "Pushes a crystal over a surrounded target with a piston and explodes it.", Module.Category.COMBAT);

      for (Setting<?> setting : new Setting[]{this.minDamage, this.maxSelfDamage, this.delay, this.autoBase, this.chain, this.pauseOnEat, this.rotate}) {
         setting.setPage("General");
      }

      for (Setting<?> setting : new Setting[]{this.render, this.fadeTime, this.fillColor, this.outlineColor}) {
         setting.setPage("Render");
      }
   }

   @Override
   public void onEnable() {
      this.resetState();
      this.delayTicks = this.delay.getValue();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.chainBreaker.close();
      KetamineClub.rotationManager.cancel("PistonCrystal");
      KetamineClub.rotationManager.cancel("PistonCrystal_break");
      this.delayTicks = 0;
      this.lastDamage = 0.0F;
      this.clearBreakTracking();
      this.activeChain = null;
      this.sessionLevel = null;
      this.sessionPlayer = null;
      this.renderMap.clear();
      this.placedContraption.clear();
   }

   @Subscribe(
      priority = -10
   )
   private void onTick(TickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         if (this.sessionLevel != mc.level || this.sessionPlayer != mc.player) {
            this.resetState();
            this.sessionLevel = mc.level;
            this.sessionPlayer = mc.player;
            this.delayTicks = this.delay.getValue();
         }

         if (!PingBypassInput.isScreenOpen() && mc.player.containerMenu.containerId == 0 && !this.shouldPause()) {
            int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
            int now = mc.player.tickCount;
            this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeTicks);
            this.placedContraption.entrySet().removeIf(e -> now - e.getValue() > 60);
            if (this.activeChain != null) {
               this.continueChain();
            } else {
               if (this.lastHead != null && this.breakScanTicks > 0) {
                  this.breakCrystalsAround(this.lastHead);
                  this.breakScanTicks--;
                  if (this.breakScanTicks <= 0) {
                     this.clearBreakTracking();
                  }
               }

               if (this.delayTicks < this.delay.getValue()) {
                  this.delayTicks++;
               } else if (KetamineClub.placementManager.canPistonAct(true)) {
                  Item pistonItem = this.getPistonItem();
                  if (pistonItem != null && this.hasItem(Items.REDSTONE_BLOCK) && this.hasItem(Items.END_CRYSTAL)) {
                     PistonCrystalModule.Setup setup = this.findSetup();
                     if (setup == null) {
                        this.clearPending();
                     } else if (!this.breakCrystalsAround(setup.head())) {
                        if (setup.chain() != null) {
                           this.clearBreakTracking();
                           this.activeChain = new PistonCrystalModule.ChainSession(
                              setup, new PistonChain.Execution(setup.chain(), System.currentTimeMillis(), this.chainTimeoutMs()), setup.chain().blocks()
                           );
                           this.continueChain();
                        } else {
                           float targetYaw = setup.dir().toYRot();
                           float targetPitch = 0.0F;
                           if (!this.rotate.getValue()
                              || KetamineClub.rotationManager
                                 .submit(new RotationRequest("PistonCrystal", 70, targetYaw, targetPitch, RotationRequest.Mode.SILENT, false, true))) {
                              this.place(setup, pistonItem);
                           }
                        }
                     }
                  } else {
                     this.clearPending();
                     this.lastDamage = 0.0F;
                  }
               }
            }
         } else {
            this.clearPending();
            this.clearBreakTracking();
         }
      } else {
         this.resetState();
      }
   }

   private void clearPending() {
      this.chainBreaker.close();
      KetamineClub.rotationManager.cancel("PistonCrystal");
      this.activeChain = null;
      this.lastDamage = 0.0F;
   }

   private long chainTimeoutMs() {
      PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
      int ping = info == null ? 0 : Math.max(0, info.getLatency());
      return Math.clamp(500L + ping * 3L, 1000L, 5000L);
   }

   private void continueChain() {
      PistonCrystalModule.ChainSession session = this.activeChain;
      PistonCrystalModule.Setup setup = session.setup();
      if (!(this.chain.getValue() && mc.level.getEntity(setup.targetId()) instanceof Player target)
         || !target.isAlive()
         || target.position().distanceToSqr(setup.targetPosition()) > 1.0
         || KetamineClub.friendManager.isFriend(target)
         || this.targetsModule != null && !this.targetsModule.isValidPlayerTarget(target)) {
         this.abortChain();
      } else if (!this.chainBreaker.tick()) {
         this.abortChain();
      } else {
         Difficulty difficulty = mc.level.getDifficulty();
         if (!(
               this.calcDamage(target, this.profileOf(target), difficulty, setup.explosionPos(), setup.placeBase() ? setup.base() : null)
                  < this.minDamage.getValue()
            )
            && !(this.calcDamage(mc.player, this.profileOf(mc.player), difficulty, setup.explosionPos(), null) > this.maxSelfDamage.getValue())) {
            long now = System.currentTimeMillis();
            PistonChain.Action action = session.execution().next(this.chainWorld, now);
            if (action == PistonChain.Action.FAILED) {
               this.abortChain();
            } else if (action == PistonChain.Action.DONE) {
               int tick = mc.player.tickCount;
               this.renderMap.put(setup.chain().firstPiston(), tick);
               this.renderMap.put(setup.chain().secondPiston(), tick);
               this.lastHead = setup.head();
               this.breakScanTicks = Math.max(3, (int)(this.chainTimeoutMs() / 50L));
               this.delayTicks = 0;
               this.activeChain = null;
            } else if (action != PistonChain.Action.WAIT
               && KetamineClub.placementManager.canPistonAct(action != PistonChain.Action.CRYSTAL && action != PistonChain.Action.REMOVE_FIRST_PISTON)) {
               PistonChain.Plan plan = setup.chain();

               boolean sent = switch (action) {
                  case FIRST_PISTON -> this.placeChainPiston(plan.firstPiston(), plan.firstPush());
                  case SECOND_PISTON -> this.placeChainPiston(plan.secondPiston(), plan.secondPush());
                  case FIRST_POWER -> this.placeBlockDirect(plan.firstPower(), Items.REDSTONE_BLOCK);
                  case REMOVE_FIRST_PISTON -> this.chainBreaker.start(plan.firstPiston(), this.rotate.getValue());
                  case BASE -> this.autoBase.getValue() && this.placeBlockDirect(plan.base(), Items.OBSIDIAN);
                  case CRYSTAL -> {
                     this.pendingCrystalSource = plan.crystal();
                     this.pendingCrystalId = -1;
                     boolean placed = this.placeCrystalDirect(plan.base(), Items.END_CRYSTAL);
                     if (placed) {
                        this.renderMap.put(plan.crystal(), mc.player.tickCount);
                     } else {
                        this.pendingCrystalSource = null;
                     }

                     yield placed;
                  }
                  case SECOND_POWER -> this.placeBlockDirect(plan.secondPower(), Items.REDSTONE_BLOCK);
                  default -> false;
               };
               if (sent) {
                  session.execution().sent(action, now);
               }
            }
         } else {
            this.abortChain();
         }
      }
   }

   private boolean placeChainPiston(BlockPos pos, Direction facing) {
      Item item = this.getPistonItem();
      if (item == null) {
         return false;
      } else {
         if (this.rotate.getValue()) {
            Direction look = facing.getOpposite();
            float yaw = look.getAxis().isVertical() ? KetamineClub.rotationManager.getServerYaw() : look.toYRot();
            float pitch = look == Direction.UP ? -90.0F : (look == Direction.DOWN ? 90.0F : 0.0F);
            if (!KetamineClub.rotationManager.submit(new RotationRequest("PistonCrystal", 70, yaw, pitch, RotationRequest.Mode.SILENT, false, true))) {
               return false;
            }
         }

         Vec3 look = Vec3.directionFromRotation(KetamineClub.rotationManager.getServerPitch(), KetamineClub.rotationManager.getServerYaw());
         return Direction.getApproximateNearest(look).getOpposite() != facing ? false : this.placeBlockDirect(pos, item);
      }
   }

   private void abortChain() {
      this.clearPending();
      this.clearBreakTracking();
      this.delayTicks = 0;
   }

   private boolean validChainCell(BlockPos pos) {
      if (!mc.level.isOutsideBuildHeight(pos) && mc.level.getWorldBorder().isWithinBounds(pos)) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         return mine == null || !mine.isEnabled() || !mine.alreadyBreaking(pos);
      } else {
         return false;
      }
   }

   private void place(PistonCrystalModule.Setup setup, Item pistonItem) {
      if (!this.shouldPause()) {
         int blocks = 1 + (setup.placeBase() ? 1 : 0) + (setup.placeRedstone() ? 1 : 0);
         if (KetamineClub.placementManager.placementWindowAvailable(blocks)) {
            if (setup.placeBase()) {
               if (!this.hasItem(Items.OBSIDIAN)) {
                  return;
               }

               if (!this.placeBlockDirect(setup.base(), Items.OBSIDIAN)) {
                  return;
               }
            }

            if (this.placeBlockDirect(setup.piston(), pistonItem)) {
               if (!setup.placeRedstone() || this.placeBlockDirect(setup.redstone(), Items.REDSTONE_BLOCK)) {
                  this.pendingCrystalSource = setup.crystal();
                  this.pendingCrystalId = -1;
                  if (!this.placeCrystalDirect(setup.base(), Items.END_CRYSTAL)) {
                     this.pendingCrystalSource = null;
                  } else {
                     this.renderMap.put(setup.crystal(), mc.player.tickCount);
                     this.lastHead = setup.head();
                     this.breakScanTicks = 3;
                     this.delayTicks = 0;
                  }
               }
            }
         }
      }
   }

   private boolean placeBlockDirect(BlockPos pos, Item item) {
      if (this.shouldPause()) {
         return false;
      } else {
         Result result = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
         if (result.found() && KetamineClub.placementManager.placePistonBlock(pos, null, result.slot())) {
            this.renderMap.put(pos, mc.player.tickCount);
            this.placedContraption.put(pos, mc.player.tickCount);
            this.delayTicks = 0;
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean placeCrystalDirect(BlockPos base, Item crystalItem) {
      if (this.shouldPause()) {
         return false;
      } else {
         Result result = InventoryUtil.find(crystalItem, InventoryUtil.PLACE_SCOPE);
         return result.found() && KetamineClub.placementManager.placePistonCrystal(base, result.slot(), false);
      }
   }

   private boolean shouldPause() {
      if (!this.pauseOnEat.getValue()) {
         return false;
      } else {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         return mc.player.isUsingItem() || offhand != null && offhand.shouldDeferForEat();
      }
   }

   private void clearBreakTracking() {
      this.lastHead = null;
      this.breakScanTicks = 0;
      this.pendingCrystalSource = null;
      this.pendingCrystalId = -1;
   }

   public boolean isOwnPistonBlock(BlockPos pos) {
      if (pos != null && !this.isDisabled() && mc.player != null && this.sessionLevel == mc.level) {
         if (this.activeChain != null && this.activeChain.blocks().contains(pos)) {
            return true;
         } else {
            Integer tick = this.placedContraption.get(pos);
            return tick != null && mc.player.tickCount - tick <= 60;
         }
      } else {
         return false;
      }
   }

   public void onCrystalAdded(EndCrystal crystal) {
      if (crystal != null && !this.isDisabled() && this.sessionLevel == mc.level) {
         if (this.pendingCrystalId < 0 && this.pendingCrystalSource != null && crystal.blockPosition().equals(this.pendingCrystalSource)) {
            this.pendingCrystalId = crystal.getId();
         }
      }
   }

   public boolean isChainCrystalProtected(EndCrystal crystal) {
      if (crystal != null && !this.isDisabled() && this.sessionLevel == mc.level && (this.activeChain != null || this.breakScanTicks > 0)) {
         return this.pendingCrystalId >= 0
            ? crystal.getId() == this.pendingCrystalId
            : this.pendingCrystalSource != null && crystal.blockPosition().equals(this.pendingCrystalSource);
      } else {
         return false;
      }
   }

   private boolean breakCrystalsAround(BlockPos head) {
      if (this.shouldPause()) {
         return false;
      } else {
         Vec3 eye = mc.player.getEyePosition();
         AABB area = new AABB(head).inflate(1.5);
         boolean found = false;

         for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, area, e -> e.isAlive())) {
            found = true;
            if (!(distSqToBox(eye, crystal.getBoundingBox()) > 20.25)) {
               this.breakCrystal(crystal);
            }
         }

         return found;
      }
   }

   private void breakCrystal(EndCrystal crystal) {
      if (!this.shouldPause()) {
         AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (autoCrystal != null && autoCrystal.isEnabled()) {
            autoCrystal.breakCrystalForPistonCrystal(crystal);
         } else {
            Vec3 eyePos = mc.player.getEyePosition(1.0F);
            Vec3 hit = this.closestPointOnBox(eyePos, crystal.getBoundingBox());
            float[] angles = MathUtil.calcAngle(eyePos, hit);
            Runnable attackAction = () -> {
               ClientPacketListener conn = mc.getConnection();
               if (conn != null) {
                  conn.send(new ServerboundAttackPacket(crystal.getId()));
                  conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
               }
            };
            if (!this.rotate.getValue()
               || KetamineClub.rotationManager
                  .submit(new RotationRequest("PistonCrystal_break", 60, angles[0], angles[1], RotationRequest.Mode.SILENT, false, true))) {
               attackAction.run();
            }
         }
      }
   }

   private PistonCrystalModule.Setup findSetup() {
      Player target = this.findTarget();
      if (target == null) {
         this.lastDamage = 0.0F;
         return null;
      } else {
         PistonCrystalModule.ArmorProfile targetProfile = this.profileOf(target);
         PistonCrystalModule.ArmorProfile selfProfile = this.profileOf(mc.player);
         Difficulty diff = mc.level.getDifficulty();
         Vec3 eye = mc.player.getEyePosition();
         AABB bb = target.getBoundingBox();
         int minX = Mth.floor(bb.minX);
         int maxX = Mth.floor(bb.maxX - 1.0E-7);
         int minY = Mth.floor(bb.minY);
         int maxY = Mth.floor(bb.maxY - 1.0E-7);
         int minZ = Mth.floor(bb.minZ);
         int maxZ = Mth.floor(bb.maxZ - 1.0E-7);
         PistonCrystalModule.Setup best = null;
         MutableBlockPos cursor = new MutableBlockPos();
         boolean lowPose = target.getBbHeight() <= 1.0F;

         for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
               for (int bz = minZ; bz <= maxZ; bz++) {
                  cursor.set(bx, by, bz);
                  BlockPos cell = cursor.immutable();
                  BlockPos[] heads = lowPose ? new BlockPos[]{cell, cell.above()} : new BlockPos[]{cell.above()};

                  for (BlockPos head : heads) {
                     for (Direction dir : Plane.HORIZONTAL) {
                        PistonCrystalModule.Setup side = this.sideSetup(target, targetProfile, selfProfile, diff, head, dir, eye);
                        if (side != null && this.betterSetup(side, best)) {
                           best = side;
                        }

                        PistonCrystalModule.Setup top = this.topSetup(target, targetProfile, selfProfile, diff, head, dir, eye);
                        if (top != null && this.betterSetup(top, best)) {
                           best = top;
                        }
                     }

                     if (this.chain.getValue() && head.getY() <= maxY) {
                        for (Direction outside : Plane.HORIZONTAL) {
                           for (Direction sidex : new Direction[]{outside.getClockWise(), outside.getCounterClockWise()}) {
                              PistonCrystalModule.Setup corner = this.cornerSetup(target, targetProfile, selfProfile, diff, head, outside, sidex, eye);
                              if (corner != null && this.betterSetup(corner, best)) {
                                 best = corner;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         this.lastDamage = best != null ? best.damage() : 0.0F;
         return best;
      }
   }

   private boolean betterSetup(PistonCrystalModule.Setup candidate, PistonCrystalModule.Setup current) {
      return current == null
         ? true
         : candidate.damage() - (candidate.chain() == null ? 0.0F : 0.5F) > current.damage() - (current.chain() == null ? 0.0F : 0.5F);
   }

   private PistonCrystalModule.Setup cornerSetup(
      Player target,
      PistonCrystalModule.ArmorProfile targetProfile,
      PistonCrystalModule.ArmorProfile selfProfile,
      Difficulty difficulty,
      BlockPos head,
      Direction firstOutside,
      Direction secondOutside,
      Vec3 eye
   ) {
      if (this.itemCount(Items.PISTON, Items.STICKY_PISTON) >= 2 && this.itemCount(Items.REDSTONE_BLOCK) >= 2) {
         BlockPos base = head.relative(firstOutside).relative(secondOutside).below();
         boolean placeBase = this.needsBase(base);
         if (placeBase && !this.canAutoBase(base)) {
            return null;
         } else {
            PistonChain.Plan plan = PistonChain.find(head, firstOutside, secondOutside, this.chainWorld);
            if (plan != null && !(distSqToBox(eye, PistonChain.crystalBox(plan.end())) > 20.25)) {
               float damage = this.calcDamage(target, targetProfile, difficulty, plan.end(), placeBase ? base : null);
               return !(damage < this.minDamage.getValue())
                     && !(this.calcDamage(mc.player, selfProfile, difficulty, plan.end(), null) > this.maxSelfDamage.getValue())
                  ? new PistonCrystalModule.Setup(
                     secondOutside,
                     plan.secondPiston(),
                     plan.secondPower(),
                     plan.crystal(),
                     base,
                     head,
                     true,
                     placeBase,
                     damage,
                     plan,
                     target.getId(),
                     target.position(),
                     plan.end()
                  )
                  : null;
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private PistonCrystalModule.Setup sideSetup(
      Player target,
      PistonCrystalModule.ArmorProfile targetProfile,
      PistonCrystalModule.ArmorProfile selfProfile,
      Difficulty diff,
      BlockPos head,
      Direction dir,
      Vec3 eye
   ) {
      BlockPos crystalPos = head.relative(dir);
      BlockPos base = crystalPos.below();
      BlockPos pistonPos = head.relative(dir, 2);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      } else if (!mc.level.getBlockState(crystalPos).isAir()) {
         return null;
      } else if (!mc.level.getBlockState(head).isAir()) {
         return null;
      } else {
         Vec3 explosionPos = new Vec3(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
         return this.buildSetup(target, targetProfile, selfProfile, diff, head, dir, crystalPos, base, pistonPos, explosionPos, eye, placeBase);
      }
   }

   private PistonCrystalModule.Setup topSetup(
      Player target,
      PistonCrystalModule.ArmorProfile targetProfile,
      PistonCrystalModule.ArmorProfile selfProfile,
      Difficulty diff,
      BlockPos head,
      Direction dir,
      Vec3 eye
   ) {
      BlockPos base = head.relative(dir);
      BlockPos crystalPos = base.above();
      BlockPos pistonPos = crystalPos.relative(dir);
      boolean placeBase = this.needsBase(base);
      if (placeBase && !this.canAutoBase(base)) {
         return null;
      } else if (!mc.level.getBlockState(crystalPos).isAir()) {
         return null;
      } else {
         BlockPos dest = head.above();
         Vec3 explosionPos = mc.level.getBlockState(dest).isAir()
            ? new Vec3(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5)
            : new Vec3(crystalPos.getX() + 0.5 - dir.getStepX() * 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5 - dir.getStepZ() * 0.5);
         return this.buildSetup(target, targetProfile, selfProfile, diff, head, dir, crystalPos, base, pistonPos, explosionPos, eye, placeBase);
      }
   }

   private boolean needsBase(BlockPos base) {
      return !PistonCrystalCalculation.isCrystalBase(mc.level, base);
   }

   private boolean canAutoBase(BlockPos base) {
      return this.autoBase.getValue() && this.hasItem(Items.OBSIDIAN) && PlaceUtil.canPlace(base);
   }

   private PistonCrystalModule.Setup buildSetup(
      Player target,
      PistonCrystalModule.ArmorProfile targetProfile,
      PistonCrystalModule.ArmorProfile selfProfile,
      Difficulty diff,
      BlockPos head,
      Direction dir,
      BlockPos crystalPos,
      BlockPos base,
      BlockPos pistonPos,
      Vec3 explosionPos,
      Vec3 eye,
      boolean placeBase
   ) {
      if (!PlaceUtil.canPlace(pistonPos)) {
         return null;
      } else if (placeBase && !PlaceUtil.canPlace(base)) {
         return null;
      } else {
         AABB crystalBox = new AABB(
            explosionPos.x - 1.0, explosionPos.y, explosionPos.z - 1.0, explosionPos.x + 1.0, explosionPos.y + 2.0, explosionPos.z + 1.0
         );
         if (distSqToBox(eye, crystalBox) > 20.25) {
            return null;
         } else {
            float targetDmg = this.calcDamage(target, targetProfile, diff, explosionPos, placeBase ? base : null);
            if (targetDmg < this.minDamage.getValue().floatValue()) {
               return null;
            } else {
               float selfDmg = this.calcDamage(mc.player, selfProfile, diff, explosionPos, null);
               if (selfDmg > this.maxSelfDamage.getValue().floatValue()) {
                  return null;
               } else {
                  PistonCrystalModule.RedstoneSpot redstone = this.findRedstoneSpot(pistonPos, dir, explosionPos, eye);
                  return redstone == null
                     ? null
                     : new PistonCrystalModule.Setup(
                        dir,
                        pistonPos,
                        redstone.pos(),
                        crystalPos,
                        base,
                        head,
                        redstone.place(),
                        placeBase,
                        targetDmg,
                        null,
                        target.getId(),
                        target.position(),
                        explosionPos
                     );
               }
            }
         }
      }
   }

   private int itemCount(Item... items) {
      int count = 0;

      for (int slot = 0; slot < 36; slot++) {
         ItemStack stack = mc.player.getInventory().getItem(slot);

         for (Item item : items) {
            if (stack.is(item)) {
               count += stack.getCount();
               break;
            }
         }
      }

      return count;
   }

   private PistonCrystalModule.RedstoneSpot findRedstoneSpot(BlockPos pistonPos, Direction dir, Vec3 explosionPos, Vec3 eye) {
      BlockPos above = pistonPos.above();
      BlockPos inDir = pistonPos.relative(dir);
      BlockPos cw = pistonPos.relative(dir.getClockWise());
      BlockPos ccw = pistonPos.relative(dir.getCounterClockWise());
      BlockPos below = pistonPos.below();
      if (mc.level.getBlockState(above).is(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(above, false);
      } else if (mc.level.getBlockState(inDir).is(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(inDir, false);
      } else if (mc.level.getBlockState(cw).is(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(cw, false);
      } else if (mc.level.getBlockState(ccw).is(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(ccw, false);
      } else if (mc.level.getBlockState(below).is(Blocks.REDSTONE_BLOCK)) {
         return new PistonCrystalModule.RedstoneSpot(below, false);
      } else {
         BlockPos fallback = null;

         for (BlockPos pos : new BlockPos[]{above, inDir, cw, ccw, below}) {
            if (PlaceUtil.canPlace(pos) && !(eye.distanceToSqr(Vec3.atCenterOf(pos)) > 25.0)) {
               if (this.redstoneSafe(pos, explosionPos)) {
                  return new PistonCrystalModule.RedstoneSpot(pos, true);
               }

               if (fallback == null) {
                  fallback = pos;
               }
            }
         }

         return fallback == null ? null : new PistonCrystalModule.RedstoneSpot(fallback, true);
      }
   }

   private boolean redstoneSafe(BlockPos pos, Vec3 explosionPos) {
      Vec3 to = Vec3.atCenterOf(pos);
      if (explosionPos.distanceToSqr(to) > 36.0) {
         return true;
      } else {
         Vec3 diff = to.subtract(explosionPos);
         int steps = (int)Math.ceil(diff.length() / 0.25);
         long last = Long.MIN_VALUE;

         for (int i = 1; i < steps; i++) {
            double s = (double)i / steps;
            this.rayCursor.set(Mth.floor(explosionPos.x + diff.x * s), Mth.floor(explosionPos.y + diff.y * s), Mth.floor(explosionPos.z + diff.z * s));
            long key = this.rayCursor.asLong();
            if (key != last) {
               last = key;
               if (this.rayCursor.equals(pos)) {
                  break;
               }

               if (mc.level.getBlockState(this.rayCursor).getBlock().getExplosionResistance() >= 600.0F) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private double calcExposure(Vec3 source, AABB box, BlockPos phantomBase) {
      double dx = box.getXsize();
      double dy = box.getYsize();
      double dz = box.getZsize();
      int steps = 2;
      int total = 0;
      int unblocked = 0;

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               Vec3 point = new Vec3(box.minX + dx * xi / steps, box.minY + dy * yi / steps, box.minZ + dz * zi / steps);
               if (!this.explosionBlocked(point, source, phantomBase)) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   private boolean explosionBlocked(Vec3 from, Vec3 to, BlockPos phantomBase) {
      Vec3 diff = to.subtract(from);
      int steps = (int)Math.ceil(diff.length() / 0.25);
      long last = Long.MIN_VALUE;

      for (int i = 1; i < steps; i++) {
         double s = (double)i / steps;
         this.rayCursor.set(Mth.floor(from.x + diff.x * s), Mth.floor(from.y + diff.y * s), Mth.floor(from.z + diff.z * s));
         long key = this.rayCursor.asLong();
         if (key != last) {
            last = key;
            if (phantomBase != null && this.rayCursor.equals(phantomBase)) {
               return true;
            }

            if (mc.level.getBlockState(this.rayCursor).getBlock().getExplosionResistance() >= 600.0F) {
               return true;
            }
         }
      }

      return false;
   }

   private Player findTarget() {
      if (this.targetsModule == null) {
         this.targetsModule = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      }

      Player best = null;
      double bestSq = Double.MAX_VALUE;

      for (Player p : mc.level.players()) {
         if (p != mc.player
            && !p.isDeadOrDying()
            && !KetamineClub.friendManager.isFriend(p)
            && (this.targetsModule == null || this.targetsModule.isValidPlayerTarget(p))
            && !this.isPhased(p)) {
            double dSq = mc.player.distanceToSqr(p);
            if (!(dSq > 100.0) && !(dSq >= bestSq)) {
               bestSq = dSq;
               best = p;
            }
         }
      }

      return best;
   }

   private boolean isPhased(Player target) {
      AABB bb = target.getBoundingBox().deflate(0.001);
      int minX = Mth.floor(bb.minX);
      int maxX = Mth.floor(bb.maxX);
      int minY = Mth.floor(bb.minY);
      int maxY = Mth.floor(bb.maxY);
      int minZ = Mth.floor(bb.minZ);
      int maxZ = Mth.floor(bb.maxZ);
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cursor.set(x, y, z);
               BlockState state = mc.level.getBlockState(cursor);
               if (!state.isAir()) {
                  VoxelShape shape = state.getCollisionShape(mc.level, cursor);
                  if (!shape.isEmpty() && shape.bounds().move(x, y, z).intersects(bb)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private float calcDamage(Player target, PistonCrystalModule.ArmorProfile profile, Difficulty diff, Vec3 explosionPos, BlockPos phantomBase) {
      double distSq = target.position().distanceToSqr(explosionPos);
      if (distSq > 144.0) {
         return 0.0F;
      } else {
         double exposure = this.calcExposure(explosionPos, target.getBoundingBox(), phantomBase);
         if (exposure <= 0.0) {
            return 0.0F;
         } else {
            double impact = (1.0 - Math.sqrt(distSq) / 12.0) * exposure;
            if (impact <= 0.0) {
               return 0.0F;
            } else {
               float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
               switch (diff) {
                  case EASY:
                     damage = Math.min(damage / 2.0F + 1.0F, damage);
                     break;
                  case HARD:
                     damage *= 1.5F;
               }

               float i = 2.0F + profile.toughness() / 4.0F;
               float j = Mth.clamp(profile.armor() - damage / i, profile.armor() * 0.2F, 20.0F);
               damage *= 1.0F - j / 25.0F;
               damage *= profile.resistanceMul();
               damage = CombatRules.getDamageAfterMagicAbsorb(damage, profile.protPoints());
               return Math.max(damage, 0.0F);
            }
         }
      }
   }

   private PistonCrystalModule.ArmorProfile profileOf(Player target) {
      float armor = (float)target.getAttributeValue(Attributes.ARMOR);
      float tough = (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
      float resistanceMul = 1.0F;
      MobEffectInstance res = target.getEffect(MobEffects.RESISTANCE);
      if (res != null) {
         resistanceMul = 1.0F - 0.2F * (res.getAmplifier() + 1);
      }

      int protPoints = 0;
      if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
         protPoints += 4;
      }

      if (!target.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
         protPoints += 4;
      }

      if (!target.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
         protPoints += 8;
      }

      if (!target.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
         protPoints += 4;
      }

      return new PistonCrystalModule.ArmorProfile(armor, tough, resistanceMul, protPoints);
   }

   private Vec3 closestPointOnBox(Vec3 eye, AABB box) {
      boolean minX = eye.x < box.minX;
      boolean maxX = eye.x > box.maxX;
      boolean minZ = eye.z < box.minZ;
      boolean maxZ = eye.z > box.maxZ;
      double x = Mth.clamp(eye.x, box.minX, box.maxX);
      double y = Mth.clamp(eye.y, box.minY, box.maxY);
      double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
      if (minX) {
         x += 0.0625;
      } else if (maxX) {
         x -= 0.0625;
      }

      if (minZ) {
         z += 0.0625;
      } else if (maxZ) {
         z -= 0.0625;
      }

      return new Vec3(x, y, z);
   }

   private static double distSqToBox(Vec3 p, AABB box) {
      double dx = p.x < box.minX ? box.minX - p.x : (p.x > box.maxX ? p.x - box.maxX : 0.0);
      double dy = p.y < box.minY ? box.minY - p.y : (p.y > box.maxY ? p.y - box.maxY : 0.0);
      double dz = p.z < box.minZ ? box.minZ - p.z : (p.z > box.maxZ ? p.z - box.maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private Item getPistonItem() {
      if (this.hasItem(Items.PISTON)) {
         return Items.PISTON;
      } else {
         return this.hasItem(Items.STICKY_PISTON) ? Items.STICKY_PISTON : null;
      }
   }

   private boolean hasItem(Item item) {
      return InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE).found();
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && !nullCheck()) {
         int fadeTicks = (int)(this.fadeTime.getValue() * 20.0F);
         int now = mc.player.tickCount;

         for (Entry<BlockPos, Integer> entry : this.renderMap.entrySet()) {
            int age = now - entry.getValue();
            if (age <= fadeTicks) {
               double t = (double)age / fadeTicks;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               AABB renderBox = new AABB(entry.getKey());
               RenderUtil.drawBoxFilled(event.getMatrix(), renderBox, withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), renderBox, withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record ArmorProfile(float armor, float toughness, float resistanceMul, int protPoints) {
   }

   private record ChainSession(PistonCrystalModule.Setup setup, PistonChain.Execution execution, Set<BlockPos> blocks) {
   }

   private record RedstoneSpot(BlockPos pos, boolean place) {
   }

   private record Setup(
      Direction dir,
      BlockPos piston,
      BlockPos redstone,
      BlockPos crystal,
      BlockPos base,
      BlockPos head,
      boolean placeRedstone,
      boolean placeBase,
      float damage,
      PistonChain.Plan chain,
      int targetId,
      Vec3 targetPosition,
      Vec3 explosionPos
   ) {
   }
}
