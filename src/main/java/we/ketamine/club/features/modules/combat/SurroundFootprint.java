package we.ketamine.club.features.modules.combat;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import we.ketamine.club.util.PlaceUtil;

record SurroundFootprint(int minX, int maxX, int minZ, int maxZ, int feetY) {
   static SurroundFootprint of(AABB box, int feetY) {
      return new SurroundFootprint(PlaceUtil.minCell(box.minX), PlaceUtil.maxCell(box.maxX), PlaceUtil.minCell(box.minZ), PlaceUtil.maxCell(box.maxZ), feetY);
   }

   boolean wall(BlockPos pos) {
      if (pos.getY() != this.feetY) {
         return false;
      } else {
         int x = pos.getX();
         int z = pos.getZ();
         return (x == this.minX - 1 || x == this.maxX + 1) && z >= this.minZ && z <= this.maxZ
            || (z == this.minZ - 1 || z == this.maxZ + 1) && x >= this.minX && x <= this.maxX;
      }
   }

   boolean floor(BlockPos pos) {
      return pos.getY() == this.feetY - 1 && pos.getX() >= this.minX && pos.getX() <= this.maxX && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
   }

   boolean contains(BlockPos pos) {
      return this.wall(pos) || this.floor(pos);
   }

   Set<BlockPos> cells() {
      Set<BlockPos> result = new LinkedHashSet<>();

      for (int x = this.minX; x <= this.maxX; x++) {
         result.add(new BlockPos(x, this.feetY, this.minZ - 1));
         result.add(new BlockPos(x, this.feetY, this.maxZ + 1));

         for (int z = this.minZ; z <= this.maxZ; z++) {
            result.add(new BlockPos(x, this.feetY - 1, z));
         }
      }

      for (int z = this.minZ; z <= this.maxZ; z++) {
         result.add(new BlockPos(this.minX - 1, this.feetY, z));
         result.add(new BlockPos(this.maxX + 1, this.feetY, z));
      }

      return result;
   }
}
