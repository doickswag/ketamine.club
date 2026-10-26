package we.ketamine.club.features.modules.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.BlockNameList;
import we.ketamine.club.util.InteractionUtil;
import we.ketamine.club.util.render.RenderUtil;

public class NukerModule extends Module {
   private static final double MAX_SPEED_MINE_RANGE = 7.0;
   private static final int SCAN_PADDING = 1;
   private static final int MAX_CANDIDATES = 4096;
   private static final int REQUEST_DELAY_TICKS = 1;
   private final Setting<String> blocks = this.str("Blocks", "stone,cobblestone,deepslate").setPage("Selection");
   private final Setting<NukerModule.MiningPattern> pattern = this.mode("Pattern", NukerModule.MiningPattern.SPHERE).setPage("Selection");
   private final Setting<NukerModule.Targeting> targeting = this.mode("Targeting", NukerModule.Targeting.CLOSEST).setPage("Selection");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Boolean> fill = this.bool("Fill", true).setPage("Render");
   private final Setting<Boolean> outline = this.bool("Outline", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.1F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 170, 35, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 195, 70, 210).setPage("Render");
   private final Setting<Color> activeColor = this.color("ActiveColor", 255, 55, 55, 120).setPage("Render");
   private final List<BlockPos> renderCandidates = new ArrayList<>();
   private SpeedMineController speedMine;
   private int lastRequestTick = -1000000;

