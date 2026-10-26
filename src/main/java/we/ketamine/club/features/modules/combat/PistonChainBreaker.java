package we.ketamine.club.features.modules.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.util.InteractionUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.traits.Util;

final class PistonChainBreaker implements Util {
   private static final String ID = "PistonCrystal_chainMine";
   private BlockPos target;
   private SpeedMineController miner;
   private boolean requestedMine;
   private SwapManager.SwapHandle hold;
   private Level level;
   private Player player;
   private int toolSlot = -1;
   private int heldSlot = -1;
   private Item tool;
   private boolean swapped;
   private boolean stopSent;
   private double progress;
   private int lastTick;
   private boolean rotate;

   PistonChainBreaker() {
   }

   boolean canBreak(BlockPos pos) {
      if (mc.player != null && mc.level != null && mc.gameMode != null) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         return mine != null && mine.isEnabled() && !mc.player.isCreative()
            ? mine.inBreakRange(pos)
            : mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= mc.player.blockInteractionRange();
      } else {
         return false;
      }
   }

   boolean start(BlockPos pos, boolean rotate) {
      if (this.target != null) {
         return this.target.equals(pos);
      } else if (this.canBreak(pos)
         && mc.level.getBlockState(pos).getBlock() instanceof PistonBaseBlock
         && !mc.gameMode.isDestroying()
         && !mc.options.keyAttack.isDown()) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         if (mine != null && mine.isEnabled() && !mc.player.isCreative()) {
            boolean existing = mine.alreadyBreaking(pos);
            if (existing || !mine.hasActiveMining() && mine.silentBreakBlock(pos, 5.0)) {
               this.target = pos.immutable();
               this.miner = mine;
               this.requestedMine = !existing;
               this.level = mc.level;
               this.player = mc.player;
               return true;
            } else {
               return false;
            }
         } else if (mc.player.containerMenu.containerId == 0 && InventoryUtil.cursor().isEmpty() && !KetamineClub.swapManager.willClickSlotBeLimited(10)) {
            AutoCrystalModule crystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (crystal != null) {
               crystal.releaseCompletedPlacementSwap();
            }

            this.hold = KetamineClub.swapManager.acquireLease("PistonCrystal_chainMine", 55);
            if (this.hold == null) {
               return false;
            } else {
               this.level = mc.level;
               this.player = mc.player;
               this.target = pos.immutable();
               this.heldSlot = InventoryUtil.selected();
               this.toolSlot = this.heldSlot;
               BlockState state = this.level.getBlockState(this.target);
               float best = InteractionUtil.getBreakDelta(this.player.getInventory().getItem(this.toolSlot), state, this.target);

               for (int slot = 0; slot < 36; slot++) {
                  float delta = InteractionUtil.getBreakDelta(this.player.getInventory().getItem(slot), state, this.target);
                  if (!(delta <= best)) {
                     best = delta;
                     this.toolSlot = slot;
                  }
               }

               this.tool = this.player.getInventory().getItem(this.toolSlot).getItem();
               this.swapped = this.toolSlot != this.heldSlot;
               if (this.swapped) {
                  InventoryUtil.swapToHotbarSlot(this.toolSlot, this.heldSlot);
               }

               this.rotate = rotate;
               this.stopSent = false;
               this.progress = 0.0;
               this.lastTick = this.player.tickCount;
               mc.gameMode.ensureHasSentCarriedItem();
               this.send(Action.START_DESTROY_BLOCK);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   boolean tick() {
      if (this.target == null) {
         return true;
      } else if (mc.level == null || mc.player == null || this.level != mc.level || this.player != mc.player) {
         this.close();
         return false;
      } else if (mc.level.getBlockState(this.target).isAir()) {
         this.close();
         return true;
      } else if (this.miner == null) {
         SpeedMineController otherMine = KetamineClub.moduleManager.getSpeedMine();
         if (this.level == mc.level
            && this.player == mc.player
            && KetamineClub.swapManager.holds(this.hold)
            && InventoryUtil.selected() == this.heldSlot
            && !mc.gameMode.isDestroying()
            && !mc.options.keyAttack.isDown()
            && this.canBreak(this.target)
            && (otherMine == null || !otherMine.isEnabled() || !otherMine.hasActiveMining())) {
            if (!KetamineClub.swapManager.holdsActive(this.hold) || this.stopSent || this.lastTick == this.player.tickCount) {
               return true;
            } else if (!this.player.getInventory().getItem(this.heldSlot).is(this.tool)) {
               this.close();
               return false;
            } else {
               this.lastTick = this.player.tickCount;
               this.progress = this.progress + this.level.getBlockState(this.target).getDestroyProgress(this.player, this.level, this.target);
               if (this.progress >= 1.0) {
                  this.send(Action.STOP_DESTROY_BLOCK);
                  this.stopSent = true;
               }

               return true;
            }
         } else {
            this.close();
            return false;
         }
      } else if (this.miner.isEnabled() && this.miner.alreadyBreaking(this.target)) {
         return true;
      } else {
         this.close();
         return false;
      }
   }

   private void send(Action action) {
      Vec3 eye = mc.player.getEyePosition();
      Direction face = Direction.getApproximateNearest(eye.subtract(Vec3.atCenterOf(this.target)));
      if (this.rotate && action != Action.ABORT_DESTROY_BLOCK) {
         Vec3 hit = Vec3.atCenterOf(this.target).relative(face, 0.5);
         float[] angles = MathUtil.calcAngle(eye, hit);
         KetamineClub.rotationManager
            .submit(new RotationRequest("PistonCrystal_chainMine", 55, angles[0], angles[1], RotationRequest.Mode.SILENT, false, true));
      }

      mc.getConnection().send(new ServerboundPlayerActionPacket(action, this.target, face));
      if (action != Action.ABORT_DESTROY_BLOCK) {
         mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
      }
   }

   void close() {
      if (this.miner != null
         && this.level == mc.level
         && this.player == mc.player
         && this.requestedMine
         && this.target != null
         && !mc.options.keyAttack.isDown()) {
         this.miner.cancelPrimary(this.target);
      }

      if (this.hold != null && this.level == mc.level && this.player == mc.player && mc.getConnection() != null) {
         SpeedMineController otherMine = KetamineClub.moduleManager.getSpeedMine();
         if (this.target != null
            && !mc.level.getBlockState(this.target).isAir()
            && !mc.gameMode.isDestroying()
            && !mc.options.keyAttack.isDown()
            && (otherMine == null || !otherMine.isEnabled() || !otherMine.hasActiveMining())) {
            this.send(Action.ABORT_DESTROY_BLOCK);
         }

         if (this.swapped
            && mc.player.containerMenu.containerId == 0
            && (mc.player.getInventory().getItem(this.heldSlot).is(this.tool) || mc.player.getInventory().getItem(this.heldSlot).isEmpty())) {
            InventoryUtil.swapToHotbarSlot(this.toolSlot, this.heldSlot);
         }
      }

      KetamineClub.swapManager.release(this.hold);
      KetamineClub.rotationManager.cancel("PistonCrystal_chainMine");
      this.target = null;
      this.miner = null;
      this.requestedMine = false;
      this.hold = null;
      this.level = null;
      this.player = null;
      this.swapped = false;
      this.toolSlot = this.heldSlot = -1;
      this.tool = null;
   }
}
