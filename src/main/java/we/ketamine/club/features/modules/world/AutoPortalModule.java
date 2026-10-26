package we.ketamine.club.features.modules.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.player.ChatUtil;
import we.ketamine.club.util.render.RenderUtil;

public class AutoPortalModule extends Module {
   private static final int IGNITE_PRIORITY = 40;
   private final Setting<Boolean> render = this.bool("Render", true);
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F);
   private final Setting<Color> fillColor = this.color("FillColor", 85, 0, 255, 44);
   private final Setting<Color> outlineColor = this.color("OutlineColor", 85, 0, 255, 44);
   private final List<BlockPos> portalBlocks = new ArrayList<>();
   private final Map<BlockPos, Long> renderMap = new HashMap<>();

   public AutoPortalModule() {
      super("AutoPortal", "For the base hunter who has places to be.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.portalBlocks.clear();
      int obsidianCount = 0;

      for (int i = 0; i < 36; i++) {
         if (mc.player.getInventory().getItem(i).getItem() == Items.OBSIDIAN) {
            obsidianCount += mc.player.getInventory().getItem(i).getCount();
         }
      }

      if (obsidianCount < 10) {
         ChatUtil.sendMessage(Component.literal("[AutoPortal] Not enough obsidian (need at least 10)!"));
         this.disable();
      } else {
         Direction forward = mc.player.getDirection();
         Direction right = forward.getClockWise();
         BlockPos standingPos = mc.player.blockPosition();
         BlockPos blockBelow = standingPos.below();

         double blockHeight;
         try {
            blockHeight = mc.level.getBlockState(blockBelow).getCollisionShape(mc.level, blockBelow).max(Axis.Y);
         } catch (Exception var13) {
            blockHeight = 1.0;
         }

         if (blockHeight < 1.0) {
            standingPos = standingPos.above();
         }

         BlockPos base = standingPos.relative(forward, 2).relative(right, -1);
         List<BlockPos> frame = new ArrayList<>();
         frame.add(base.relative(right, 1));
         frame.add(base.relative(right, 2));

         for (int ix = 1; ix <= 3; ix++) {
            frame.add(base.relative(right, 0).above(ix));
         }

         for (int ix = 1; ix <= 3; ix++) {
            frame.add(base.relative(right, 3).above(ix));
         }

         frame.add(base.relative(right, 1).above(4));
         frame.add(base.relative(right, 2).above(4));
         long obsidianMatches = frame.stream().filter(pos -> mc.level.getBlockState(pos).getBlock().asItem() == Items.OBSIDIAN).count();
         if (obsidianMatches >= frame.size()) {
            ChatUtil.sendMessage(Component.literal("[AutoPortal] A portal already exists here!"));
            this.disable();
         } else {
            boolean obstructed = frame.stream()
               .anyMatch(pos -> !mc.level.getBlockState(pos).canBeReplaced() && mc.level.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN);
            if (obstructed) {
               ChatUtil.sendMessage(Component.literal("[AutoPortal] Portal area obstructed. Move and try again."));
               this.disable();
            } else {
               this.portalBlocks.addAll(frame);
            }
         }
      }
   }

   @Override
   public void onDisable() {
      KetamineClub.placementManager.removeQueuedFor(this.portalBlocks::contains);
      this.portalBlocks.clear();
      this.renderMap.clear();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !this.portalBlocks.isEmpty()) {
         boolean allDone = true;

         for (BlockPos pos : this.portalBlocks) {
            if (mc.level.getBlockState(pos).getBlock().asItem() != Items.OBSIDIAN) {
               allDone = false;
               break;
            }
         }

         if (allDone) {
            this.ignite();
            ChatUtil.sendMessage(Component.literal("[AutoPortal] Portal complete!"));
            this.disable();
         } else {
            Result obsidian = this.findHotbar(Items.OBSIDIAN);
            if (obsidian != null) {
               Vec3 eye = mc.player.getEyePosition();
               long now = System.currentTimeMillis();

               for (BlockPos posx : this.portalBlocks) {
                  BlockState state = mc.level.getBlockState(posx);
                  if (state.getBlock().asItem() != Items.OBSIDIAN) {
                     if (Vec3.atCenterOf(posx).distanceTo(eye) > 5.154) {
                        ChatUtil.sendMessage(Component.literal("[AutoPortal] Out of range \u2014 disabling."));
                        this.disable();
                        return;
                     }

                     if (state.canBeReplaced()) {
                        if (KetamineClub.placementManager.enqueue(posx, obsidian.slot())) {
                           this.renderMap.put(posx, now);
                        }
                     } else {
                        mc.gameMode.startDestroyBlock(posx, Direction.UP);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                     }
                  }
               }

               long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
               this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
            }
         }
      }
   }

   private void ignite() {
      Result fas = this.findHotbar(Items.FLINT_AND_STEEL);
      if (fas != null) {
         BlockPos firePos = this.portalBlocks.get(0).above();
         BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(firePos), Direction.UP, firePos, false);
         KetamineClub.swapManager.submit(new SwapRequest("AutoPortal_ignite", 40, fas, () -> {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
         }));
      }
   }

   private Result findHotbar(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         long now = System.currentTimeMillis();
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

         for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
            long age = now - entry.getValue();
            if (!(age > fadeMs)) {
               double t = age / fadeMs;
               Color fc = this.fillColor.getValue();
               Color oc = this.outlineColor.getValue();
               RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
               RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
            }
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }
}
