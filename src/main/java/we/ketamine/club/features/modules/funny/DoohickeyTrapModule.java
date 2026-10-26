package we.ketamine.club.features.modules.funny;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class DoohickeyTrapModule extends Module {
   private static final String ROTATION_ID = "DoohickeyTrap";
   private static final int ROTATION_PRIORITY = 70;
   private static final int ACTION_RETRY_TICKS = 2;
   private static final int SETUP_TIMEOUT_TICKS = 80;
   private static final Direction[] CAGE_ORDER = new Direction[]{
      Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
   };
   private final Setting<Double> targetRange = this.num("TargetRange", 8.0, 1.0, 12.0).setPage("General");
   private final Setting<Double> placeRange = this.num("PlaceRange", 5.0, 1.0, 5.0).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.0F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 198, 124, 178, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 198, 124, 178, 180).setPage("Render");
   private final Setting<Color> centerColor = this.color("CenterColor", 80, 210, 135, 190).setPage("Render");
   private final Set<BlockPos> ownedContraption = new HashSet<>();
   private DoohickeyTrapModule.TrapPlan activePlan;
   private DoohickeyTrapModule.Stage stage = DoohickeyTrapModule.Stage.PISTONS;
   private int planIndex;
   private int stageStartTick;
   private int lastActionTick;

   public DoohickeyTrapModule() {
      super("DoohickeyTrap", "Pins a player inside one air block with a powered piston on every side.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("DoohickeyTrap");
      this.resetState();
   }

   private void resetState() {
      this.activePlan = null;
      this.stage = DoohickeyTrapModule.Stage.PISTONS;
      this.planIndex = 0;
      this.stageStartTick = 0;
      this.lastActionTick = -1000000;
      this.ownedContraption.clear();
   }

   @Subscribe
   private void onTick(PreTickEvent event) {
      if (nullCheck() || mc.player.isDeadOrDying()) {
         this.abandonPlan();
      } else if (mc.player.containerMenu.containerId == 0 && !mc.player.isUsingItem()) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            if (this.activePlan == null) {
               DoohickeyTrapModule.TrapPlan plan = this.findPlan();
               if (plan == null) {
                  return;
               }

               this.beginPlan(plan);
            }

            Player target = this.activeTarget();
            if (target != null && this.targetNearCenter(target)) {
               if (this.stage != DoohickeyTrapModule.Stage.HOLD && mc.player.tickCount - this.stageStartTick > 80) {
                  this.abandonPlan();
               } else {
                  switch (this.stage) {
                     case PISTONS:
                        this.tickPistons();
                        break;
                     case REDSTONES:
                        this.tickRedstones();
                        break;
                     case HOLD:
                        this.tickHold();
                  }
               }
            } else {
               this.abandonPlan();
            }
         }
      }
   }

   private void beginPlan(DoohickeyTrapModule.TrapPlan plan) {
      this.activePlan = plan;
      this.enterStage(DoohickeyTrapModule.Stage.PISTONS, 0);
   }

   private void abandonPlan() {
      KetamineClub.rotationManager.cancel("DoohickeyTrap");
      this.activePlan = null;
      this.stage = DoohickeyTrapModule.Stage.PISTONS;
      this.planIndex = 0;
      this.ownedContraption.clear();
   }

   private void enterStage(DoohickeyTrapModule.Stage next, int index) {
      this.stage = next;
      this.planIndex = index;
      this.stageStartTick = mc.player.tickCount;
      this.lastActionTick = -1000000;
   }

   private void tickPistons() {
      List<DoohickeyTrapModule.PistonPlan> pistons;
      for (pistons = this.activePlan.pistons();
         this.planIndex < pistons.size() && this.pistonReady(pistons.get(this.planIndex));
         this.stageStartTick = mc.player.tickCount
      ) {
         this.planIndex++;
      }

      if (this.planIndex >= pistons.size()) {
         KetamineClub.rotationManager.cancel("DoohickeyTrap");
         this.enterStage(DoohickeyTrapModule.Stage.REDSTONES, 0);
      } else if (this.canAct()) {
         DoohickeyTrapModule.PistonPlan piston = pistons.get(this.planIndex);
         if (!this.canPlacePiston(piston)) {
            this.abandonPlan();
         } else {
            int pistonSlot = this.pistonSlot();
            if (pistonSlot >= 0) {
               RotationRequest request = new RotationRequest(
                  "DoohickeyTrap", 70, piston.placementLook().toYRot(), this.placementPitch(piston.placementLook()), RotationRequest.Mode.SILENT
               );
               if (KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request)) {
                  Direction face = this.supportFace(piston.piston());
                  if (KetamineClub.placementManager.placeDirect(piston.piston(), face, pistonSlot)) {
                     this.ownedContraption.add(piston.piston());
                     this.markAction();
                  }
               }
            }
         }
      }
   }

   private void tickRedstones() {
      List<DoohickeyTrapModule.PistonPlan> pistons;
      for (pistons = this.activePlan.pistons();
         this.planIndex < pistons.size() && this.redstoneReady(pistons.get(this.planIndex));
         this.stageStartTick = mc.player.tickCount
      ) {
         this.planIndex++;
      }

      if (this.planIndex >= pistons.size()) {
         this.enterStage(DoohickeyTrapModule.Stage.HOLD, 0);
      } else if (this.canAct()) {
         DoohickeyTrapModule.PistonPlan piston = pistons.get(this.planIndex);
         if (!this.pistonReady(piston)) {
            this.enterStage(DoohickeyTrapModule.Stage.PISTONS, this.planIndex);
         } else if (!PlaceUtil.canPlace(piston.redstone())) {
            this.abandonPlan();
         } else {
            int redstoneSlot = this.hotbarSlotOf(Items.REDSTONE_BLOCK);
            if (redstoneSlot >= 0) {
               Direction face = this.supportFace(piston.redstone(), piston.piston());
               if (KetamineClub.placementManager.placeDirect(piston.redstone(), face, redstoneSlot)) {
                  this.ownedContraption.add(piston.redstone());
                  this.markAction();
               }
            }
         }
      }
   }

   private void tickHold() {
      List<DoohickeyTrapModule.PistonPlan> pistons = this.activePlan.pistons();

      for (int i = 0; i < pistons.size(); i++) {
         if (!this.pistonReady(pistons.get(i))) {
            this.enterStage(DoohickeyTrapModule.Stage.PISTONS, i);
            return;
         }
      }

      for (int ix = 0; ix < pistons.size(); ix++) {
         if (!this.redstoneReady(pistons.get(ix))) {
            this.enterStage(DoohickeyTrapModule.Stage.REDSTONES, ix);
            return;
         }
      }
   }

   private boolean canAct() {
      return mc.player.tickCount - this.lastActionTick >= 2;
   }

   private void markAction() {
      this.lastActionTick = mc.player.tickCount;
   }

   private DoohickeyTrapModule.TrapPlan findPlan() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      double rangeSq = this.targetRange.getValue() * this.targetRange.getValue();
      List<Player> candidates = new ArrayList<>();

      for (Player player : mc.level.players()) {
         if (player != mc.player
            && !player.isDeadOrDying()
            && (targets == null || targets.isValidPlayerTarget(player))
            && mc.player.distanceToSqr(player) <= rangeSq) {
            candidates.add(player);
         }
      }

      candidates.sort(Comparator.comparingDouble(mc.player::distanceToSqr));

      for (Player target : candidates) {
         DoohickeyTrapModule.TrapPlan plan = this.findPlan(target);
         if (plan != null) {
            return plan;
         }
      }

      return null;
   }

   private DoohickeyTrapModule.TrapPlan findPlan(Player target) {
      AABB targetBox = target.getBoundingBox();
      DoohickeyTrapModule.TrapPlan best = null;

      for (int x = PlaceUtil.minCell(targetBox.minX); x <= PlaceUtil.maxCell(targetBox.maxX); x++) {
         for (int y = PlaceUtil.minCell(targetBox.minY); y <= PlaceUtil.maxCell(targetBox.maxY); y++) {
            for (int z = PlaceUtil.minCell(targetBox.minZ); z <= PlaceUtil.maxCell(targetBox.maxZ); z++) {
               BlockPos center = new BlockPos(x, y, z);
               double overlap = this.overlap(targetBox, center);
               if (!(overlap <= 0.0)) {
                  DoohickeyTrapModule.TrapPlan candidate = this.buildPlan(target.getId(), center, overlap);
                  if (candidate != null && this.better(candidate, best)) {
                     best = candidate;
                  }
               }
            }
         }
      }

      return best;
   }

   private DoohickeyTrapModule.TrapPlan buildPlan(int targetId, BlockPos center, double overlap) {
      BlockState centerState = mc.level.getBlockState(center);
      if (!centerState.isAir() && !centerState.canBeReplaced()) {
         return null;
      } else {
         Vec3 eye = mc.player.getEyePosition();
         double placeRangeSq = this.placeRange.getValue() * this.placeRange.getValue();
         List<DoohickeyTrapModule.PistonPlan> pistons = new ArrayList<>(CAGE_ORDER.length);
         Set<BlockPos> reserved = new HashSet<>();
         reserved.add(center);

         for (Direction side : CAGE_ORDER) {
            BlockPos head = center.relative(side);
            BlockPos piston = center.relative(side, 2);
            if (!this.headAvailable(head, piston, side) || !this.pistonAvailable(piston, side)) {
               return null;
            }

            if (eye.distanceToSqr(Vec3.atCenterOf(piston)) > placeRangeSq) {
               return null;
            }

            if (this.crystalObscures(piston)) {
               return null;
            }

            reserved.add(head);
            reserved.add(piston);
            pistons.add(new DoohickeyTrapModule.PistonPlan(side, head, piston, null));
         }

         List<DoohickeyTrapModule.PistonPlan> complete = new ArrayList<>(pistons.size());
         double distance = 0.0;

         for (DoohickeyTrapModule.PistonPlan pistonx : pistons) {
            BlockPos redstone = this.findRedstoneSpot(pistonx, reserved, eye, placeRangeSq);
            if (redstone == null) {
               return null;
            }

            reserved.add(redstone);
            complete.add(new DoohickeyTrapModule.PistonPlan(pistonx.placementLook(), pistonx.head(), pistonx.piston(), redstone));
            distance += eye.distanceToSqr(Vec3.atCenterOf(pistonx.piston()));
            distance += eye.distanceToSqr(Vec3.atCenterOf(redstone)) * 0.25;
         }

         return new DoohickeyTrapModule.TrapPlan(targetId, center.immutable(), List.copyOf(complete), overlap, distance);
      }
   }

   private BlockPos findRedstoneSpot(DoohickeyTrapModule.PistonPlan piston, Set<BlockPos> reserved, Vec3 eye, double rangeSq) {
      Direction pushDirection = piston.placementLook().getOpposite();
      Direction back = pushDirection.getOpposite();
      BlockPos base = piston.piston();
      BlockPos[] candidates = pushDirection.getAxis().isVertical()
         ? new BlockPos[]{base.relative(back), base.north(), base.south(), base.west(), base.east()}
         : new BlockPos[]{
            base.above(), base.relative(back), base.relative(pushDirection.getClockWise()), base.relative(pushDirection.getCounterClockWise()), base.below()
         };

      for (BlockPos pos : candidates) {
         if (!reserved.contains(pos) && mc.level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK)) {
            return pos.immutable();
         }
      }

      for (BlockPos posx : candidates) {
         if (!reserved.contains(posx) && PlaceUtil.canPlace(posx) && !(eye.distanceToSqr(Vec3.atCenterOf(posx)) > rangeSq) && !this.crystalObscures(posx)) {
            return posx.immutable();
         }
      }

      return null;
   }

   private boolean better(DoohickeyTrapModule.TrapPlan candidate, DoohickeyTrapModule.TrapPlan current) {
      if (current == null) {
         return true;
      } else {
         int overlap = Double.compare(candidate.overlap(), current.overlap());
         return overlap > 0 || overlap == 0 && candidate.distance() < current.distance();
      }
   }

   private double overlap(AABB box, BlockPos pos) {
      double x = Math.max(0.0, Math.min(box.maxX, pos.getX() + 1.0) - Math.max(box.minX, (double)pos.getX()));
      double y = Math.max(0.0, Math.min(box.maxY, pos.getY() + 1.0) - Math.max(box.minY, (double)pos.getY()));
      double z = Math.max(0.0, Math.min(box.maxZ, pos.getZ() + 1.0) - Math.max(box.minZ, (double)pos.getZ()));
      return x * y * z;
   }

   private boolean pistonAvailable(BlockPos pos, Direction placementLook) {
      DoohickeyTrapModule.PistonPlan probe = new DoohickeyTrapModule.PistonPlan(placementLook, pos.relative(placementLook.getOpposite()), pos, null);
      return this.pistonReady(probe) || PlaceUtil.canPlace(pos);
   }

   private boolean headAvailable(BlockPos head, BlockPos piston, Direction placementLook) {
      DoohickeyTrapModule.PistonPlan probe = new DoohickeyTrapModule.PistonPlan(placementLook, head, piston, null);
      if (this.pistonReady(probe)) {
         BlockState pistonState = mc.level.getBlockState(piston);
         if (!(Boolean)pistonState.getValue(BlockStateProperties.EXTENDED)) {
            return true;
         } else {
            BlockState headState = mc.level.getBlockState(head);
            return headState.is(Blocks.PISTON_HEAD) || headState.is(Blocks.MOVING_PISTON);
         }
      } else {
         BlockState state = mc.level.getBlockState(head);
         return state.isAir() || state.canBeReplaced();
      }
   }

   private boolean canPlacePiston(DoohickeyTrapModule.PistonPlan piston) {
      if (!PlaceUtil.canPlace(piston.piston())) {
         return false;
      } else {
         BlockState headState = mc.level.getBlockState(piston.head());
         return headState.isAir() || headState.canBeReplaced();
      }
   }

   private boolean pistonReady(DoohickeyTrapModule.PistonPlan piston) {
      BlockState state = mc.level.getBlockState(piston.piston());
      return (state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON))
         && state.hasProperty(BlockStateProperties.FACING)
         && state.getValue(BlockStateProperties.FACING) == piston.placementLook().getOpposite();
   }

   private boolean redstoneReady(DoohickeyTrapModule.PistonPlan piston) {
      return mc.level.getBlockState(piston.redstone()).is(Blocks.REDSTONE_BLOCK);
   }

   private Player activeTarget() {
      if (this.activePlan == null) {
         return null;
      } else if (mc.level.getEntity(this.activePlan.targetId()) instanceof Player player && !player.isDeadOrDying()) {
         TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
         return targets != null && !targets.isValidPlayerTarget(player) ? null : player;
      } else {
         return null;
      }
   }

   private boolean targetNearCenter(Player target) {
      return target.getBoundingBox().intersects(new AABB(this.activePlan.center()).inflate(1.25));
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

   private float placementPitch(Direction placementLook) {
      if (placementLook == Direction.UP) {
         return -90.0F;
      } else {
         return placementLook == Direction.DOWN ? 90.0F : 0.0F;
      }
   }

   private boolean crystalObscures(BlockPos pos) {
      return !mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos), Entity::isAlive).isEmpty();
   }

   private int pistonSlot() {
      int slot = this.hotbarSlotOf(Items.PISTON);
      return slot >= 0 ? slot : this.hotbarSlotOf(Items.STICKY_PISTON);
   }

   private int hotbarSlotOf(Item item) {
      Result result = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return result.found() && result.type() != ResultType.OFFHAND ? result.slot() : -1;
   }

   public boolean isOwnPistonBlock(BlockPos pos) {
      return pos != null && this.isEnabled() && this.ownedContraption.contains(pos);
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.activePlan != null) {
         for (DoohickeyTrapModule.PistonPlan piston : this.activePlan.pistons()) {
            RenderUtil.drawBoxFilled(event.getMatrix(), piston.head(), this.fillColor.getValue());
            RenderUtil.drawBox(event.getMatrix(), piston.head(), this.outlineColor.getValue(), this.lineWidth.getValue());
         }

         RenderUtil.drawBox(event.getMatrix(), this.activePlan.center(), this.centerColor.getValue(), this.lineWidth.getValue() + 0.5F);
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.activePlan == null) {
         return null;
      } else {
         return switch (this.stage) {
            case PISTONS -> "Pistons " + Math.min(this.planIndex, CAGE_ORDER.length) + "/6";
            case REDSTONES -> "Power " + Math.min(this.planIndex, CAGE_ORDER.length) + "/6";
            case HOLD -> "Holding";
         };
      }
   }

   private record PistonPlan(Direction placementLook, BlockPos head, BlockPos piston, BlockPos redstone) {
   }

   private static enum Stage {
      PISTONS,
      REDSTONES,
      HOLD;

      private Stage() {
      }
   }

   private record TrapPlan(int targetId, BlockPos center, List<DoohickeyTrapModule.PistonPlan> pistons, double overlap, double distance) {
   }
}
