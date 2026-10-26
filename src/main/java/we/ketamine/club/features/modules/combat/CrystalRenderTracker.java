package we.ketamine.club.features.modules.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;

final class CrystalRenderTracker {
   private final Map<BlockPos, CrystalRenderTracker.Placement> pending = new LinkedHashMap<>();
   private final Map<Integer, CrystalRenderTracker.Crystal> crystals = new LinkedHashMap<>();

   CrystalRenderTracker() {
   }

   CrystalRenderTracker.Render placed(BlockPos base, float damage, long now, long timeout) {
      this.prune(now);
      BlockPos immutable = base.immutable();
      this.pending.put(immutable.above(), new CrystalRenderTracker.Placement(immutable, damage, now + timeout));
      trim(this.pending, 128);
      return new CrystalRenderTracker.Render(immutable, damage);
   }

   void spawned(BlockPos air, int id, UUID uuid, long now) {
      this.prune(now);
      CrystalRenderTracker.Crystal previous = this.crystals.get(id);
      if (previous == null || !previous.uuid().equals(uuid)) {
         CrystalRenderTracker.Placement placement = this.pending.remove(air);
         this.crystals.put(id, new CrystalRenderTracker.Crystal(uuid, placement));
         trim(this.crystals, 1024);
      }
   }

   CrystalRenderTracker.Render removed(int id) {
      CrystalRenderTracker.Crystal crystal = this.crystals.remove(id);
      return crystal != null && crystal.placement() != null ? new CrystalRenderTracker.Render(crystal.placement().base(), crystal.placement().damage()) : null;
   }

   void prune(long now) {
      this.pending.values().removeIf(placement -> now >= placement.expires());
   }

   void reset() {
      this.pending.clear();
      this.crystals.clear();
   }

   private static <K, V> void trim(Map<K, V> map, int maximum) {
      while (map.size() > maximum) {
         map.remove(map.keySet().iterator().next());
      }
   }

   private record Crystal(UUID uuid, CrystalRenderTracker.Placement placement) {
   }

   private record Placement(BlockPos base, float damage, long expires) {
   }

   record Render(BlockPos base, float damage) {
   }
}