   public NukerModule() {
      super("Nuker", "Automatically mines selected blocks within SpeedMine2 range.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.setSpeedMine(KetamineClub.moduleManager.getSpeedMine());
      this.lastRequestTick = -1000000;
      this.renderCandidates.clear();
   }

   @Override
   public void onDisable() {
      this.speedMine = null;
      this.renderCandidates.clear();
   }

   @Subscribe
   private void onTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isCreative() && !mc.player.isSpectator()) {
         this.setSpeedMine(KetamineClub.moduleManager.getSpeedMine());
         this.scanCandidates();
         if (this.speedMine != null && this.speedMine.isEnabled()) {
            if (!this.speedMine.hasRebreakBlock() || this.speedMine.canRebreakRebreakBlock()) {
               if (mc.player.tickCount - this.lastRequestTick >= 1) {
                  BlockPos primary = this.speedMine.getPrimaryBlockPos();
                  BlockPos secondary = this.speedMine.getSecondaryBlockPos();
                  if (primary == null || secondary == null) {
                     if (this.speedMine.isDoubleBreakEnabled() || primary == null) {
                        for (BlockPos candidate : this.renderCandidates) {
                           if (!this.speedMine.alreadyBreaking(candidate) && this.speedMine.silentBreakBlock(candidate, 15.0)) {
                              this.lastRequestTick = mc.player.tickCount;
                              if (this.speedMine.getPrimaryBlockPos() != null && this.speedMine.getSecondaryBlockPos() != null) {
                                 return;
                              }

                              if (!this.speedMine.isDoubleBreakEnabled()) {
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.renderCandidates.clear();
      }
   }

   private void setSpeedMine(SpeedMineController next) {
      if (this.speedMine != next) {
         this.speedMine = next;
      }
   }

   private void scanCandidates() {
      this.renderCandidates.clear();
      if (this.speedMine != null) {
         Set<String> selectedBlocks = BlockNameList.parse(this.blocks.getValue());
         if (!selectedBlocks.isEmpty()) {
            Player player = mc.player;
            BlockPos origin = player.blockPosition();
            Vec3 eye = player.getEyePosition();
            int radius = (int)Math.ceil(7.0) + 1;
            int minX = (int)Math.floor(eye.x) - radius;
            int maxX = (int)Math.floor(eye.x) + radius;
            int minY = (int)Math.floor(eye.y) - radius;
            int maxY = (int)Math.floor(eye.y) + radius;
            int minZ = (int)Math.floor(eye.z) - radius;
            int maxZ = (int)Math.floor(eye.z) + radius;

            for (int x = minX; x <= maxX && this.renderCandidates.size() < 4096; x++) {
               for (int y = minY; y <= maxY && this.renderCandidates.size() < 4096; y++) {
                  for (int z = minZ; z <= maxZ && this.renderCandidates.size() < 4096; z++) {
                     BlockPos pos = new BlockPos(x, y, z);
                     if (this.speedMine.inBreakRange(pos) && this.matchesPattern(pos, origin)) {
                        BlockState state = mc.level.getBlockState(pos);
                        if (!state.isAir()
                           && InteractionUtil.canBreak(pos, state)
                           && !new AABB(pos).intersects(player.getBoundingBox())
                           && this.matchesSelection(state, selectedBlocks)) {
                           this.renderCandidates.add(pos);
                        }
                     }
                  }
               }
            }

            this.renderCandidates.sort(this.comparator(eye));
         }
      }
   }

   private boolean matchesPattern(BlockPos pos, BlockPos origin) {
      return switch ((NukerModule.MiningPattern)this.pattern.getValue()) {
         case SPHERE -> true;
         case HORIZONTAL -> pos.getY() == origin.getY();
         case VERTICAL -> pos.getX() == origin.getX() && pos.getZ() == origin.getZ();
         case ABOVE -> pos.getY() >= origin.getY();
         case BELOW -> pos.getY() <= origin.getY();
         case SURFACE -> this.hasAirNeighbor(pos);
      };
   }

   private boolean hasAirNeighbor(BlockPos pos) {
      for (Direction direction : Direction.values()) {
         if (mc.level.getBlockState(pos.relative(direction)).isAir()) {
            return true;
         }
      }

      return false;
   }

   private Comparator<BlockPos> comparator(Vec3 eye) {
      Comparator<BlockPos> distance = Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos)));
      Comparator<BlockPos> stable = Comparator.<BlockPos>comparingLong(pos -> pos.getY())
         .thenComparingLong(pos -> pos.getX())
         .thenComparingLong(pos -> pos.getZ());

      return switch ((NukerModule.Targeting)this.targeting.getValue()) {
         case CLOSEST -> distance.thenComparing(stable);
         case FURTHEST -> distance.reversed().thenComparing(stable);
         case UP_DOWN -> Comparator.<BlockPos>comparingLong(pos -> pos.getY()).reversed().thenComparing(distance).thenComparing(stable);
         case DOWN_UP -> Comparator.<BlockPos>comparingLong(pos -> pos.getY()).thenComparing(distance).thenComparing(stable);
         case NORTH_SOUTH -> Comparator.<BlockPos>comparingLong(pos -> pos.getZ()).thenComparing(distance).thenComparing(stable);
         case SOUTH_NORTH -> Comparator.<BlockPos>comparingLong(pos -> pos.getZ()).reversed().thenComparing(distance).thenComparing(stable);
         case WEST_EAST -> Comparator.<BlockPos>comparingLong(pos -> pos.getX()).thenComparing(distance).thenComparing(stable);
         case EAST_WEST -> Comparator.<BlockPos>comparingLong(pos -> pos.getX()).reversed().thenComparing(distance).thenComparing(stable);
      };
   }

   private boolean matchesSelection(BlockState state, Set<String> selectedBlocks) {
      String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
      return selectedBlocks.contains(id);
   }

   @Override
   public void fromJson(JsonElement element) {
      super.fromJson(element);
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (!object.has("Blocks") && object.has("Selection")) {
            this.blocks.setValue(BlockNameList.readSetting(object.get("Selection")));
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && !nullCheck()) {
         for (BlockPos pos : this.renderCandidates) {
            boolean active = this.speedMine != null && this.speedMine.alreadyBreaking(pos);
            if (this.fill.getValue()) {
               RenderUtil.drawBoxFilled(event.getMatrix(), pos, active ? this.activeColor.getValue() : this.fillColor.getValue());
            }

            if (this.outline.getValue()) {
               RenderUtil.drawBox(event.getMatrix(), pos, active ? this.activeColor.getValue() : this.outlineColor.getValue(), this.lineWidth.getValue());
            }
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.renderCandidates.isEmpty()
         ? null
         : this.renderCandidates.size() + (this.speedMine == null ? "" : " | " + (this.speedMine.getSecondaryBlockPos() == null ? "1" : "2"));
   }

   public static enum MiningPattern {
      SPHERE,
      HORIZONTAL,
      VERTICAL,
      ABOVE,
      BELOW,
      SURFACE;

      private MiningPattern() {
      }
   }

   public static enum Targeting {
      CLOSEST,
      FURTHEST,
      UP_DOWN,
      DOWN_UP,
      NORTH_SOUTH,
      SOUTH_NORTH,
      WEST_EAST,
      EAST_WEST;

      private Targeting() {
      }
   }
}
