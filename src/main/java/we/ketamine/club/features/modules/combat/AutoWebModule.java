package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;

public class AutoWebModule extends Module {
   private static final long RENDER_HOLD_MS = 500L;
   private static final long RENDER_FADE_MS = 350L;
   private final Setting<Double> range = this.num("TargetRange", 4.0, 1.0, 4.0).setPage("General");
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Integer> smoothness = this.num("Smoothness", 10, 1, 50).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 150, 150, 150, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 220, 220, 220, 220).setPage("Render");
   private int lastPlaceTick = -1;
   private BlockPos renderPos;
   private AABB smoothBox;
   private long lastTargetMs;

   public AutoWebModule() {
      super("AutoWeb", "Places cobwebs in cells intersecting enemy hitboxes.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.lastPlaceTick = -1;
      this.renderPos = null;
      this.smoothBox = null;
      this.lastTargetMs = 0L;
   }

   @Override
   public void onDisable() {
      this.renderPos = null;
      this.smoothBox = null;
      this.lastTargetMs = 0L;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         if (mc.player.containerMenu.containerId == 0) {
            TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
            double rangeSq = this.range.getValue() * this.range.getValue();
            List<Player> players = new ArrayList<>();

            for (Player player : mc.level.players()) {
               if (player != mc.player
                  && !player.isDeadOrDying()
                  && (targets == null || targets.isValidPlayerTarget(player))
                  && mc.player.distanceToSqr(player) <= rangeSq) {
                  players.add(player);
               }
            }

            if (!players.isEmpty()) {
               this.lastTargetMs = System.currentTimeMillis();
               players.sort(Comparator.comparingDouble(mc.player::distanceToSqr));
               int sinceLast = mc.player.tickCount - this.lastPlaceTick;
               if (sinceLast < 0 || sinceLast >= this.delay.getValue()) {
                  Result web = InventoryUtil.find(Items.COBWEB, InventoryUtil.PLACE_SCOPE);
                  if (web.found()) {
                     Vec3 eye = mc.player.getEyePosition();

                     for (Player playerx : players) {
                        for (AutoWebModule.Cell cell : this.intersectingCells(playerx.getBoundingBox(), eye)) {
                           if (KetamineClub.placementManager.placeDirectInEntity(cell.pos(), web.slot())) {
                              this.lastPlaceTick = mc.player.tickCount;
                              this.renderPos = cell.pos();
                              return;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private List<AutoWebModule.Cell> intersectingCells(AABB box, Vec3 eye) {
      List<AutoWebModule.Cell> cells = new ArrayList<>();

      for (int x = PlaceUtil.minCell(box.minX); x <= PlaceUtil.maxCell(box.maxX); x++) {
         for (int y = PlaceUtil.minCell(box.minY); y <= PlaceUtil.maxCell(box.maxY); y++) {
            for (int z = PlaceUtil.minCell(box.minZ); z <= PlaceUtil.maxCell(box.maxZ); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (mc.level.getBlockState(pos).canBeReplaced()) {
                  double overlap = this.overlap(box, pos);
                  if (!(overlap <= 0.0)) {
                     cells.add(new AutoWebModule.Cell(pos, overlap, eye.distanceToSqr(Vec3.atCenterOf(pos))));
                  }
               }
            }
         }
      }

      cells.sort(Comparator.comparingDouble(AutoWebModule.Cell::overlap).reversed().thenComparingDouble(AutoWebModule.Cell::distance));
      return cells;
   }

   private double overlap(AABB box, BlockPos pos) {
      double x = Math.max(0.0, Math.min(box.maxX, pos.getX() + 1.0) - Math.max(box.minX, (double)pos.getX()));
      double y = Math.max(0.0, Math.min(box.maxY, pos.getY() + 1.0) - Math.max(box.minY, (double)pos.getY()));
      double z = Math.max(0.0, Math.min(box.maxZ, pos.getZ() + 1.0) - Math.max(box.minZ, (double)pos.getZ()));
      return x * y * z;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPos != null) {
         float alpha = this.renderAlpha();
         if (!(alpha <= 0.0F)) {
            this.updateSmoothBox(new AABB(this.renderPos));
            RenderUtil.drawBoxFilled(event.getMatrix(), this.smoothBox, this.faded(this.fillColor.getValue(), alpha));
            RenderUtil.drawBox(event.getMatrix(), this.smoothBox, this.faded(this.outlineColor.getValue(), alpha), this.lineWidth.getValue());
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
         this.renderPos = null;
         this.smoothBox = null;
         return 0.0F;
      }
   }

   private Color faded(Color color, float alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha));
   }

   private void updateSmoothBox(AABB target) {
      if (this.smoothBox == null) {
         this.smoothBox = target;
      } else {
         double factor = 1.0 / this.smoothness.getValue().intValue();
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

   private record Cell(BlockPos pos, double overlap, double distance) {
   }
}
