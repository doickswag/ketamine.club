package we.ketamine.club.features.modules.combat;

import io.netty.channel.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.mixin.network.ConnectionAccessor;
import we.ketamine.club.pingbypass.PingBypassInput;

public class AutoLogModule extends Module {
   private final Setting<Boolean> logOnHealth = this.bool("LogOnHealth", true);
   private final Setting<Integer> healthThreshold = this.num("HealthThreshold", 6, 1, 36);
   private final Setting<Boolean> logOnTotems = this.bool("LogOnTotems", true);
   private final Setting<Integer> totemThreshold = this.num("TotemThreshold", 1, 0, 64);
   private final Setting<Bind> disconnectBind = this.key("DisconnectBind", Bind.none());
   private int cachedTotems = 0;
   private volatile boolean awaitingRechamber = false;
   private List<String> nearbyPlayerNames = new ArrayList<>();

   public AutoLogModule() {
      super("AutoLog", "Automatically disconnects based on health or totem count.", Module.Category.COMBAT);
      this.healthThreshold.setVisibility(v -> this.logOnHealth.getValue());
      this.totemThreshold.setVisibility(v -> this.logOnTotems.getValue());
   }

   @Override
   public void onEnable() {
      this.awaitingRechamber = false;
      if (!nullCheck()) {
         this.updateTotemCount();
      }
   }

   @Override
   public void onDisable() {
      this.awaitingRechamber = false;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (!PingBypassInput.isScreenOpen()) {
            if (mc.player.isDeadOrDying()) {
               this.disable();
            } else {
               this.updateTotemCount();
               this.nearbyPlayerNames = mc.level.players().stream().filter(p -> p != mc.player).map(p -> p.getGameProfile().name()).toList();
               if (!this.awaitingRechamber && this.isSurvival()) {
                  this.checkThresholds();
               }
            }
         }
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!nullCheck() && this.isSurvival()) {
         if (event.getPacket() instanceof ClientboundEntityEventPacket pkt) {
            if (pkt.getEventId() == 35 && pkt.getEntity(mc.level) == mc.player) {
               this.cachedTotems--;
               if (!this.logOnTotems.getValue() || this.cachedTotems > this.totemThreshold.getValue()) {
                  return;
               }

               if (this.awaitingRechamber) {
                  this.awaitingRechamber = false;
                  mc.execute(this::disconnect);
                  return;
               }

               boolean pollOffhand = this.totemThreshold.getValue() > 1 && this.isOffhandModuleEnabled();
               Channel channel = pollOffhand ? this.getChannel() : null;
               if (channel == null) {
                  this.disconnect();
                  return;
               }

               this.awaitingRechamber = true;
               this.pollForRechamber(channel, System.currentTimeMillis() + 500L);
            }
         } else if (event.getPacket() instanceof ClientboundSetHealthPacket pktx) {
            if (pktx.getHealth() <= 0.0F) {
               this.disable();
               return;
            }

            if (this.logOnHealth.getValue() && pktx.getHealth() <= this.healthThreshold.getValue().intValue()) {
               this.disconnect();
            }
         }
      }
   }

   @Subscribe
   private void onKeyInput(KeyInputEvent event) {
      if (!this.disconnectBind.getValue().isEmpty() && event.getAction() == 1) {
         if (event.getKey() == this.disconnectBind.getValue().getKey() && !nullCheck() && this.isSurvival()) {
            this.disconnect();
         }
      }
   }

   private void pollForRechamber(Channel channel, long deadline) {
      if (!channel.isOpen()) {
         this.awaitingRechamber = false;
      } else {
         channel.eventLoop().schedule(() -> mc.execute(() -> {
            if (this.awaitingRechamber && !nullCheck() && this.isSurvival()) {
               this.updateTotemCount();
               boolean offhandHasTotem = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
               boolean noTotems = this.cachedTotems == 0;
               boolean timedOut = System.currentTimeMillis() >= deadline;
               if (!offhandHasTotem && !noTotems && !timedOut) {
                  this.pollForRechamber(channel, deadline);
               } else {
                  this.awaitingRechamber = false;
                  if (this.logOnTotems.getValue() && this.cachedTotems <= this.totemThreshold.getValue()) {
                     this.disconnect();
                  }
               }
            } else {
               this.awaitingRechamber = false;
            }
         }), 10L, TimeUnit.MILLISECONDS);
      }
   }

   private void checkThresholds() {
      if (this.logOnHealth.getValue()) {
         float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
         if (hp <= this.healthThreshold.getValue().intValue()) {
            this.disconnect();
            return;
         }
      }

      if (this.logOnTotems.getValue() && this.cachedTotems <= this.totemThreshold.getValue()) {
         this.disconnect();
      }
   }

   private void updateTotemCount() {
      int count = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (stack.is(Items.TOTEM_OF_UNDYING)) {
            count += stack.getCount();
         }
      }

      ItemStack offhand = mc.player.getOffhandItem();
      if (offhand.is(Items.TOTEM_OF_UNDYING)) {
         count += offhand.getCount();
      }

      this.cachedTotems = count;
   }

   private Channel getChannel() {
      return mc.getConnection() == null ? null : ((ConnectionAccessor)mc.getConnection().getConnection()).getChannel();
   }

   private void closeChannel() {
      Channel channel = this.getChannel();
      if (channel != null) {
         channel.close();
      }
   }

   private boolean isOffhandModuleEnabled() {
      OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
      return offhand != null && offhand.isEnabled();
   }

   private boolean isSurvival() {
      return mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
   }

   private void disconnect() {
      if (!nullCheck() && mc.getConnection() != null) {
         BlockPos pos = mc.player.blockPosition();
         List<String> nearby = new ArrayList<>(this.nearbyPlayerNames);
         StringBuilder msg = new StringBuilder("AutoLog Disconnect");
         msg.append("\nTotems: ").append(this.cachedTotems);
         msg.append("\nCoords: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
         msg.append(nearby.isEmpty() ? "\nNearby: none" : "\nNearby: " + String.join(", ", nearby));
         mc.getConnection().getConnection().disconnect(Component.literal(msg.toString()));
      } else {
         this.closeChannel();
      }

      this.disable();
   }

   @Override
   public String getDisplayInfo() {
      return nullCheck() ? null : String.valueOf(this.cachedTotems);
   }
}
