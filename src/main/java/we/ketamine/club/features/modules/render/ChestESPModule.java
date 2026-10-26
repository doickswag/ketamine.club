package we.ketamine.club.features.modules.render;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;

public class ChestESPModule extends Module {
   private final Setting<Double> range = this.num("Range", 64.0, 8.0, 256.0).setPage("General");
   private final Setting<Boolean> fill = this.bool("Fill", true).setPage("Render");
   private final Setting<Boolean> outline = this.bool("Outline", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.1F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 170, 35, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 195, 70, 220).setPage("Render");
   private final Set<BlockPos> chestPositions = new HashSet<>();

   public ChestESPModule() {
      super("ChestESP", "Highlights nearby chests through walls.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.chestPositions.clear();
   }

   @Override
   public void onDisable() {
      this.chestPositions.clear();
   }

   @Override
   public void onTick() {
      if (nullCheck()) {
         this.chestPositions.clear();
      } else {
         this.chestPositions.clear();
         int radius = (int)Math.ceil(this.range.getValue());
         int minChunkX = Math.floorDiv(mc.player.blockPosition().getX() - radius, 16);
         int maxChunkX = Math.floorDiv(mc.player.blockPosition().getX() + radius, 16);
         int minChunkZ = Math.floorDiv(mc.player.blockPosition().getZ() - radius, 16);
         int maxChunkZ = Math.floorDiv(mc.player.blockPosition().getZ() + radius, 16);
         double rangeSquared = this.range.getValue() * this.range.getValue();

         for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
               if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                  LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                  if (chunk != null) {
                     for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (blockEntity instanceof ChestBlockEntity) {
                           BlockPos pos = blockEntity.getBlockPos();
                           double dx = pos.getX() + 0.5 - mc.player.getX();
                           double dy = pos.getY() + 0.5 - mc.player.getY();
                           double dz = pos.getZ() + 0.5 - mc.player.getZ();
                           if (dx * dx + dy * dy + dz * dz <= rangeSquared) {
                              this.chestPositions.add(pos.immutable());
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         for (BlockPos pos : this.chestPositions) {
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
      return this.chestPositions.isEmpty() ? null : String.valueOf(this.chestPositions.size());
   }
}
