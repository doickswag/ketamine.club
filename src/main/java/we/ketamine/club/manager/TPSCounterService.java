package we.ketamine.club.manager;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;

public class TPSCounterService extends Feature {
   private final float[] tickRates = new float[20];
   private int nextIndex = 0;
   private int countTick = 0;
   private long lastTimeUpdate = -1L;

   public TPSCounterService() {
   }

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.nextIndex = 0;
      this.countTick = 0;
      this.lastTimeUpdate = -1L;
   }

   @Subscribe
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundSetTimePacket) {
         long now = System.currentTimeMillis();
         if (this.lastTimeUpdate != -1L) {
            float elapsed = (float)(now - this.lastTimeUpdate) / 1000.0F;
            float tps = 20.0F / elapsed;
            this.tickRates[this.nextIndex % this.tickRates.length] = Math.min(Math.max(tps, 0.0F), 20.0F);
            this.nextIndex++;
            this.countTick = Math.min(this.countTick + 1, this.tickRates.length);
         }

         this.lastTimeUpdate = now;
      }
   }

   public float getAverageTPS() {
      if (this.countTick == 0) {
         return 20.0F;
      } else {
         float sum = 0.0F;
         int valid = 0;

         for (int i = 0; i < this.countTick; i++) {
            float t = this.tickRates[i];
            if (t > 0.0F) {
               sum += t;
               valid++;
            }
         }

         return valid == 0 ? 20.0F : Math.min(Math.max(sum / valid, 0.0F), 20.0F);
      }
   }

   public float getLatestTPS() {
      if (this.countTick == 0) {
         return 20.0F;
      } else {
         int last = (this.nextIndex - 1 + this.tickRates.length) % this.tickRates.length;
         return Math.min(Math.max(this.tickRates[last], 0.0F), 20.0F);
      }
   }

   public float getMinTPS() {
      if (this.countTick == 0) {
         return 20.0F;
      } else {
         float min = 20.0F;

         for (int i = 0; i < this.countTick; i++) {
            float t = this.tickRates[i];
            if (t > 0.0F && t < min) {
               min = t;
            }
         }

         return Math.min(Math.max(min, 0.0F), 20.0F);
      }
   }
}
