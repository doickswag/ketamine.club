package we.ketamine.club.features.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.InteractionUtil;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.traits.Util;

public class BreakIndicatorsModule extends Module {
   private final Setting<Boolean> useDoubleminePrediction = this.bool("UseDoubleminePrediction", false).setPage("General");
   private final Setting<Float> rebreakCompletionAmount = this.num("RebreakCompletionAmount", 0.7F, 0.0F, 1.5F).setPage("General");
   private final Setting<Float> completionAmount = this.num("FullCompletionAmount", 1.0F, 0.0F, 1.5F).setPage("General");
   private final Setting<Float> removeCompletionAmount = this.num("ForceRemoveCompletionAmount", 1.3F, 0.0F, 1.5F).setPage("General");
   private final Setting<Boolean> ignoreFriends = this.bool("IgnoreFriends", false).setPage("General");
   private final Setting<Boolean> holdRebreak = this.bool("HoldRebreak", true).setPage("General");
   private final Setting<Boolean> render = this.bool("DoRender", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> sideColor = this.color("SideColor", 255, 0, 80, 10).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 255, 255, 255, 40).setPage("Render");
   private final Setting<Boolean> chinese = this.bool("Chinese", false).setPage("Chinese");
   private final Setting<BreakIndicatorsModule.Easing> chineseEase = this.mode("Ease", BreakIndicatorsModule.Easing.CubicInOut)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue());
   private final Setting<Boolean> chineseProgress = this.bool("Progress", true).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Float> chineseTextScale = this.num("TextScale", 1.0F, 0.1F, 3.0F)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseProgress.getValue());
   private final Setting<Float> chineseMinTextScale = this.num("MinTextScale", 0.3F, 0.1F, 1.0F)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseProgress.getValue());
   private final Setting<Boolean> chineseSecond = this.bool("Second", true).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFill = this.color("Fill", 198, 176, 12, 78).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseBox = this.color("Box", 198, 176, 12, 255).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFriendFill = this.color("FriendFill", 30, 45, 169, 78).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseFriendBox = this.color("FriendBox", 30, 45, 169, 255).setPage("Chinese").setVisibility(v -> this.chinese.getValue());
   private final Setting<Color> chineseSecondFill = this.color("SecondFill", 255, 255, 255, 100)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseSecond.getValue());
   private final Setting<Color> chineseSecondBox = this.color("SecondBox", 255, 255, 255, 255)
      .setPage("Chinese")
      .setVisibility(v -> this.chinese.getValue() && this.chineseSecond.getValue());
   private static final Color PROGRESS_START = new Color(255, 6, 6);
   private static final Color PROGRESS_END = new Color(0, 255, 12);
   private static final Color DOUBLE_COLOR = new Color(255, 179, 96);
   private static final long BREAK_TIME_MS = 2500L;
   private final Map<BlockPos, BreakIndicatorsModule.BreakEntry> breaks = new ConcurrentHashMap<>();

   public BreakIndicatorsModule() {
      super("BreakIndicators", "Renders the progress of a block being broken.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.breaks.clear();
   }

   @Override
   public void onDisable() {
      this.breaks.clear();
   }

   @Subscribe
   private void onPacket(PacketEvent.Receive event) {
      if (mc.level != null && mc.player != null) {
         if (event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            BlockPos var11 = packet.getPos().immutable();
            BlockState state = mc.level.getBlockState(var11);
            if (InteractionUtil.canBreak(var11, state)) {
               Entity entity = mc.level.getEntity(packet.getId());
               int id = packet.getId();
               long now = System.currentTimeMillis();
               if (this.holdRebreak.getValue()) {
                  this.breaks.values().removeIf(b -> b.held && b.entityId == id && !b.pos.equals(var11));
               }

               BreakIndicatorsModule.BreakEntry existing = this.breaks.get(var11);
               if (existing != null) {
                  if (existing.held) {
                     existing.held = false;
                     existing.startMs = now;
                  }
               } else {
                  if (this.useDoubleminePrediction.getValue() && entity instanceof Player) {
                     List<BreakIndicatorsModule.BreakEntry> playerBreaks = this.breaks
                        .values()
                        .stream()
                        .filter(b -> b.entity == entity && !b.pos.equals(var11))
                        .sorted(Comparator.comparingLong(b -> b.startMs))
                        .toList();
                     if (playerBreaks.size() >= 2) {
                        this.breaks.remove(playerBreaks.getLast().pos);
                     }
                  }

                  this.breaks.put(var11, new BreakIndicatorsModule.BreakEntry(var11, id, entity, now));
               }
            }
         }
      }
   }

   public boolean isBlockBeingBroken(BlockPos pos) {
      return this.breaks.containsKey(pos);
   }

   public boolean isBlockBeingBrokenByFriend(BlockPos pos) {
      BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
      return entry != null && entry.entity instanceof Player player && KetamineClub.friendManager.isFriend(player);
   }

   public boolean isBlockBeingBrokenBySelf(BlockPos pos) {
      if (mc.player == null) {
         return false;
      } else {
         BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
         return entry != null && entry.entityId == mc.player.getId();
      }
   }

   public double getBlockBreakProgress(BlockPos pos) {
      BreakIndicatorsModule.BreakEntry entry = this.breaks.get(pos);
      if (entry == null) {
         return 0.0;
      } else {
         return entry.held ? 1.0 : Math.clamp(entry.progress(System.currentTimeMillis()), 0.0, 1.0);
      }
   }

   public Map<BlockPos, BreakIndicatorsModule.BreakInfo> getActiveBreaksSnapshot() {
      Map<BlockPos, BreakIndicatorsModule.BreakInfo> snapshot = new HashMap<>();

      for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
         if (!entry.held) {
            snapshot.put(entry.pos, new BreakIndicatorsModule.BreakInfo(entry.pos, entry.entity instanceof Player player ? player : null));
         }
      }

      return snapshot;
   }

   @Subscribe
   private void onRender(Render3DEvent event) {
      if (!nullCheck()) {
         long now = System.currentTimeMillis();
         Iterator<Entry<BlockPos, BreakIndicatorsModule.BreakEntry>> iterator = this.breaks.entrySet().iterator();

         while (iterator.hasNext()) {
            BreakIndicatorsModule.BreakEntry entry = iterator.next().getValue();
            BlockState state = mc.level.getBlockState(entry.pos);
            if (state.isAir()) {
               iterator.remove();
            } else if (!entry.held) {
               boolean finished = entry.progress(now) > this.removeCompletionAmount.getValue().floatValue() || !InteractionUtil.canBreak(entry.pos, state);
               if (finished) {
                  if (this.holdRebreak.getValue()) {
                     entry.held = true;
                  } else {
                     iterator.remove();
                  }
               }
            }
         }

         if (this.useDoubleminePrediction.getValue()) {
            Map<Player, List<BreakIndicatorsModule.BreakEntry>> playerBreakingBlocks = this.breaks
               .values()
               .stream()
               .filter(entryx -> entryx.entity instanceof Player)
               .sorted(Comparator.comparingLong(entryx -> entryx.startMs))
               .collect(Collectors.groupingBy(entryx -> (Player)entryx.entity, Collectors.toList()));

            for (List<BreakIndicatorsModule.BreakEntry> list : playerBreakingBlocks.values()) {
               list.forEach(entryx -> entryx.isRebreak = false);
               if (list.size() >= 2) {
                  list.getLast().isRebreak = true;
               }
            }
         }

         if (this.render.getValue()) {
            for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
               if (!this.ignoreFriends.getValue() || !(entry.entity instanceof Player player && KetamineClub.friendManager.isFriend(player))) {
                  entry.renderBlock(event, now);
               }
            }
         }
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (this.render.getValue() && this.chinese.getValue() && this.chineseProgress.getValue()) {
            if (MatrixCapture.projection != null) {
               GuiGraphicsExtractor graphics = event.getContext();
               long now = System.currentTimeMillis();

               record TextJob(double distSq, Runnable draw) {
               }

               List<TextJob> jobs = new ArrayList<>();

               for (BreakIndicatorsModule.BreakEntry entry : this.breaks.values()) {
                  if ((!this.ignoreFriends.getValue() || !(entry.entity instanceof Player player && KetamineClub.friendManager.isFriend(player)))
                     && (!entry.isRebreak || this.chineseSecond.getValue())) {
                     Vec3 center = Vec3.atCenterOf(entry.pos);
                     double distSq = mc.player.position().distanceToSqr(center);
                     jobs.add(new TextJob(distSq, () -> entry.renderText(graphics, center, Math.sqrt(distSq), now)));
                  }
               }

               jobs.sort(Comparator.comparingDouble(TextJob::distSq).reversed());

               for (TextJob job : jobs) {
                  job.draw.run();
               }
            }
         }
      }
   }

   private static Color fade(Color from, Color to, double t) {
      double p = Math.clamp(t, 0.0, 1.0);
      return new Color(
         (int)(from.getRed() + (to.getRed() - from.getRed()) * p),
         (int)(from.getGreen() + (to.getGreen() - from.getGreen()) * p),
         (int)(from.getBlue() + (to.getBlue() - from.getBlue()) * p)
      );
   }

   private final class BreakEntry {
      private final BlockPos pos;
      private final int entityId;
      private final Entity entity;
      private long startMs;
      private boolean isRebreak;
      private boolean held;

      private BreakEntry(BlockPos pos, int entityId, Entity entity, long startMs) {
         Objects.requireNonNull(BreakIndicatorsModule.this);
         super();
         this.pos = pos;
         this.entityId = entityId;
         this.entity = entity;
         this.startMs = startMs;
      }

      private boolean complete() {
         return this.held || Util.mc.level.getBlockState(this.pos).isAir();
      }

      private double easedScale(long now) {
         double completion = (this.isRebreak
               ? BreakIndicatorsModule.this.rebreakCompletionAmount.getValue()
               : BreakIndicatorsModule.this.completionAmount.getValue())
            .floatValue();
         double raw = this.held ? 1.0 : (completion <= 0.0 ? 1.0 : Math.clamp(this.progress(now) / completion, 0.0, 1.0));
         return Math.clamp(BreakIndicatorsModule.this.chineseEase.getValue().ease(raw), 0.0, 1.0);
      }

      private Color fillColorFor() {
         if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
            return BreakIndicatorsModule.this.chineseSecondFill.getValue();
         } else {
            return this.entity instanceof Player player && KetamineClub.friendManager.isFriend(player)
               ? BreakIndicatorsModule.this.chineseFriendFill.getValue()
               : BreakIndicatorsModule.this.chineseFill.getValue();
         }
      }

      private Color boxColorFor() {
         if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
            return BreakIndicatorsModule.this.chineseSecondBox.getValue();
         } else {
            return this.entity instanceof Player player && KetamineClub.friendManager.isFriend(player)
               ? BreakIndicatorsModule.this.chineseFriendBox.getValue()
               : BreakIndicatorsModule.this.chineseBox.getValue();
         }
      }

      private void renderChinese(Render3DEvent event, long now) {
         if (!this.isRebreak || BreakIndicatorsModule.this.chineseSecond.getValue()) {
            double size = 0.5 * (1.0 - this.easedScale(now));
            AABB box = new AABB(this.pos).deflate(size);
            RenderUtil.drawBoxFilled(event.getMatrix(), box, this.fillColorFor());
            RenderUtil.drawBox(event.getMatrix(), box, this.boxColorFor(), BreakIndicatorsModule.this.lineWidth.getValue());
         }
      }

      private void renderText(GuiGraphicsExtractor graphics, Vec3 center, double dist, long now) {
         float[] screen = MatrixCapture.worldToScreen(center.x, center.y, center.z);
         if (screen != null) {
            String name = this.entity != null ? this.entity.getName().getString() : "?";
            String status;
            Color statusColor;
            if (this.complete()) {
               status = "Broke";
               statusColor = Util.mc.level.getBlockState(this.pos).isAir() ? BreakIndicatorsModule.PROGRESS_END : BreakIndicatorsModule.PROGRESS_START;
            } else {
               double p = Math.clamp(this.progress(now), 0.0, 1.0);
               status = String.format("%.1f", p * 100.0);
               statusColor = BreakIndicatorsModule.fade(BreakIndicatorsModule.PROGRESS_START, BreakIndicatorsModule.PROGRESS_END, p);
            }

            if (this.isRebreak && BreakIndicatorsModule.this.chineseSecond.getValue()) {
               status = "Double";
               statusColor = BreakIndicatorsModule.DOUBLE_COLOR;
            }

            float s = (float)Math.max(
               (double)BreakIndicatorsModule.this.chineseMinTextScale.getValue().floatValue(),
               BreakIndicatorsModule.this.chineseTextScale.getValue().floatValue() * 8.0 / (dist + 8.0)
            );
            int lineH = 9;
            graphics.pose().pushMatrix();
            graphics.pose().translate(screen[0], screen[1]);
            graphics.pose().scale(s, s);
            graphics.text(Util.mc.font, name, -Util.mc.font.width(name) / 2, -lineH - 1, -1);
            graphics.text(Util.mc.font, status, -Util.mc.font.width(status) / 2, 1, statusColor.getRGB() | 0xFF000000);
            graphics.pose().popMatrix();
         }
      }

      private void renderBlock(Render3DEvent event, long now) {
         if (BreakIndicatorsModule.this.chinese.getValue()) {
            this.renderChinese(event, now);
         } else {
            BlockState state = Util.mc.level.getBlockState(this.pos);
            VoxelShape shape = state.getShape(Util.mc.level, this.pos);
            if (shape.isEmpty()) {
               RenderUtil.drawBoxFilled(event.getMatrix(), this.pos, BreakIndicatorsModule.this.sideColor.getValue());
               RenderUtil.drawBox(event.getMatrix(), this.pos, BreakIndicatorsModule.this.lineColor.getValue(), BreakIndicatorsModule.this.lineWidth.getValue());
            } else {
               AABB orig = shape.bounds();
               double completion = (this.isRebreak
                     ? BreakIndicatorsModule.this.rebreakCompletionAmount.getValue()
                     : BreakIndicatorsModule.this.completionAmount.getValue())
                  .floatValue();
               double scale = this.held ? 1.0 : (completion <= 0.0 ? 1.0 : Math.clamp(this.progress(now) / completion, 0.0, 1.0));
               double centerX = (orig.minX + orig.maxX) * 0.5;
               double centerY = (orig.minY + orig.maxY) * 0.5;
               double centerZ = (orig.minZ + orig.maxZ) * 0.5;
               double halfX = orig.getXsize() * scale * 0.5;
               double halfY = orig.getYsize() * scale * 0.5;
               double halfZ = orig.getZsize() * scale * 0.5;
               double x1 = this.pos.getX() + centerX - halfX;
               double y1 = this.pos.getY() + centerY - halfY;
               double z1 = this.pos.getZ() + centerZ - halfZ;
               double x2 = this.pos.getX() + centerX + halfX;
               double y2 = this.pos.getY() + centerY + halfY;
               double z2 = this.pos.getZ() + centerZ + halfZ;
               AABB renderBox = new AABB(x1, y1, z1, x2, y2, z2);
               RenderUtil.drawBoxFilled(event.getMatrix(), renderBox, BreakIndicatorsModule.this.sideColor.getValue());
               RenderUtil.drawBox(
                  event.getMatrix(), renderBox, BreakIndicatorsModule.this.lineColor.getValue(), BreakIndicatorsModule.this.lineWidth.getValue()
               );
            }
         }
      }

      private double progress(long now) {
         return (now - this.startMs) / 2500.0;
      }
   }

   public record BreakInfo(BlockPos pos, Player player) {
   }

   public static enum Easing {
      Linear,
      SineInOut,
      QuadInOut,
      CubicInOut,
      ExpoOut,
      BackOut;

      private Easing() {
      }

      public double ease(double t) {
         return switch (this) {
            case Linear -> t;
            case SineInOut -> -(Math.cos(Math.PI * t) - 1.0) / 2.0;
            case QuadInOut -> t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
            case CubicInOut -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
            case ExpoOut -> t >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
            case BackOut -> {
               double c1 = 1.70158;
               double c3 = c1 + 1.0;
               yield 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
            }
         };
      }
   }
}
