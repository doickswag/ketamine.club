package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.BedDamageUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.RenderUtil;

public class AutoBedModule extends Module {
   private static final String ROTATION_ID = "AutoBed";
   private static final int ROTATION_PRIORITY = 63;
   private static final int SWAP_PRIORITY = 67;
   private static final double RENDER_INTERP = 5.0;
   private static final long RENDER_HOLD_MS = 500L;
   private static final long RENDER_FADE_MS = 350L;
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 8.0, 0.0, 36.0).setPage("General");
   private static final double PLACE_RANGE = 6.0;
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Boolean> damageText = this.bool("DamageText", true).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 190, 45, 70, 55).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 90, 110, 220).setPage("Render");
   private int lastBurstTick = -1;
   private float lastDamage;
   private BlockPos renderFoot;
   private BlockPos renderHead;
   private float renderDamage;
   private AABB smoothBox;
   private long lastTargetMs;

   public AutoBedModule() {
      super("AutoBed", "Intentional game design", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.lastBurstTick = -1;
      this.lastDamage = 0.0F;
      this.renderFoot = null;
      this.renderHead = null;
      this.renderDamage = 0.0F;
      this.smoothBox = null;
      this.lastTargetMs = 0L;
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("AutoBed");
      this.lastDamage = 0.0F;
      this.renderFoot = null;
      this.renderHead = null;
      this.renderDamage = 0.0F;
      this.smoothBox = null;
      this.lastTargetMs = 0L;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         if (!mc.level.dimension().equals(Level.OVERWORLD)) {
            if (mc.player.containerMenu.containerId == 0) {
               List<BedDamageUtil.Target> targets = this.collectTargets();
               if (targets.isEmpty()) {
                  this.lastDamage = 0.0F;
               } else {
                  this.lastTargetMs = System.currentTimeMillis();
                  int sinceLast = mc.player.tickCount - this.lastBurstTick;
                  if (sinceLast < 0 || sinceLast >= this.delay.getValue()) {
                     if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                        OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
                        if (offhand == null || !offhand.shouldDeferForEat()) {
                           Result bed = this.findBed();
                           BedBlock bedBlock = bed.found() && bed.stack().getItem() instanceof BedItem bedItem ? (BedBlock)bedItem.getBlock() : null;
                           AutoBedModule.Candidate best = this.findBest(targets, bedBlock);
                           if (best == null) {
                              this.lastDamage = 0.0F;
                           } else {
                              this.queueBurst(best);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void queueBurst(AutoBedModule.Candidate candidate) {
      float yaw;
      float pitch;
      if (candidate.needPlace()) {
         yaw = candidate.placement().yaw();
         pitch = candidate.placement().pitch();
      } else {
         float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), candidate.detonateHit().getLocation());
         yaw = angles[0];
         pitch = angles[1];
      }

      RotationRequest request = new RotationRequest("AutoBed", 63, yaw, pitch);
      if (KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request)) {
         this.finishBurst(candidate);
      }
   }

   private void finishBurst(AutoBedModule.Candidate candidate) {
      if (this.burst(candidate, this.findBed())) {
         this.lastBurstTick = mc.player.tickCount;
         this.lastDamage = candidate.damage();
         if (this.render.getValue() || this.damageText.getValue()) {
            this.renderFoot = candidate.foot();
            this.renderHead = candidate.head();
            this.renderDamage = candidate.damage();
         }
      }
   }

   private boolean burst(AutoBedModule.Candidate candidate, Result bed) {
      if (!nullCheck() && !mc.player.isDeadOrDying() && !mc.level.dimension().equals(Level.OVERWORLD) && mc.player.containerMenu.containerId == 0) {
         ClientPacketListener conn = mc.getConnection();
         if (conn == null) {
            return false;
         } else {
            if (candidate.needPlace()) {
               if (!this.canPlaceBed(candidate.foot(), candidate.head(), candidate.footState(), candidate.headState())) {
                  return false;
               }

               if (!this.placeBed(conn, candidate, bed)) {
                  return false;
               }
            } else if (!this.isBedPair(candidate.foot(), candidate.head())) {
               return false;
            }

            this.sendUseItemOn(conn, candidate.detonateHit(), InteractionHand.MAIN_HAND);
            conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean placeBed(ClientPacketListener conn, AutoBedModule.Candidate candidate, Result bed) {
      if (bed.found() && bed.stack().getItem() instanceof BedItem) {
         Runnable place = () -> {
            this.sendUseItemOn(conn, candidate.placement().hit(), InteractionHand.MAIN_HAND);
            KetamineClub.placementManager.notePlacement(candidate.foot());
         };
         if (bed.type() == ResultType.INVENTORY) {
            return KetamineClub.swapManager.submit(new SwapRequest("AutoBed", 67, bed, place, true));
         } else if (bed.type() != ResultType.HOTBAR) {
            return false;
         } else {
            int originalSlot = KetamineClub.swapManager.serverSlot();
            if (bed.slot() != originalSlot) {
               conn.send(new ServerboundSetCarriedItemPacket(bed.slot()));
            }

            try {
               place.run();
            } finally {
               if (bed.slot() != originalSlot) {
                  conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private void sendUseItemOn(ClientPacketListener conn, BlockHitResult hit, InteractionHand hand) {
      BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

      try {
         conn.send(new ServerboundUseItemOnPacket(hand, hit, handler.currentSequence()));
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
   }

   private AutoBedModule.Candidate findBest(List<BedDamageUtil.Target> targets, BedBlock bedBlock) {
      Vec3 eye = mc.player.getEyePosition(1.0F);
      BlockPos playerPos = mc.player.blockPosition();
      double maxSelf = this.maxSelfDamage.getValue();
      double min = this.minDamage.getValue();
      double rangeSq = 36.0;
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      int radius = (int)Math.ceil(6.0);
      int radiusSq = radius * radius;
      AutoBedModule.Candidate best = null;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = -radius; x <= radius; x++) {
         int xx = x * x;

         for (int y = -radius; y <= radius; y++) {
            int xxyy = xx + y * y;

            for (int z = -radius; z <= radius; z++) {
               if (xxyy + z * z <= radiusSq) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  BlockState state = mc.level.getBlockState(cursor);
                  if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                     Direction facing = (Direction)state.getValue(BedBlock.FACING);
                     BlockPos head = cursor.immutable();
                     BlockPos foot = head.relative(facing.getOpposite());
                     BlockState footState = mc.level.getBlockState(foot);
                     if (this.isBedPair(foot, head)) {
                        BlockHitResult hit = this.bedHit(foot, footState, head, state, eye);
                        best = this.evaluate(best, targets, foot, head, footState, state, null, hit, false, eye, rangeSq, min, maxSelf, playerHealth);
                     }
                  } else if (bedBlock != null && state.canBeReplaced()) {
                     BlockPos foot = cursor.immutable();
                     BlockHitResult placeHit = KetamineClub.placementManager.prepareAirPlaceHit(foot);
                     if (placeHit != null) {
                        float[] placeAngles = MathUtil.calcAngle(eye, placeHit.getLocation());
                        Direction facing = Direction.fromYRot(placeAngles[0]);
                        BlockPos head = foot.relative(facing);
                        BlockState footState = (BlockState)((BlockState)bedBlock.defaultBlockState().setValue(BedBlock.FACING, facing))
                           .setValue(BedBlock.PART, BedPart.FOOT);
                        BlockState headState = (BlockState)footState.setValue(BedBlock.PART, BedPart.HEAD);
                        if (this.canPlaceBed(foot, head, footState, headState)) {
                           AutoBedModule.Placement placement = new AutoBedModule.Placement(placeHit, placeAngles[0], placeAngles[1]);
                           BlockHitResult hit = this.bedHit(foot, footState, head, headState, eye);
                           best = this.evaluate(best, targets, foot, head, footState, headState, placement, hit, true, eye, rangeSq, min, maxSelf, playerHealth);
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private AutoBedModule.Candidate evaluate(
      AutoBedModule.Candidate best,
      List<BedDamageUtil.Target> targets,
      BlockPos foot,
      BlockPos head,
      BlockState footState,
      BlockState headState,
      AutoBedModule.Placement placement,
      BlockHitResult hit,
      boolean needPlace,
      Vec3 eye,
      double rangeSq,
      double min,
      double maxSelf,
      float playerHealth
   ) {
      if (hit != null && !(eye.distanceToSqr(hit.getLocation()) > rangeSq)) {
         Vec3 explosionPos = Vec3.atCenterOf(head);
         boolean bounded = false;

         for (BedDamageUtil.Target target : targets) {
            if (!(target.pos().distanceTo(explosionPos) > 10.0) && BedDamageUtil.maxDamage(target, explosionPos) >= min) {
               bounded = true;
               break;
            }
         }

         if (!bounded) {
            return best;
         } else {
            float damage = 0.0F;
            boolean any = false;

            for (BedDamageUtil.Target targetx : targets) {
               if (!(targetx.pos().distanceTo(explosionPos) > 10.0)) {
                  float targetDamage = BedDamageUtil.damage(targetx, explosionPos, foot, head);
                  if (!(targetDamage <= 0.0F)) {
                     damage += targetDamage;
                     any = true;
                  }
               }
            }

            if (any && !(damage < min) && (best == null || !(damage <= best.damage()))) {
               float selfDamage = BedDamageUtil.selfDamage(explosionPos, foot, head);
               return !(selfDamage > maxSelf) && (!(selfDamage > 0.0F) || !(selfDamage + 1.5F >= playerHealth))
                  ? new AutoBedModule.Candidate(foot, head, footState, headState, placement, hit, damage, needPlace)
                  : best;
            } else {
               return best;
            }
         }
      } else {
         return best;
      }
   }

   private BlockHitResult bedHit(BlockPos foot, BlockState footState, BlockPos head, BlockState headState, Vec3 eye) {
      BlockHitResult footHit = this.hitOnShape(foot, footState, eye);
      BlockHitResult headHit = this.hitOnShape(head, headState, eye);
      if (footHit == null) {
         return headHit;
      } else if (headHit == null) {
         return footHit;
      } else {
         return eye.distanceToSqr(footHit.getLocation()) <= eye.distanceToSqr(headHit.getLocation()) ? footHit : headHit;
      }
   }

   private BlockHitResult hitOnShape(BlockPos pos, BlockState state, Vec3 eye) {
      VoxelShape shape = state.getShape(mc.level, pos);
      if (shape.isEmpty()) {
         return null;
      } else {
         AABB bounds = shape.bounds().move(pos);
         Vec3 point = new Vec3(
            Mth.clamp(eye.x, bounds.minX + 1.0E-4, bounds.maxX - 1.0E-4),
            Mth.clamp(eye.y, bounds.minY + 1.0E-4, bounds.maxY - 1.0E-4),
            Mth.clamp(eye.z, bounds.minZ + 1.0E-4, bounds.maxZ - 1.0E-4)
         );
         BlockHitResult clipped = shape.clip(eye, point.add(point.subtract(eye).normalize().scale(0.01)), pos);
         return clipped != null ? clipped : new BlockHitResult(point, Direction.UP, pos, false);
      }
   }

   private boolean canPlaceBed(BlockPos foot, BlockPos head, BlockState footState, BlockState headState) {
      if (mc.level.isOutsideBuildHeight(foot.getY()) || mc.level.isOutsideBuildHeight(head.getY())) {
         return false;
      } else if (!mc.level.getWorldBorder().isWithinBounds(foot) || !mc.level.getWorldBorder().isWithinBounds(head)) {
         return false;
      } else if (mc.level.getBlockState(foot).canBeReplaced() && mc.level.getBlockState(head).canBeReplaced()) {
         CollisionContext context = CollisionContext.placementContext(mc.player);
         boolean footClear = mc.level.isUnobstructed(footState, foot, context);
         boolean headClear = mc.level.isUnobstructed(headState, head, context);
         return footClear || headClear;
      } else {
         return false;
      }
   }

   private boolean isBedPair(BlockPos foot, BlockPos head) {
      BlockState headState = mc.level.getBlockState(head);
      if (headState.getBlock() instanceof BedBlock && headState.getValue(BedBlock.PART) == BedPart.HEAD) {
         Direction facing = (Direction)headState.getValue(BedBlock.FACING);
         if (!head.relative(facing.getOpposite()).equals(foot)) {
            return false;
         } else {
            BlockState footState = mc.level.getBlockState(foot);
            return footState.is(headState.getBlock()) && footState.getValue(BedBlock.PART) == BedPart.FOOT && footState.getValue(BedBlock.FACING) == facing;
         }
      } else {
         return false;
      }
   }

   private List<BedDamageUtil.Target> collectTargets() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      List<BedDamageUtil.Target> result = new ArrayList<>();
      AABB area = mc.player.getBoundingBox().inflate(16.0);

      for (Entity entity : mc.level.getEntities(mc.player, area)) {
         if (entity instanceof LivingEntity living && !living.isDeadOrDying() && (targets == null || targets.isValidPlayerTarget(entity))) {
            result.add(BedDamageUtil.Target.of(living));
         }
      }

      return result;
   }

   private Result findBed() {
      return InventoryUtil.find((Predicate<ItemStack>)(stack -> stack.getItem() instanceof BedItem), InventoryUtil.PLACE_SCOPE);
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderFoot != null && this.renderHead != null) {
         float alpha = this.renderAlpha();
         if (!(alpha <= 0.0F)) {
            this.updateSmoothBox(this.renderBox());
            RenderUtil.drawBoxFilled(event.getMatrix(), this.smoothBox, this.faded(this.fillColor.getValue(), alpha));
            RenderUtil.drawBox(event.getMatrix(), this.smoothBox, this.faded(this.outlineColor.getValue(), alpha), 1.0F);
         }
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck() && this.damageText.getValue() && this.renderFoot != null && this.renderHead != null && !(this.renderDamage <= 0.0F)) {
         float alpha = this.renderAlpha();
         if (!(alpha <= 0.0F)) {
            AABB box = this.renderBox();
            float[] screen = MatrixCapture.worldToScreen((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
            if (screen != null) {
               String text = String.format("%.1f", this.renderDamage);
               GuiGraphicsExtractor graphics = event.getContext();
               int halfWidth = mc.font.width(text) / 2;
               graphics.pose().pushMatrix();
               graphics.pose().translate(screen[0], screen[1]);
               graphics.text(mc.font, text, -halfWidth, -9 / 2, new Color(255, 255, 255, Math.round(255.0F * alpha)).getRGB(), true);
               graphics.pose().popMatrix();
            }
         }
      }
   }

   private float renderAlpha() {
      long age = System.currentTimeMillis() - this.lastTargetMs;
      if (age <= 500L) {
         return 1.0F;
      } else if (age < 850L) {
         return 1.0F - (float)(age - 500L) / 350.0F;
      } else {
         this.renderFoot = null;
         this.renderHead = null;
         this.renderDamage = 0.0F;
         this.smoothBox = null;
         return 0.0F;
      }
   }

   private Color faded(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha));
   }

   private AABB renderBox() {
      return new AABB(
         Math.min(this.renderFoot.getX(), this.renderHead.getX()),
         Math.min(this.renderFoot.getY(), this.renderHead.getY()),
         Math.min(this.renderFoot.getZ(), this.renderHead.getZ()),
         Math.max(this.renderFoot.getX(), this.renderHead.getX()) + 1.0,
         Math.max(this.renderFoot.getY(), this.renderHead.getY()) + 1.0,
         Math.max(this.renderFoot.getZ(), this.renderHead.getZ()) + 1.0
      );
   }

   private void updateSmoothBox(AABB target) {
      if (this.smoothBox == null) {
         this.smoothBox = target;
      } else {
         double factor = 0.2;
         this.smoothBox = new AABB(
            lerp(this.smoothBox.minX, target.minX, factor),
            lerp(this.smoothBox.minY, target.minY, factor),
            lerp(this.smoothBox.minZ, target.minZ, factor),
            lerp(this.smoothBox.maxX, target.maxX, factor),
            lerp(this.smoothBox.maxY, target.maxY, factor),
            lerp(this.smoothBox.maxZ, target.maxZ, factor)
         );
      }
   }

   private static double lerp(double from, double to, double factor) {
      return from + (to - from) * factor;
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record Candidate(
      BlockPos foot,
      BlockPos head,
      BlockState footState,
      BlockState headState,
      AutoBedModule.Placement placement,
      BlockHitResult detonateHit,
      float damage,
      boolean needPlace
   ) {
   }

   private record Placement(BlockHitResult hit, float yaw, float pitch) {
   }
}
