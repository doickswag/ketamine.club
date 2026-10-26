package we.ketamine.club.features.modules.funny;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.combat.AutoCrystalModule;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.TntMinecartDamageUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;

public class AutoMinecartModule extends Module {
   private static final String ROTATION_ID = "AutoMinecart";
   private static final int ROTATION_PRIORITY = 65;
   private static final int SWAP_PRIORITY = 65;
   private static final int CONFIRM_RETRY_TICKS = 4;
   private static final int ACTION_DELAY_TICKS = 0;
   private static final int TIMEOUT_TICKS = 30;
   private final Setting<Double> targetRange = this.num("TargetRange", 8.0, 1.0, 12.0).setPage("General");
   private final Setting<Double> placeRange = this.num("PlaceRange", 5.0, 1.0, 5.0).setPage("General");
   private final Setting<Integer> minecarts = this.num("Minecarts", 4, 1, 16).setPage("General");
   private final Setting<Double> minDamage = this.num("MinDamage", 12.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 8.0, 0.0, 36.0).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 135, 35, 50).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 175, 70, 220).setPage("Render");
   private AutoMinecartModule.Candidate candidate;
   private AutoMinecartModule.Stage stage = AutoMinecartModule.Stage.BASE;
   private int stageStartTick;
   private int lastActionTick;
   private int expectedCartCount;
   private boolean awaitingConfirmation;
   private boolean detonationSent;
   private float lastDamage;
   private BlockPos renderRail;
   private BlockPos renderBase;
   private float renderDamage;
   private long renderUpdatedAt;

   public AutoMinecartModule() {
      super("AutoMinecart", "Stacks TNT minecarts and ignites them with an end crystal.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.resetState(true);
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("AutoMinecart");
      this.resetState(true);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (nullCheck() || mc.player.isDeadOrDying()) {
         this.resetState(false);
      } else if (mc.player.containerMenu.containerId == 0 && !mc.player.isUsingItem()) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            if (this.candidate == null) {
               this.candidate = this.findBestCandidate();
               if (this.candidate == null) {
                  this.lastDamage = 0.0F;
                  return;
               }

               this.lastDamage = this.candidate.damage();
               this.updateRender(this.candidate);
               this.enterStage(this.candidate.needsBase() ? AutoMinecartModule.Stage.BASE : AutoMinecartModule.Stage.RAIL);
            }

            AutoMinecartModule.Evaluation current = this.evaluate(this.candidate);
            if (current == null) {
               this.resetState(false);
            } else {
               this.lastDamage = current.damage();
               this.updateRender(this.candidate, current.damage());
               if (mc.player.tickCount - this.stageStartTick > 30) {
                  this.resetState(false);
               } else {
                  switch (this.stage) {
                     case BASE:
                        this.tickBase();
                        break;
                     case RAIL:
                        this.tickRail();
                        break;
                     case CARTS:
                        this.tickCarts();
                        break;
                     case CRYSTAL:
                        this.tickCrystal();
                        break;
                     case DETONATE:
                        this.tickDetonate();
                  }
               }
            }
         }
      }
   }

   private void tickBase() {
      if (this.isCrystalBase(this.candidate.base())) {
         this.enterStage(AutoMinecartModule.Stage.RAIL);
      } else if (!mc.level.getBlockState(this.candidate.base()).canBeReplaced()) {
         this.resetState(false);
      } else {
         if (this.awaitingConfirmation) {
            if (mc.player.tickCount - this.lastActionTick < 4) {
               return;
            }

            this.awaitingConfirmation = false;
         }

         if (this.canAct()) {
            Result obsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
            if (!obsidian.found()) {
               this.resetState(false);
            } else {
               if (this.rotateTo(Vec3.atCenterOf(this.candidate.base()))
                  && KetamineClub.placementManager.placeDirect(this.candidate.base(), null, obsidian.slot())) {
                  this.awaitingConfirmation = true;
                  this.markAction();
               }
            }
         }
      }
   }

   private void tickRail() {
      if (this.isRail(this.candidate.rail())) {
         this.enterStage(AutoMinecartModule.Stage.CARTS);
      } else if (!this.canPlaceRail(this.candidate.rail())) {
         this.resetState(false);
      } else {
         if (this.awaitingConfirmation) {
            if (mc.player.tickCount - this.lastActionTick < 4) {
               return;
            }

            this.awaitingConfirmation = false;
         }

         if (this.canAct()) {
            Result rail = InventoryUtil.find(Items.RAIL, InventoryUtil.PLACE_SCOPE);
            if (!rail.found()) {
               this.resetState(false);
            } else {
               if (this.rotateTo(Vec3.atCenterOf(this.candidate.rail()))
                  && KetamineClub.placementManager.placeDirectInEntity(this.candidate.rail(), rail.slot())) {
                  this.awaitingConfirmation = true;
                  this.markAction();
               }
            }
         }
      }
   }

   private void tickCarts() {
      if (!this.isRail(this.candidate.rail())) {
         this.resetState(false);
      } else {
         int count = this.minecartCount(this.candidate.rail());
         if (count >= this.minecarts.getValue()) {
            this.enterStage(AutoMinecartModule.Stage.CRYSTAL);
         } else {
            if (this.awaitingConfirmation) {
               if (count > this.expectedCartCount) {
                  this.awaitingConfirmation = false;
               } else {
                  if (mc.player.tickCount - this.lastActionTick < 4) {
                     return;
                  }

                  this.awaitingConfirmation = false;
               }
            }

            if (this.canAct()) {
               Result minecart = InventoryUtil.find(Items.TNT_MINECART, InventoryUtil.PLACE_SCOPE);
               if (!minecart.found()) {
                  this.resetState(false);
               } else {
                  if (this.placeMinecart(minecart)) {
                     this.expectedCartCount = count;
                     this.awaitingConfirmation = true;
                     this.markAction();
                  }
               }
            }
         }
      }
   }

   private void tickCrystal() {
      int count = this.minecartCount(this.candidate.rail());
      if (count >= this.minecarts.getValue()) {
         if (this.hasCrystal(this.candidate.crystal())) {
            this.enterStage(AutoMinecartModule.Stage.DETONATE);
         } else {
            if (this.awaitingConfirmation) {
               if (count < this.expectedCartCount) {
                  this.resetState(false);
                  return;
               }

               if (mc.player.tickCount - this.lastActionTick < 4) {
                  return;
               }

               this.awaitingConfirmation = false;
            }

            if (this.canAct()) {
               Result crystal = InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.PLACE_SCOPE);
               if (!crystal.found()) {
                  this.resetState(false);
               } else {
                  if (this.rotateTo(Vec3.atCenterOf(this.candidate.base().above()))
                     && KetamineClub.placementManager.placeCrystal(this.candidate.base(), crystal.slot(), true)) {
                     this.expectedCartCount = count;
                     this.awaitingConfirmation = true;
                     this.markAction();
                  }
               }
            }
         }
      } else {
         if (this.awaitingConfirmation && count < this.expectedCartCount) {
            this.resetState(false);
         } else {
            this.enterStage(AutoMinecartModule.Stage.CARTS);
         }
      }
   }

   private void tickDetonate() {
      if (this.detonationSent) {
         if (!this.hasCrystal(this.candidate.crystal())) {
            this.resetState(false);
         }
      } else if (!this.hasCrystal(this.candidate.crystal())) {
         this.resetState(false);
      } else if (this.canAct()) {
         AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (autoCrystal != null && autoCrystal.breakCrystalAt(this.candidate.crystal())) {
            this.detonationSent = true;
            this.markAction();
         }
      }
   }

   private AutoMinecartModule.Candidate findBestCandidate() {
      List<LivingEntity> targets = this.collectTargets();
      if (targets.isEmpty()) {
         return null;
      } else if (InventoryUtil.find(Items.RAIL, InventoryUtil.PLACE_SCOPE).found()
         && InventoryUtil.find(Items.TNT_MINECART, InventoryUtil.PLACE_SCOPE).found()
         && InventoryUtil.find(Items.END_CRYSTAL, InventoryUtil.PLACE_SCOPE).found()) {
         Vec3 eye = mc.player.getEyePosition(1.0F);
         double rangeSq = this.placeRange.getValue() * this.placeRange.getValue();
         AutoMinecartModule.Candidate best = null;

         for (LivingEntity target : targets) {
            AABB box = target.getBoundingBox();
            int minX = PlaceUtil.minCell(box.minX);
            int maxX = PlaceUtil.maxCell(box.maxX);
            int minZ = PlaceUtil.minCell(box.minZ);
            int maxZ = PlaceUtil.maxCell(box.maxZ);
            int y = Mth.floor(box.minY + 1.0E-4);

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  BlockPos rail = new BlockPos(x, y, z);
                  if ((this.isRail(rail) || this.canPlaceRail(rail))
                     && this.inventoryCount(Items.TNT_MINECART) + this.minecartCount(rail) >= this.minecarts.getValue()
                     && !mc.player.getBoundingBox().intersects(new AABB(rail))
                     && !(eye.distanceToSqr(Vec3.atCenterOf(rail)) > rangeSq)) {
                     for (Direction direction : Plane.HORIZONTAL) {
                        BlockPos base = rail.relative(direction).below();
                        BlockPos crystal = base.above();
                        boolean needsBase = !this.isCrystalBase(base);
                        if ((!needsBase || PlaceUtil.canPlace(base))
                           && (!needsBase || InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE).found())
                           && !(eye.distanceToSqr(Vec3.atCenterOf(base)) > rangeSq)
                           && this.crystalSpaceClear(crystal)) {
                           AutoMinecartModule.Candidate possible = new AutoMinecartModule.Candidate(rail, base, crystal, needsBase, 0.0F, 0.0F);
                           AutoMinecartModule.Evaluation evaluation = this.evaluate(possible, targets);
                           if (evaluation != null) {
                              possible = new AutoMinecartModule.Candidate(rail, base, crystal, needsBase, evaluation.damage(), evaluation.selfDamage());
                              if (best == null
                                 || possible.damage() > best.damage()
                                 || possible.damage() == best.damage() && possible.selfDamage() < best.selfDamage()) {
                                 best = possible;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private AutoMinecartModule.Evaluation evaluate(AutoMinecartModule.Candidate possible) {
      return this.evaluate(possible, this.collectTargets());
   }

   private AutoMinecartModule.Evaluation evaluate(AutoMinecartModule.Candidate possible, List<LivingEntity> targets) {
      if (!targets.isEmpty() && this.crystalSpaceClear(possible.crystal())) {
         Vec3 minecartExplosion = this.minecartExplosionPos(possible.rail());
         Vec3 crystalExplosion = this.crystalExplosionPos(possible.crystal());
         BlockPos plannedObsidian = possible.needsBase() && !this.isCrystalBase(possible.base()) ? possible.base() : null;
         float damage = 0.0F;

         for (LivingEntity target : targets) {
            TntMinecartDamageUtil.Target snapshot = TntMinecartDamageUtil.Target.ofBestArmor(target);
            if (!(TntMinecartDamageUtil.maxDamage(snapshot, minecartExplosion, crystalExplosion, this.minecarts.getValue()) < this.minDamage.getValue())) {
               damage += TntMinecartDamageUtil.damage(snapshot, minecartExplosion, crystalExplosion, plannedObsidian, this.minecarts.getValue());
            }
         }

         if (damage < this.minDamage.getValue()) {
            return null;
         } else {
            float selfDamage = TntMinecartDamageUtil.selfDamage(minecartExplosion, crystalExplosion, plannedObsidian, this.minecarts.getValue());
            float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            return !(selfDamage > this.maxSelfDamage.getValue()) && (!(selfDamage > 0.0F) || !(selfDamage + 1.5F >= health))
               ? new AutoMinecartModule.Evaluation(damage, selfDamage)
               : null;
         }
      } else {
         return null;
      }
   }

   private List<LivingEntity> collectTargets() {
      TargetsModule targetsModule = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      List<LivingEntity> targets = new ArrayList<>();
      double rangeSq = this.targetRange.getValue() * this.targetRange.getValue();

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof LivingEntity living
            && !living.isDeadOrDying()
            && !(mc.player.distanceToSqr(entity) > rangeSq)
            && (targetsModule == null || targetsModule.isValidTarget(entity))) {
            targets.add(living);
         }
      }

      return targets;
   }

   private boolean placeMinecart(Result minecart) {
      Vec3 hitPos = new Vec3(this.candidate.rail().getX() + 0.5, this.candidate.rail().getY() + 0.125, this.candidate.rail().getZ() + 0.5);
      if (!this.rotateTo(hitPos)) {
         return false;
      } else {
         BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, this.candidate.rail(), false);
         return KetamineClub.swapManager.submit(new SwapRequest("AutoMinecart", 65, minecart, result -> this.sendUseItemOn(hit, result.hand()), true));
      }
   }

   private void sendUseItemOn(BlockHitResult hit, InteractionHand hand) {
      ClientPacketListener connection = mc.getConnection();
      if (connection != null) {
         BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

         try {
            connection.send(new ServerboundUseItemOnPacket(hand, hit, handler.currentSequence()));
         } catch (Throwable var8) {
            if (handler != null) {
               try {
                  handler.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (handler != null) {
            handler.close();
         }

         connection.send(new ServerboundSwingPacket(hand));
      }
   }

   private boolean rotateTo(Vec3 hitPos) {
      float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), hitPos);
      RotationRequest request = new RotationRequest("AutoMinecart", 65, angles[0], angles[1], RotationRequest.Mode.SILENT);
      return KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request);
   }

   private boolean canPlaceRail(BlockPos rail) {
      if (!mc.level.isOutsideBuildHeight(rail) && mc.level.getWorldBorder().isWithinBounds(rail)) {
         BlockState state = mc.level.getBlockState(rail);
         return !state.canBeReplaced() ? false : Blocks.RAIL.defaultBlockState().canSurvive(mc.level, rail);
      } else {
         return false;
      }
   }

   private boolean crystalSpaceClear(BlockPos crystal) {
      BlockState state = mc.level.getBlockState(crystal);
      if (state.isAir() || state.is(Blocks.FIRE) && mc.level.dimension().equals(Level.END)) {
         for (Entity entity : mc.level.getEntities(null, new AABB(crystal))) {
            if (!(entity instanceof ItemEntity) && (!(entity instanceof EndCrystal endCrystal) || !endCrystal.blockPosition().equals(crystal))) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean isCrystalBase(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   private boolean isRail(BlockPos pos) {
      return BaseRailBlock.isRail(mc.level.getBlockState(pos));
   }

   private boolean hasCrystal(BlockPos pos) {
      AABB box = new AABB(pos).inflate(0.25);

      for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, box)) {
         if (crystal.isAlive() && crystal.blockPosition().equals(pos)) {
            return true;
         }
      }

      return false;
   }

   private int minecartCount(BlockPos rail) {
      AABB box = new AABB(rail).inflate(0.35, 0.75, 0.35);
      return mc.level.getEntitiesOfClass(MinecartTNT.class, box, Entity::isAlive).size();
   }

   private int inventoryCount(Item item) {
      int count = 0;

      for (int slot = 0; slot < 36; slot++) {
         if (mc.player.getInventory().getItem(slot).is(item)) {
            count += mc.player.getInventory().getItem(slot).getCount();
         }
      }

      return count;
   }

   private Vec3 minecartExplosionPos(BlockPos rail) {
      return new Vec3(rail.getX() + 0.5, rail.getY() + 0.0625, rail.getZ() + 0.5);
   }

   private Vec3 crystalExplosionPos(BlockPos crystal) {
      return new Vec3(crystal.getX() + 0.5, crystal.getY(), crystal.getZ() + 0.5);
   }

   private boolean canAct() {
      return mc.player.tickCount - this.lastActionTick >= 0;
   }

   private void markAction() {
      this.lastActionTick = mc.player.tickCount;
   }

   private void enterStage(AutoMinecartModule.Stage next) {
      this.stage = next;
      this.stageStartTick = mc.player.tickCount;
      this.awaitingConfirmation = false;
      this.expectedCartCount = 0;
      this.detonationSent = false;
   }

   private void resetState(boolean clearRender) {
      this.candidate = null;
      this.stage = AutoMinecartModule.Stage.BASE;
      this.stageStartTick = 0;
      this.lastActionTick = -1073741824;
      this.expectedCartCount = 0;
      this.awaitingConfirmation = false;
      this.detonationSent = false;
      this.lastDamage = 0.0F;
      KetamineClub.rotationManager.cancel("AutoMinecart");
      if (clearRender) {
         this.renderRail = null;
         this.renderBase = null;
         this.renderDamage = 0.0F;
         this.renderUpdatedAt = 0L;
      }
   }

   private void updateRender(AutoMinecartModule.Candidate possible) {
      this.updateRender(possible, possible.damage());
   }

   private void updateRender(AutoMinecartModule.Candidate possible, float damage) {
      this.renderRail = possible.rail();
      this.renderBase = possible.base();
      this.renderDamage = damage;
      this.renderUpdatedAt = System.currentTimeMillis();
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderRail != null && this.renderBase != null) {
         float alpha = this.renderAlpha();
         if (!(alpha <= 0.0F)) {
            Color fill = this.faded(this.fillColor.getValue(), alpha);
            Color outline = this.faded(this.outlineColor.getValue(), alpha);
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderRail, fill);
            RenderUtil.drawBox(event.getMatrix(), this.renderRail, outline, 1.0F);
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderBase, fill);
            RenderUtil.drawBox(event.getMatrix(), this.renderBase, outline, 1.0F);
         }
      }
   }

   private float renderAlpha() {
      if (this.candidate != null) {
         return 1.0F;
      } else {
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
         long age = System.currentTimeMillis() - this.renderUpdatedAt;
         if (age >= fadeMs) {
            this.renderRail = null;
            this.renderBase = null;
            this.renderDamage = 0.0F;
            return 0.0F;
         } else {
            return 1.0F - (float)(age / fadeMs);
         }
      }
   }

   private Color faded(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp(Math.round(color.getAlpha() * alpha), 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.candidate != null ? String.format("%.1f %s", this.lastDamage, this.stage.name().toLowerCase()) : null;
   }

   private record Candidate(BlockPos rail, BlockPos base, BlockPos crystal, boolean needsBase, float damage, float selfDamage) {
   }

   private record Evaluation(float damage, float selfDamage) {
   }

   private static enum Stage {
      BASE,
      RAIL,
      CARTS,
      CRYSTAL,
      DETONATE;

      private Stage() {
      }
   }
}
