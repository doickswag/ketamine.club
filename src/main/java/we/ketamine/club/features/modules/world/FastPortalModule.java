package we.ketamine.club.features.modules.world;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class FastPortalModule extends Module {
   public Setting<Float> cooldown = this.num("Cooldown", 2.0F, 0.5F, 20.0F);
   private int cooldownTicks = 0;

   public FastPortalModule() {
      super("FastPortal", "Throws an ender pearl when inside nether portals", Module.Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.cooldownTicks = 0;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
         } else {
            Vec3 eye = mc.player.getEyePosition();
            BlockPos eyeBlockPos = BlockPos.containing(eye);
            if (mc.level.getBlockState(eyeBlockPos).is(Blocks.NETHER_PORTAL)) {
               Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
               if (pearl.found()) {
                  if (!mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
                     Vec3 portalCenter = new Vec3(eyeBlockPos.getX(), eyeBlockPos.getY() - 1, eyeBlockPos.getZ());
                     float yaw = this.calcYaw(eye, portalCenter);
                     float pitch = this.calcPitch(eye, portalCenter);
                     KetamineClub.rotationManager.submit(new RotationRequest("fastportal", 20, yaw, pitch, RotationRequest.Mode.SILENT));
                     mc.gameMode.ensureHasSentCarriedItem();
                     boolean sent = KetamineClub.swapManager.submit(new SwapRequest("FastPortal", 20, pearl, r -> {
                        BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

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
                     }));
                     if (sent) {
                        this.cooldownTicks = (int)(this.cooldown.getValue() * 20.0F);
                     }
                  }
               }
            }
         }
      }
   }

   private float calcYaw(Vec3 eye, Vec3 target) {
      Vec3 diff = target.subtract(eye);
      return (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
   }

   private float calcPitch(Vec3 eye, Vec3 target) {
      Vec3 diff = target.subtract(eye);
      double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
      return (float)(-Math.toDegrees(Math.atan2(diff.y, horizontalDist)));
   }
}
