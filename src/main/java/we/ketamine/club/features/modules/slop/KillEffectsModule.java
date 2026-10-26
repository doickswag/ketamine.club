package we.ketamine.club.features.modules.slop;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class KillEffectsModule extends Module {
   private static final long ATTACK_MEMORY_MS = 5000L;
   private static final RandomSource RANDOM = RandomSource.create();
   public final Setting<KillEffectsModule.Effect> effect = this.mode("Effect", KillEffectsModule.Effect.LIGHTNING);
   public final Setting<Integer> strikes = this.num("Strikes", 1, 1, 5);
   private final Map<UUID, Long> recentlyAttacked = new ConcurrentHashMap<>();
   private final Set<UUID> aliveLastTick = ConcurrentHashMap.newKeySet();

   public KillEffectsModule() {
      super("KillEffects", "Adds client-side effects when a player you damaged dies.", Module.Category.SLOP);
      this.strikes.setVisibility(value -> this.effect.getValue() == KillEffectsModule.Effect.LIGHTNING);
   }

   @Override
   public void onEnable() {
      this.recentlyAttacked.clear();
      this.aliveLastTick.clear();
      if (mc.level != null) {
         for (Player player : mc.level.players()) {
            this.aliveLastTick.add(player.getUUID());
         }
      }
   }

   @Override
   public void onDisable() {
      this.recentlyAttacked.clear();
      this.aliveLastTick.clear();
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!nullCheck() && event.getPacket() instanceof ClientboundDamageEventPacket packet) {
         if (packet.sourceCauseId() == mc.player.getId()) {
            if (mc.level.getEntity(packet.entityId()) instanceof Player player && player != mc.player) {
               this.recentlyAttacked.put(player.getUUID(), System.currentTimeMillis());
            }
         }
      }
   }

   @Subscribe(
      priority = 100
   )
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         long now = System.currentTimeMillis();
         this.recentlyAttacked.entrySet().removeIf(entry -> now - entry.getValue() > 5000L);
         Set<UUID> aliveNow = new HashSet<>();

         for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            if (player.isAlive() && !player.isDeadOrDying()) {
               aliveNow.add(id);
            } else if (this.aliveLastTick.remove(id) && this.recentlyAttacked.containsKey(id)) {
               this.spawnEffect(player);
               this.recentlyAttacked.remove(id);
            }
         }

         this.aliveLastTick.clear();
         this.aliveLastTick.addAll(aliveNow);
      }
   }

   @Subscribe
   private void onDisconnect(DisconnectEvent event) {
      this.recentlyAttacked.clear();
      this.aliveLastTick.clear();
   }

   private void spawnEffect(Player player) {
      if (this.effect.getValue() != KillEffectsModule.Effect.LIGHTNING) {
         this.fireworkExplode(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
      } else {
         for (int i = 0; i < this.strikes.getValue(); i++) {
            LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, mc.level);
            bolt.setVisualOnly(true);
            bolt.setPos(player.getX(), player.getY(), player.getZ());
            mc.level.addEntity(bolt);
         }
      }
   }

   private void fireworkExplode(double x, double y, double z) {
      int amount = 4;

      for (int i = -amount; i <= amount; i++) {
         for (int j = -amount; j <= amount; j++) {
            for (int k = -amount; k <= amount; k++) {
               double vx = j + (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5;
               double vy = i + (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5;
               double vz = k + (RANDOM.nextDouble() - RANDOM.nextDouble()) * 0.5;
               double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
               if (!(length < 0.001)) {
                  mc.level.addParticle(ParticleTypes.FIREWORK, x, y, z, vx / length, vy / length, vz / length);
               }
            }
         }
      }
   }

   public static enum Effect {
      LIGHTNING,
      FIREWORK;

      private Effect() {
      }
   }
}
