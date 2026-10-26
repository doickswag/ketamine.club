package we.ketamine.club.features.modules.funny;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
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
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class TrickshotModule extends Module {
   private static final double TARGET_X = 0.5;
   private static final double TARGET_Y = 62.0;
   private static final double TARGET_Z = 0.5;
   private static final BlockPos TARGET_BP = BlockPos.containing(0.5, 62.0, 0.5);
   private static final double MAX_XZ_DIST = 60.0;
   private static final double PEARL_SPEED = 1.5;
   private static final double DRAG = 0.99;
   private static final double GRAVITY = 0.03;

   public TrickshotModule() {
      super("Trickshot", "Throws an ender pearl to 0 62 0 in the End.", Module.Category.FUNNY);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.player != null && mc.level != null) {
         if (!mc.level.dimension().identifier().getPath().equals("the_end")) {
            this.disable();
         } else {
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            if (px * px + pz * pz > 3600.0) {
               this.disable();
            } else {
               Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
               if (!pearl.found()) {
                  this.disable();
               } else if (mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
                  this.disable();
               } else {
                  Vec3 eye = mc.player.getEyePosition();
                  Vec3 spawnPos = eye.subtract(0.0, 0.1, 0.0);
                  double dX = 0.5 - eye.x;
                  double dZ = 0.5 - eye.z;
                  if (dX * dX + dZ * dZ < 0.001) {
                     this.disable();
                  } else {
                     float yaw = (float)Math.toDegrees(Math.atan2(-dX, dZ));
                     Float pitchResult = this.calculatePitch(yaw, spawnPos);
                     if (pitchResult == null) {
                        this.disable();
                     } else {
                        float pitch = pitchResult;
                        KetamineClub.rotationManager.submit(new RotationRequest("trickshot", 20, yaw, pitch, RotationRequest.Mode.SILENT));
                        mc.gameMode.ensureHasSentCarriedItem();
                        KetamineClub.swapManager
                           .submit(
                              new SwapRequest(
                                 "Trickshot",
                                 20,
                                 pearl,
                                 r -> {
                                    BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level)
                                       .ketamineclub$getBlockStatePredictionHandler()
                                       .startPredicting();

                                    try {
                                       mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
                                    } catch (Throwable var7x) {
                                       if (handler != null) {
                                          try {
                                             handler.close();
                                          } catch (Throwable var6x) {
                                             var7x.addSuppressed(var6x);
                                          }
                                       }

                                       throw var7x;
                                    }

                                    if (handler != null) {
                                       handler.close();
                                    }
                                 }
                              )
                           );
                        this.disable();
                     }
                  }
               }
            }
         }
      } else {
         this.disable();
      }
   }

   private Float calculatePitch(float yaw, Vec3 spawnPos) {
      Float fallback = null;

      for (float pitch = -80.0F; pitch <= 80.0F; pitch += 0.25F) {
         Double vy = this.tryPitch(yaw, pitch, spawnPos);
         if (vy != null) {
            if (vy < 0.0) {
               return pitch;
            }

            if (fallback == null) {
               fallback = pitch;
            }
         }
      }

      return fallback;
   }

   private Double tryPitch(float yaw, float pitch, Vec3 spawnPos) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double vx = -Math.sin(yawRad) * Math.cos(pitchRad) * 1.5;
      double vy = -Math.sin(pitchRad) * 1.5;
      double vz = Math.cos(yawRad) * Math.cos(pitchRad) * 1.5;
      double x = spawnPos.x;
      double y = spawnPos.y;
      double z = spawnPos.z;

      for (int t = 0; t < 1000; t++) {
         double var22 = vy - 0.03;
         vx *= 0.99;
         vy = var22 * 0.99;
         vz *= 0.99;
         x += vx;
         y += vy;
         z += vz;
         BlockPos bp = BlockPos.containing(x, y, z);
         if (bp.equals(TARGET_BP)) {
            return vy;
         }

         if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) {
            return null;
         }
      }

      return null;
   }
}
