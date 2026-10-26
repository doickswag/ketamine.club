package we.ketamine.club.features.modules.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.render.RenderUtil;

public class ScaffoldModule extends Module {
   private final Setting<Double> radius = this.num("Radius", 1.0, 1.0, 5.0);
   private final Setting<Integer> depth = this.num("Depth", 1, 1, 5);
   private final Setting<Double> lookahead = this.num("Lookahead", 1.0, 0.0, 5.0);
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 85, 255, 85, 180).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 85, 255, 85, 35).setPage("Render");
   private final Map<BlockPos, Long> renderMap = new LinkedHashMap<>();

   public ScaffoldModule() {
      super("Scaffold", "Places blocks beneath you as you walk, through the PlacementManager. No rotations.", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.renderMap.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (!mc.player.isSpectator()) {
            int slot = this.findBlockHotbarSlot();
            if (slot >= 0) {
               Vec3 eye = mc.player.getEyePosition();
               Vec3 vel = mc.player.getDeltaMovement();
               double centerX = mc.player.getX() + vel.x * this.lookahead.getValue();
               double centerZ = mc.player.getZ() + vel.z * this.lookahead.getValue();
               int baseX = Mth.floor(centerX);
               int baseZ = Mth.floor(centerZ);
               int feetY = mc.player.blockPosition().getY();
               int drop = mc.options.keyShift.isDown() ? 1 : 0;
               double r = this.radius.getValue() - 1.0;
               double rSq = r * r;
               int scanR = (int)Math.ceil(r);
               int d = this.depth.getValue();
               List<BlockPos> candidates = new ArrayList<>();

               for (int x = -scanR; x <= scanR; x++) {
                  for (int z = -scanR; z <= scanR; z++) {
                     if (!(x * x + z * z > rSq)) {
                        for (int dy = 1; dy <= d; dy++) {
                           BlockPos pos = new BlockPos(baseX + x, feetY - dy - drop, baseZ + z);
                           BlockState state = mc.level.getBlockState(pos);
                           if (state.canBeReplaced() && PlaceUtil.canPlace(pos)) {
                              candidates.add(pos.immutable());
                           }
                        }
                     }
                  }
               }

               candidates.sort(Comparator.comparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p))));
               long now = System.currentTimeMillis();

               for (BlockPos pos : KetamineClub.placementManager.placeBatchOffhand(candidates, slot)) {
                  this.renderMap.put(pos, now);
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
            }
         }
      }
   }

   private int findBlockHotbarSlot() {
      int selected = mc.player.getInventory().getSelectedSlot();
      if (mc.player.getInventory().getItem(selected).getItem() instanceof BlockItem) {
         return selected;
      } else {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
               return i;
            }
         }

         return -1;
      }
   }

   @Subscribe
   private void onRenderTargets(Render3DEvent event) {
      if (!nullCheck() && this.render.getValue()) {
         Color line = this.lineColor.getValue();
         Color fill = this.fillColor.getValue();
         float lw = this.lineWidth.getValue();
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               float t = (float)(1.0 - age / fadeMs);
               BlockPos pos = entry.getKey();
               if (fill.getAlpha() > 0) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), pos, withAlpha(fill, (int)(fill.getAlpha() * t)));
               }

               if (line.getAlpha() > 0) {
                  RenderUtil.drawBox(event.getMatrix(), pos, withAlpha(line, (int)(line.getAlpha() * t)), lw);
               }
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }
}
