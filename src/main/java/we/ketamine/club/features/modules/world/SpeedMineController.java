package we.ketamine.club.features.modules.world;

import java.util.Set;
import net.minecraft.core.BlockPos;

public interface SpeedMineController {
   default void markRebreakAir(BlockPos pos) {
   }

   default void resetRebreakStopBudget(BlockPos pos) {
   }

   boolean isEnabled();

   boolean hasActiveMining();

   boolean silentBreakBlock(BlockPos var1, double var2);

   boolean hasDelayedDestroy();

   boolean hasRebreakBlock();

   BlockPos getDelayedDestroyBlockPos();

   BlockPos getRebreakBlockPos();

   boolean canRebreakRebreakBlock();

   boolean inBreakRange(BlockPos var1);

   boolean alreadyBreaking(BlockPos var1);

   default boolean cancelPrimary(BlockPos pos) {
      return false;
   }

   BlockPos getPrimaryBlockPos();

   BlockPos getSecondaryBlockPos();

   boolean isDoubleBreakEnabled();

   void addFinishListener(SpeedMineController.MineFinishListener var1);

   void removeFinishListener(SpeedMineController.MineFinishListener var1);

   void holdRebreak(BlockPos var1, int var2);

   void clearRebreak();

   boolean relaneRebreakToDelayed(BlockPos var1);

   void collectMiningPositions(Set<BlockPos> var1, double var2);

   SpeedMineController.MineLaneState getDelayedLaneState();

   SpeedMineController.MineLaneState getRebreakLaneState();

   void setMineChainHandler(SpeedMineController.MineChainHandler var1);

   void clearMineChainHandler(SpeedMineController.MineChainHandler var1);

   String getDebugState();

   public interface MineChainHandler {
      default void onDelayedTick(SpeedMineController.MineLaneState state) {
      }

      default boolean shouldHandleRebreak(SpeedMineController.MineLaneState state) {
         return state != null && state.seenAir();
      }

      default boolean requiresRebreakToolSwap(SpeedMineController.MineLaneState state) {
         return this.shouldHandleRebreak(state);
      }

      default boolean shouldHoldPosition(BlockPos requested) {
         return false;
      }

      default boolean onRebreak(SpeedMineController.MineLaneState state, int toolSlot, Runnable stopAction) {
         return false;
      }
   }

   public interface MineFinishListener {
      void onMineFinish(BlockPos var1);
   }

   public static enum MineLane {
      DELAYED,
      REBREAK;

      private MineLane() {
      }
   }

   public record MineLaneState(
      SpeedMineController.MineLane lane,
      BlockPos pos,
      int clientTick,
      int elapsedTicks,
      int expectedBreakTick,
      double delta,
      double progress,
      boolean stopSent,
      boolean seenAir
   ) {
   }
}
