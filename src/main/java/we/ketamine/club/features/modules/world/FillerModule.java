package we.ketamine.club.features.modules.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.BlockNameList;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;

public class FillerModule extends Module {
   private final Setting<String> blocks = this.str("Blocks", "cobblestone").setPage("Selection");
   private final Setting<FillerModule.Area> area = this.mode("Selection", FillerModule.Area.BELOW).setPage("Selection");
   private final Setting<FillerModule.FillMode> fillMode = this.mode("FillMode", FillerModule.FillMode.FULL).setPage("Selection");
   private final Setting<Integer> radius = this.num("Radius", 2, 0, 6).setPage("Selection");
   private final Setting<Integer> offsetX = this.num("OffsetX", 0, -8, 8).setPage("Offset");
   private final Setting<Integer> offsetY = this.num("OffsetY", 0, -8, 8).setPage("Offset");
   private final Setting<Integer> offsetZ = this.num("OffsetZ", 0, -8, 8).setPage("Offset");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Boolean> fill = this.bool("Fill", true).setPage("Render");
   private final Setting<Boolean> outline = this.bool("Outline", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.0F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 75, 190, 120, 40).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 95, 225, 145, 190).setPage("Render");
   private final List<BlockPos> targets = new ArrayList<>();
   private final Set<BlockPos> ownedQueue = new HashSet<>();

   public FillerModule() {
      super("Filler", "Fills a selected plane with chosen blocks through the PlacementManager.", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      KetamineClub.placementManager.removeQueuedFor(this.ownedQueue::contains);
      this.ownedQueue.clear();
      this.targets.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isCreative() && !mc.player.isSpectator()) {
         Result block = this.findSelectedBlock();
         this.buildTargets();
         Set<BlockPos> wanted = Set.copyOf(this.targets);
         KetamineClub.placementManager.removeQueuedFor(posx -> this.ownedQueue.contains(posx) && !wanted.contains(posx));
         this.ownedQueue.retainAll(wanted);
         if (block.found()) {
            for (BlockPos pos : this.targets) {
               this.ownedQueue.add(pos);
               KetamineClub.placementManager.enqueue(pos, block.slot());
            }

            KetamineClub.placementManager.flushQueue();
         }
      } else {
         this.targets.clear();
      }
   }

   private Result findSelectedBlock() {
      Set<String> selectedBlocks = BlockNameList.parse(this.blocks.getValue());
      return selectedBlocks.isEmpty()
         ? InventoryUtil.find((Predicate<ItemStack>)(stack -> false), InventoryUtil.PLACE_SCOPE)
         : InventoryUtil.find((Predicate<ItemStack>)(stack -> {
            if (stack.getItem() instanceof BlockItem blockItem) {
               String var4 = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
               return selectedBlocks.contains(var4);
            } else {
               return false;
            }
         }), InventoryUtil.PLACE_SCOPE);
   }

   private void buildTargets() {
      this.targets.clear();
      Direction facing = mc.player.getDirection();
      Direction right = facing.getClockWise();
      FillerModule.Area selectedArea = this.area.getValue();
      BlockPos center = mc.player
         .blockPosition()
         .offset(this.offsetX.getValue(), this.offsetY.getValue(), this.offsetZ.getValue())
         .relative(selectedArea.normal(right), selectedArea.baseDistance());
      int range = this.radius.getValue();

      for (int first = -range; first <= range; first++) {
         for (int second = -range; second <= range; second++) {
            if (this.included(first, second, range)) {
               BlockPos pos = selectedArea.position(center, facing, first, second);
               if (PlaceUtil.canPlace(pos)) {
                  this.targets.add(pos.immutable());
               }
            }
         }
      }

      Vec3 eye = mc.player.getEyePosition();
      this.targets.sort(Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx))));
   }

   private boolean included(int first, int second, int range) {
      int distanceSq = first * first + second * second;
      if (distanceSq > range * range) {
         return false;
      } else {
         return switch ((FillerModule.FillMode)this.fillMode.getValue()) {
            case FULL -> true;
            case OUTLINE -> range == 0 || distanceSq > (range - 1) * (range - 1);
            case CHECKER -> Math.floorMod(first + second, 2) == 0;
         };
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         for (BlockPos pos : this.targets) {
            if (this.fill.getValue()) {
               RenderUtil.drawBoxFilled(event.getMatrix(), pos, this.fillColor.getValue());
            }

            if (this.outline.getValue()) {
               RenderUtil.drawBox(event.getMatrix(), pos, this.outlineColor.getValue(), this.lineWidth.getValue());
            }
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.targets.isEmpty() ? null : Integer.toString(this.targets.size());
   }

   public static enum Area {
      ABOVE,
      RIGHT,
      LEFT,
      BELOW;

      private Area() {
      }

      private Direction normal(Direction right) {
         return switch (this) {
            case ABOVE -> Direction.UP;
            case RIGHT -> right;
            case LEFT -> right.getOpposite();
            case BELOW -> Direction.DOWN;
         };
      }

      private int baseDistance() {
         return this == ABOVE ? 2 : 1;
      }

      private BlockPos position(BlockPos center, Direction facing, int first, int second) {
         return switch (this) {
            case ABOVE, BELOW -> center.offset(first, 0, second);
            case RIGHT, LEFT -> center.relative(facing, first).above(second);
         };
      }
   }

   public static enum FillMode {
      FULL,
      OUTLINE,
      CHECKER;

      private FillMode() {
      }
   }
}
