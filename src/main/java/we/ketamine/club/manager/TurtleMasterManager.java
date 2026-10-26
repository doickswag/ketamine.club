package we.ketamine.club.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;

public class TurtleMasterManager extends Feature {
   private static final long EFFECT_PAIR_WINDOW_MS = 1500L;
   private final Map<UUID, TurtleMasterManager.TurtleData> active = new ConcurrentHashMap<>();
   private final Map<UUID, TurtleMasterManager.PendingEffects> pending = new HashMap<>();
   private final Map<Integer, TurtleMasterManager.TrackedPot> trackedPots = new HashMap<>();

   public TurtleMasterManager() {
   }

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (mc.level != null) {
         if (event.getPacket() instanceof ClientboundUpdateMobEffectPacket packet) {
            this.handleUpdate(packet);
         } else if (event.getPacket() instanceof ClientboundRemoveMobEffectPacket packet) {
            this.handleRemove(packet);
         }
      }
   }

   @Subscribe
   public void onPreTick(PreTickEvent event) {
      if (mc.level != null && mc.player != null) {
         long now = System.currentTimeMillis();
         this.trackPots(now);

         for (Player player : mc.level.players()) {
            this.observeLiveEffects(player, now);
         }

         this.prune(now);
      } else {
         this.resetAll();
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.resetAll();
   }

   private void handleUpdate(ClientboundUpdateMobEffectPacket packet) {
      if (mc.level.getEntity(packet.getEntityId()) instanceof Player player) {
         Holder<MobEffect> effect = packet.getEffect();
         boolean resistance = effect.is(MobEffects.RESISTANCE);
         boolean slowness = effect.is(MobEffects.SLOWNESS);
         if (resistance || slowness) {
            long now = System.currentTimeMillis();
            TurtleMasterManager.PendingEffects effects = this.pending.computeIfAbsent(player.getUUID(), ignored -> new TurtleMasterManager.PendingEffects());
            if (resistance) {
               effects.resistanceSeenMs = now;
               effects.resistanceDuration = packet.getEffectDurationTicks();
               effects.resistanceAmplifier = packet.getEffectAmplifier();
            } else {
               effects.slownessSeenMs = now;
               effects.slownessDuration = packet.getEffectDurationTicks();
               effects.slownessAmplifier = packet.getEffectAmplifier();
            }

            if (effects.hasPair(now)) {
               this.mark(player, effects.resistanceDuration, effects.resistanceAmplifier, effects.slownessDuration, effects.slownessAmplifier, now);
            }
         }
      }
   }

   private void handleRemove(ClientboundRemoveMobEffectPacket packet) {
      Holder<MobEffect> effect = packet.effect();
      if (effect.is(MobEffects.RESISTANCE) || effect.is(MobEffects.SLOWNESS)) {
         if (packet.getEntity(mc.level) instanceof Player player) {
            this.pending.remove(player.getUUID());
            this.active.remove(player.getUUID());
         }
      }
   }

   private void observeLiveEffects(Player player, long now) {
      if (player != null && !player.isRemoved()) {
         MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
         MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
         if (resistance != null && slowness != null) {
            this.mark(player, resistance.getDuration(), resistance.getAmplifier(), slowness.getDuration(), slowness.getAmplifier(), now);
         }
      }
   }

   private void trackPots(long now) {
      Set<Integer> alive = new HashSet<>();

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof AbstractThrownPotion pot) {
            PotionContents contents = (PotionContents)pot.getItem().get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
               MobEffectInstance resistance = null;
               MobEffectInstance slowness = null;

               for (MobEffectInstance effect : contents.getAllEffects()) {
                  if (effect.getEffect().is(MobEffects.RESISTANCE)) {
                     resistance = effect;
                  } else if (effect.getEffect().is(MobEffects.SLOWNESS)) {
                     slowness = effect;
                  }
               }

               if (resistance != null && slowness != null) {
                  alive.add(pot.getId());
                  this.trackedPots
                     .put(
                        pot.getId(),
                        new TurtleMasterManager.TrackedPot(
                           pot.position().add(pot.getDeltaMovement().scale(0.5)),
                           resistance.getDuration(),
                           resistance.getAmplifier(),
                           slowness.getDuration(),
                           slowness.getAmplifier()
                        )
                     );
               }
            }
         }
      }

      Iterator<Entry<Integer, TurtleMasterManager.TrackedPot>> iterator = this.trackedPots.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<Integer, TurtleMasterManager.TrackedPot> entry = iterator.next();
         if (!alive.contains(entry.getKey())) {
            iterator.remove();
            this.applySplash(entry.getValue(), now);
         }
      }
   }

   private void applySplash(TurtleMasterManager.TrackedPot pot, long now) {
      AABB splashBox = AABB.ofSize(pot.pos(), 8.0, 4.0, 8.0);

      for (Player player : mc.level.players()) {
         if (player != null && !player.isRemoved() && player.isAlive() && player.getBoundingBox().intersects(splashBox)) {
            double distanceSq = player.distanceToSqr(pot.pos());
            if (!(distanceSq > 16.0)) {
               double proximity = 1.0 - Math.sqrt(distanceSq) / 4.0;
               if (!(proximity <= 0.0)) {
                  int resistanceDuration = (int)(proximity * pot.resistanceDuration() + 0.5);
                  int slownessDuration = (int)(proximity * pot.slownessDuration() + 0.5);
                  if (resistanceDuration > 20 || slownessDuration > 20) {
                     this.mark(player, resistanceDuration, pot.resistanceAmplifier(), slownessDuration, pot.slownessAmplifier(), now);
                  }
               }
            }
         }
      }
   }

   private void mark(Player player, int resistanceDuration, int resistanceAmplifier, int slownessDuration, int slownessAmplifier, long now) {
      if (player != null) {
         int duration = Math.min(this.normalize(resistanceDuration), this.normalize(slownessDuration));
         long expiresAtMs = duration == Integer.MAX_VALUE ? Long.MAX_VALUE : now + duration * 50L;
         this.active.put(player.getUUID(), new TurtleMasterManager.TurtleData(resistanceAmplifier, slownessAmplifier, expiresAtMs));
      }
   }

   private int normalize(int durationTicks) {
      return durationTicks == -1 ? Integer.MAX_VALUE : Math.max(0, durationTicks);
   }

   private void prune(long now) {
      Set<UUID> present = new HashSet<>();

      for (Player player : mc.level.players()) {
         present.add(player.getUUID());
      }

      this.active.entrySet().removeIf(entry -> !present.contains(entry.getKey()) || now >= entry.getValue().expiresAtMs());
      this.pending.entrySet().removeIf(entry -> {
         TurtleMasterManager.PendingEffects effects = entry.getValue();
         long lastSeen = Math.max(effects.resistanceSeenMs, effects.slownessSeenMs);
         return now - lastSeen > 1500L;
      });
   }

   public void resetAll() {
      this.active.clear();
      this.pending.clear();
      this.trackedPots.clear();
   }

   public boolean hasTurtleMaster(Player player) {
      return player != null && this.hasTurtleMaster(player.getUUID());
   }

   public boolean hasTurtleMaster(UUID uuid) {
      TurtleMasterManager.TurtleData data = uuid == null ? null : this.active.get(uuid);
      return data != null && System.currentTimeMillis() < data.expiresAtMs();
   }

   public int getRemainingSeconds(UUID uuid) {
      TurtleMasterManager.TurtleData data = uuid == null ? null : this.active.get(uuid);
      if (data == null) {
         return 0;
      } else if (data.expiresAtMs() == Long.MAX_VALUE) {
         return Integer.MAX_VALUE;
      } else {
         long remainingMs = data.expiresAtMs() - System.currentTimeMillis();
         return Math.max(0, (int)Math.ceil(remainingMs / 1000.0));
      }
   }

   public long getExpiryMs(UUID uuid) {
      TurtleMasterManager.TurtleData data = uuid == null ? null : this.active.get(uuid);
      return data == null ? 0L : data.expiresAtMs();
   }

   public int getResistanceAmplifier(UUID uuid) {
      TurtleMasterManager.TurtleData data = uuid == null ? null : this.active.get(uuid);
      return data == null ? -1 : data.resistanceAmplifier();
   }

   public int getSlownessAmplifier(UUID uuid) {
      TurtleMasterManager.TurtleData data = uuid == null ? null : this.active.get(uuid);
      return data == null ? -1 : data.slownessAmplifier();
   }

   private static class PendingEffects {
      private long resistanceSeenMs;
      private long slownessSeenMs;
      private int resistanceDuration;
      private int slownessDuration;
      private int resistanceAmplifier;
      private int slownessAmplifier;

      private PendingEffects() {
      }

      private boolean hasPair(long now) {
         return this.resistanceSeenMs > 0L && this.slownessSeenMs > 0L && now - this.resistanceSeenMs <= 1500L && now - this.slownessSeenMs <= 1500L;
      }
   }

   private record TrackedPot(Vec3 pos, int resistanceDuration, int resistanceAmplifier, int slownessDuration, int slownessAmplifier) {
   }

   private record TurtleData(int resistanceAmplifier, int slownessAmplifier, long expiresAtMs) {
   }
}
