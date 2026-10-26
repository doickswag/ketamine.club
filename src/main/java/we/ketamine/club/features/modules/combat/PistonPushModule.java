package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class PistonPushModule extends Module {
   private static final String ROTATION_ID = "PistonPush";
   private static final int ROTATION_PRIORITY = 70;
   private static final double TARGET_RANGE = 10.0;
   private static final double TARGET_RANGE_SQ = 100.0;
   private static final int CONTRAPTION_PROTECT_TICKS = 60;
   private static final double RENDER_INTERP = 5.0;
   private static final long RENDER_HOLD_MS = 500L;
   private static final long RENDER_FADE_MS = 350L;
   private final Setting<Double> placeRange = this.num("PlaceRange", 3.0, 1.0, 5.0).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 0, 62, 122, 148).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 0, 62, 122, 148).setPage("Render");
   private final Map<BlockPos, Integer> placedContraption = new HashMap<>();
   private Boolean lastAirExit;
   private BlockPos renderPiston;
   private BlockPos renderRedstone;
   private AABB smoothBox;
   private long lastTargetMs;

   public PistonPushModule() {
      super("PistonPush", "Pushes enemy players out of blocks or around for the fuck of it.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("PistonPush");
      this.resetState();
   }

   private void resetState() {
      this.placedContraption.clear();
      this.lastAirExit = null;
      this.renderPiston = null;
      this.renderRedstone = null;
      this.smoothBox = null;
      this.lastTargetMs = 0L;
   }

   @Subscribe
   private void onTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            int now = mc.player.tickCount;
            this.placedContraption.entrySet().removeIf(entry -> now - entry.getValue() > 60);
            PistonPushModule.Setup setup = this.findSetup();
            if (setup == null) {
               this.lastAirExit = null;
            } else {
               Direction placementLook = setup.pushDirection().getOpposite();
               RotationRequest request = new RotationRequest("PistonPush", 70, placementLook.toYRot(), this.placementPitch(placementLook));
               if (KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request)) {
                  this.finishPush(setup);
               }
            }
         }
      }
   }

   private void finishPush(PistonPushModule.Setup setup) {
      if (!nullCheck() && !mc.player.isDeadOrDying() && mc.player.containerMenu.containerId == 0) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            if (mc.level.getEntity(setup.targetId()) instanceof Player target && !target.isDeadOrDying()) {
               AABB targetBox = target.getBoundingBox();
               AABB contactBox = setup.pushDirection() == Direction.UP ? this.feetBox(targetBox) : this.headBox(targetBox);
               if (contactBox.intersects(new AABB(setup.contact()))) {
                  if (PlaceUtil.canPlace(setup.piston())) {
                     if (setup.placeRedstone() || mc.level.getBlockState(setup.redstone()).is(Blocks.REDSTONE_BLOCK)) {
                        int pistonSlot = this.pistonSlot();
                        int redstoneSlot = this.hotbarSlotOf(Items.REDSTONE_BLOCK);
                        if (pistonSlot >= 0 && (!setup.placeRedstone() || redstoneSlot >= 0)) {
                           this.place(setup, pistonSlot, redstoneSlot);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void place(PistonPushModule.Setup setup, int pistonSlot, int redstoneSlot) {
      if (KetamineClub.placementManager.placeDirect(setup.piston(), setup.pistonFace(), pistonSlot)) {
         int tick = mc.player.tickCount;
         this.placedContraption.put(setup.piston(), tick);
         if (setup.placeRedstone()) {
            if (!KetamineClub.placementManager.placeDirect(setup.redstone(), setup.redstoneFace(), redstoneSlot)) {
               return;
            }

            this.placedContraption.put(setup.redstone(), tick);
         }

         this.renderPiston = setup.piston();
         this.renderRedstone = setup.redstone();
         this.lastAirExit = setup.airExit();
      }
   }

   private PistonPushModule.Setup findSetup() {
      LivingEntity target = this.findTarget();
      if (target == null) {
         return null;
      } else {
         this.lastTargetMs = System.currentTimeMillis();
         AABB targetBox = target.getBoundingBox();
         Vec3 eye = mc.player.getEyePosition();
         double rangeSq = this.placeRange.getValue() * this.placeRange.getValue();
         PistonPushModule.Setup best = this.findSetupForBox(
            target.getId(),
            targetBox,
            this.headBox(targetBox),
            new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN},
            eye,
            rangeSq,
            null
         );
         return this.findSetupForBox(target.getId(), targetBox, this.feetBox(targetBox), new Direction[]{Direction.UP}, eye, rangeSq, best);
      }
   }

   private PistonPushModule.Setup findSetupForBox(
      int targetId, AABB targetBox, AABB contactBox, Direction[] pushDirections, Vec3 eye, double rangeSq, PistonPushModule.Setup best
   ) {
      for (int x = PlaceUtil.minCell(contactBox.minX); x <= PlaceUtil.maxCell(contactBox.maxX); x++) {
         for (int y = PlaceUtil.minCell(contactBox.minY); y <= PlaceUtil.maxCell(contactBox.maxY); y++) {
            for (int z = PlaceUtil.minCell(contactBox.minZ); z <= PlaceUtil.maxCell(contactBox.maxZ); z++) {
               BlockPos contact = new BlockPos(x, y, z);
               BlockState contactState = mc.level.getBlockState(contact);
               if (contactState.isAir() || contactState.canBeReplaced()) {
                  double overlap = this.overlap(contactBox, contact);
                  if (!(overlap <= 0.0)) {
                     for (Direction pushDirection : pushDirections) {
                        PistonPushModule.Setup candidate = this.buildSetup(targetId, targetBox, contact, pushDirection, overlap, eye, rangeSq);
                        if (candidate != null && this.better(candidate, best)) {
                           best = candidate;
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private PistonPushModule.Setup buildSetup(int targetId, AABB targetBox, BlockPos contact, Direction pushDirection, double overlap, Vec3 eye, double rangeSq) {
      BlockPos piston = contact.relative(pushDirection.getOpposite());
      if (!PlaceUtil.canPlace(piston)) {
         return null;
      } else if (eye.distanceToSqr(Vec3.atCenterOf(piston)) > rangeSq) {
         return null;
      } else {
         Direction pistonFace = this.supportFace(piston);
         PistonPushModule.RedstoneSpot redstone = this.findRedstoneSpot(piston, pushDirection, eye, rangeSq);
         if (redstone != null && !this.crystalObscuresPlacement(piston, redstone.pos())) {
            boolean airExit = this.hasAirExit(targetBox, pushDirection);
            double distance = eye.distanceToSqr(Vec3.atCenterOf(piston)) + eye.distanceToSqr(Vec3.atCenterOf(redstone.pos())) * 0.25;
            return new PistonPushModule.Setup(
               targetId, contact, pushDirection, piston, pistonFace, redstone.pos(), redstone.face(), redstone.place(), airExit, overlap, distance
            );
         } else {
            return null;
         }
      }
   }

   private AABB headBox(AABB targetBox) {
      double height = Math.min(0.6, targetBox.getYsize());
      return new AABB(targetBox.minX, targetBox.maxY - height, targetBox.minZ, targetBox.maxX, targetBox.maxY, targetBox.maxZ);
   }

   private AABB feetBox(AABB targetBox) {
      double height = Math.min(0.6, targetBox.getYsize());
      return new AABB(targetBox.minX, targetBox.minY, targetBox.minZ, targetBox.maxX, targetBox.minY + height, targetBox.maxZ);
   }

   private float placementPitch(Direction placementLook) {
      if (placementLook == Direction.UP) {
         return -90.0F;
      } else {
         return placementLook == Direction.DOWN ? 90.0F : 0.0F;
      }
   }

   private boolean better(PistonPushModule.Setup candidate, PistonPushModule.Setup current) {
      if (current == null) {
         return true;
      } else if (candidate.airExit() != current.airExit()) {
         return candidate.airExit();
      } else {
         int overlap = Double.compare(candidate.overlap(), current.overlap());
         if (overlap != 0) {
            return overlap > 0;
         } else {
            return candidate.placeRedstone() != current.placeRedstone() ? !candidate.placeRedstone() : candidate.distance() < current.distance();
         }
      }
   }

   private boolean hasAirExit(AABB targetBox, Direction pushDirection) {
      AABB destination = targetBox.move(pushDirection.getStepX(), pushDirection.getStepY(), pushDirection.getStepZ()).deflate(1.0E-4);

      for (int x = PlaceUtil.minCell(destination.minX); x <= PlaceUtil.maxCell(destination.maxX); x++) {
         for (int y = PlaceUtil.minCell(destination.minY); y <= PlaceUtil.maxCell(destination.maxY); y++) {
            for (int z = PlaceUtil.minCell(destination.minZ); z <= PlaceUtil.maxCell(destination.maxZ); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = mc.level.getBlockState(pos);
               if (!state.isAir()) {
                  for (AABB shape : state.getCollisionShape(mc.level, pos).toAabbs()) {
                     if (shape.move(x, y, z).intersects(destination)) {
                        return false;
                     }
                  }
               }
            }
         }
      }

      return true;
   }

   private double overlap(AABB box, BlockPos pos) {
      double x = Math.max(0.0, Math.min(box.maxX, pos.getX() + 1.0) - Math.max(box.minX, (double)pos.getX()));
      double y = Math.max(0.0, Math.min(box.maxY, pos.getY() + 1.0) - Math.max(box.minY, (double)pos.getY()));
      double z = Math.max(0.0, Math.min(box.maxZ, pos.getZ() + 1.0) - Math.max(box.minZ, (double)pos.getZ()));
      return x * y * z;
   }

   private Direction supportFace(BlockPos pos, BlockPos... phantoms) {
      Direction real = KetamineClub.placementManager.getPlaceSide(pos);
      if (real != null) {
         return real;
      } else {
         for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);

            for (BlockPos phantom : phantoms) {
               if (phantom != null && phantom.equals(neighbour)) {
                  return direction;
               }
            }
         }

         return null;
      }
   }

   private PistonPushModule.RedstoneSpot findRedstoneSpot(BlockPos piston, Direction pushDirection, Vec3 eye, double rangeSq) {
      Direction back = pushDirection.getOpposite();
      BlockPos[] candidates = pushDirection.getAxis().isVertical()
         ? new BlockPos[]{piston.relative(back), piston.north(), piston.south(), piston.west(), piston.east()}
         : new BlockPos[]{
            piston.above(),
            piston.relative(back),
            piston.relative(pushDirection.getClockWise()),
            piston.relative(pushDirection.getCounterClockWise()),
            piston.below()
         };

      for (BlockPos pos : candidates) {
         if (mc.level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
            return new PistonPushModule.RedstoneSpot(pos, null, false);
         }
      }

      for (BlockPos posx : candidates) {
         if (PlaceUtil.canPlace(posx) && !(eye.distanceToSqr(Vec3.atCenterOf(posx)) > rangeSq)) {
            return new PistonPushModule.RedstoneSpot(posx, this.supportFace(posx, piston), true);
         }
      }

      return null;
   }

   private boolean crystalObscuresPlacement(BlockPos... positions) {
      for (BlockPos position : positions) {
         if (!mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(position), Entity::isAlive).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private LivingEntity findTarget() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      LivingEntity best = null;
      double bestSq = Double.MAX_VALUE;

      for (Player player : mc.level.players()) {
         if (player != mc.player && !player.isDeadOrDying() && (targets == null || targets.isValidPlayerTarget(player))) {
            double distanceSq = mc.player.distanceToSqr(player);
            if (!(distanceSq > 100.0) && !(distanceSq >= bestSq)) {
               bestSq = distanceSq;
               best = player;
            }
         }
      }

      return best;
   }

   public boolean isOwnPistonBlock(BlockPos pos) {
      if (pos != null && !this.isDisabled()) {
         Integer placedTick = this.placedContraption.get(pos);
         return placedTick != null && mc.player != null && mc.player.tickCount - placedTick <= 60;
      } else {
         return false;
      }
   }

   private int pistonSlot() {
      int slot = this.hotbarSlotOf(Items.PISTON);
      return slot >= 0 ? slot : this.hotbarSlotOf(Items.STICKY_PISTON);
   }

   private int hotbarSlotOf(Item item) {
      Result result = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return result.found() && result.type() != ResultType.OFFHAND ? result.slot() : -1;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPiston != null && this.renderRedstone != null) {
         float alpha = this.renderAlpha();
         if (!(alpha <= 0.0F)) {
            this.updateSmoothBox(this.renderBox());
            RenderUtil.drawBoxFilled(event.getMatrix(), this.smoothBox, this.faded(this.fillColor.getValue(), alpha));
            RenderUtil.drawBox(event.getMatrix(), this.smoothBox, this.faded(this.outlineColor.getValue(), alpha), 1.0F);
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
         this.renderPiston = null;
         this.renderRedstone = null;
         this.smoothBox = null;
         return 0.0F;
      }
   }

   private Color faded(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha));
   }

   private AABB renderBox() {
      return new AABB(
         Math.min(this.renderPiston.getX(), this.renderRedstone.getX()),
         Math.min(this.renderPiston.getY(), this.renderRedstone.getY()),
         Math.min(this.renderPiston.getZ(), this.renderRedstone.getZ()),
         Math.max(this.renderPiston.getX(), this.renderRedstone.getX()) + 1.0,
         Math.max(this.renderPiston.getY(), this.renderRedstone.getY()) + 1.0,
         Math.max(this.renderPiston.getZ(), this.renderRedstone.getZ()) + 1.0
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
      return this.lastAirExit == null ? null : (this.lastAirExit ? "Air" : "Push");
   }

   private record RedstoneSpot(BlockPos pos, Direction face, boolean place) {
   }

   private record Setup(
      int targetId,
      BlockPos contact,
      Direction pushDirection,
      BlockPos piston,
      Direction pistonFace,
      BlockPos redstone,
      Direction redstoneFace,
      boolean placeRedstone,
      boolean airExit,
      double overlap,
      double distance
   ) {
   }
}
