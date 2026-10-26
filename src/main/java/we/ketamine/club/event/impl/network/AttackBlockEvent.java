package we.ketamine.club.event.impl.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import we.ketamine.club.event.Event;

public class AttackBlockEvent extends Event {
   private final BlockPos pos;
   private final BlockState state;
   private final Direction direction;

   public AttackBlockEvent(BlockPos pos, BlockState state, Direction direction) {
      this.pos = pos;
      this.state = state;
      this.direction = direction;
   }

   public BlockPos getPos() {
      return this.pos;
   }

   public BlockState getState() {
      return this.state;
   }

   public Direction getDirection() {
      return this.direction;
   }
}
