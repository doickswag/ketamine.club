package we.ketamine.club.manager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import we.ketamine.club.event.impl.entity.TotemPopEvent;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;

public class PlayerInfoManager extends Feature {
   private final Map<UUID, Integer> totemPops = new ConcurrentHashMap<>();
   private final Set<UUID> lastTickAlive = ConcurrentHashMap.newKeySet();

   public PlayerInfoManager() {
   }

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onTotemPop(TotemPopEvent event) {
      this.totemPops.merge(event.getEntity().getUUID(), 1, Integer::sum);
   }

   @Subscribe
   public void onPreTick(PreTickEvent event) {
      if (mc.level != null && mc.player != null) {
         Set<UUID> currentlyAlive = new HashSet<>();
         Set<UUID> presentIds = new HashSet<>();

         for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            presentIds.add(id);
            if (player.isDeadOrDying()) {
               if (this.lastTickAlive.contains(id)) {
                  this.totemPops.remove(id);
               }
            } else {
               currentlyAlive.add(id);
            }
         }

         this.totemPops.keySet().removeIf(idx -> !presentIds.contains(idx));
         this.lastTickAlive.clear();
         this.lastTickAlive.addAll(currentlyAlive);
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.resetAll();
   }

   public int getTotemPops(UUID uuid) {
      return this.totemPops.getOrDefault(uuid, 0);
   }

   public void resetTotemPops(UUID uuid) {
      this.totemPops.remove(uuid);
   }

   public void resetAll() {
      this.totemPops.clear();
      this.lastTickAlive.clear();
   }

   public ItemStack getEquipment(Player player, EquipmentSlot slot) {
      return player.getItemBySlot(slot);
   }

   public ItemStack getMainHandItem(Player player) {
      return player.getMainHandItem();
   }

   public ItemStack getOffHandItem(Player player) {
      return player.getOffhandItem();
   }
}
