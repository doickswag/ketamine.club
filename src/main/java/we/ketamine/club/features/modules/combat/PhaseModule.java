package we.ketamine.club.features.modules.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.mixin.item.CooldownInstanceAccessor;
import we.ketamine.club.mixin.item.ItemCooldownsAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.player.ChatUtil;

public class PhaseModule extends Module {
   private static final double CORNER_THRESHOLD = 0.5;
   private static final double CORNER_OFFSET = 0.5;
   private static final int PRIORITY = 150;

   public PhaseModule() {
      super("Phase", "Phases into walls", Module.Category.COMBAT);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.player == null || mc.level == null) {
         this.disable();
      } else if (!KetamineClub.rotationManager.isSilentSyncRequiredAtLeast(150)) {
         Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
         if (!pearl.found()) {
            this.disable();
         } else if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
            this.announceCooldown(this.pearlCooldownTicks());
            this.disable();
         } else if (mc.player.isCrouching()) {
            this.disable();
         } else {
            Vec3 target = this.calculateTargetPos();
            float yaw = this.calcYaw(target);
            float pitch = mc.player.getBlockY() > 4 ? 85.0F : 75.0F;
            KetamineClub.rotationManager.submit(new RotationRequest("phase", 150, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.gameMode.ensureHasSentCarriedItem();
            boolean thrown = KetamineClub.swapManager.submit(new SwapRequest("Phase", 80, pearl, r -> {
               BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

               try {
                  mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
               } catch (Throwable var7) {
                  if (handler != null) {
                     try {
                        handler.close();
                     } catch (Throwable var6x) {
                        var7.addSuppressed(var6x);
                     }
                  }

                  throw var7;
               }

               if (handler != null) {
                  handler.close();
               }
            }, true));
            if (thrown) {
               this.disable();
            }
         }
      }
   }

   private int pearlCooldownTicks() {
      ItemCooldowns cooldowns = mc.player.getCooldowns();
      Identifier group = cooldowns.getCooldownGroup(new ItemStack(Items.ENDER_PEARL));
      if (group == null) {
         return 0;
      } else if (((ItemCooldownsAccessor)cooldowns).ketamineclub$getCooldowns().get(group) instanceof CooldownInstanceAccessor cooldown) {
         int remaining = cooldown.ketamineclub$getEndTime() - ((ItemCooldownsAccessor)cooldowns).ketamineclub$getTickCount();
         return Math.max(0, remaining);
      } else {
         return 0;
      }
   }

   private void announceCooldown(int ticks) {
      ChatUtil.sendPersistent(
         "phase:cooldown",
         Component.literal("Pearl cooldown is not finished: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(ticks + (ticks == 1 ? " tick" : " ticks")).withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}))
            .append(Component.literal(" remain.").withStyle(ChatFormatting.GRAY))
      );
   }

   private Vec3 calculateTargetPos() {
      double playerX = mc.player.getX();
      double playerZ = mc.player.getZ();
      double nearestIntX = Math.round(playerX);
      double nearestIntZ = Math.round(playerZ);
      double dxCorner = nearestIntX - playerX;
      double dzCorner = nearestIntZ - playerZ;
      double threshold = 0.5;
      double offset = 0.5;
      if (Math.abs(dxCorner) <= threshold && Math.abs(dzCorner) <= threshold) {
         return new Vec3(playerX + Mth.clamp(dxCorner, -offset, offset), mc.player.getY() - 0.5, playerZ + Mth.clamp(dzCorner, -offset, offset));
      } else {
         double A = Math.PI / 13;
         double B = Math.PI / 4;
         double x = playerX
            + Mth.clamp(this.toClosest(playerX, Math.floor(playerX) + (Math.PI / 13), Math.floor(playerX) + (Math.PI / 4)) - playerX, -0.2, 0.2);
         double z = playerZ
            + Mth.clamp(this.toClosest(playerZ, Math.floor(playerZ) + (Math.PI / 13), Math.floor(playerZ) + (Math.PI / 4)) - playerZ, -0.2, 0.2);
         return new Vec3(x, mc.player.getY() - 0.5, z);
      }
   }

   private double toClosest(double num, double min, double max) {
      return num - min > max - num ? max : min;
   }

   private float calcYaw(Vec3 target) {
      Vec3 eye = mc.player.getEyePosition();
      Vec3 diff = target.subtract(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
   }
}
