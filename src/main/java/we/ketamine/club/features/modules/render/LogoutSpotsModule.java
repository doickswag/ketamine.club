package we.ketamine.club.features.modules.render;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.WireframeEntityRenderer;

public class LogoutSpotsModule extends Module {
   public Setting<Color> spotColor = this.color("SpotColor", 255, 100, 100, 255);
   public Setting<Color> sideColor = this.color("SideColor", 255, 100, 100, 55);
   public Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.5F, 5.0F);
   public Setting<Boolean> showTime = this.bool("ShowTime", true);
   public Setting<Boolean> showTurtle = this.bool("ShowTurtle", true);
   public Setting<Color> nameColor = this.color("NameColor", 255, 100, 100, 255);
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private final Map<UUID, LogoutSpotsModule.SpotData> playerCache = new ConcurrentHashMap<>();
   private final Map<UUID, LogoutSpotsModule.LogoutSpot> loggedPlayers = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> ticksOnPlayerList = new ConcurrentHashMap<>();
   private ResourceKey<Level> lastDimension = null;
   private ClientLevel lastLevel = null;

   public LogoutSpotsModule() {
      super("LogoutSpots", "Shows where players logged out with a wireframe avatar", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   @Override
   public void onTick() {
      if (!nullCheck() && mc.getConnection() != null) {
         ResourceKey<Level> dim = mc.level.dimension();
         if (mc.level != this.lastLevel || this.lastDimension != null && !dim.equals(this.lastDimension)) {
            this.playerCache.clear();
            this.ticksOnPlayerList.clear();
         }

         this.lastLevel = mc.level;
         this.lastDimension = dim;

         for (Player player : mc.level.players()) {
            if (player != mc.player) {
               this.playerCache.put(player.getUUID(), new LogoutSpotsModule.SpotData(player, dim));
            }
         }

         this.loggedPlayers.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            if (mc.getConnection().getPlayerInfo(uuid) != null) {
               int n = this.ticksOnPlayerList.getOrDefault(uuid, 0);
               this.ticksOnPlayerList.put(uuid, n + 1);
               if (n > 1) {
                  this.ticksOnPlayerList.remove(uuid);
                  return true;
               }
            } else {
               this.ticksOnPlayerList.remove(uuid);
            }

            return false;
         });
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket pkt) {
         for (UUID id : pkt.profileIds()) {
            if (!this.loggedPlayers.containsKey(id)) {
               LogoutSpotsModule.SpotData data = this.playerCache.get(id);
               if (data != null) {
                  this.loggedPlayers.put(id, new LogoutSpotsModule.LogoutSpot(data));
               }
            }
         }
      } else if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket pkt && pkt.actions().contains(Action.ADD_PLAYER)) {
         for (Entry e : pkt.entries()) {
            this.loggedPlayers.remove(e.profileId());
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return this.loggedPlayers.isEmpty() ? null : String.valueOf(this.loggedPlayers.size());
      } else {
         ResourceKey<Level> dim = mc.level.dimension();
         long n = this.loggedPlayers.values().stream().filter(s -> dim.equals(s.dimension)).count();
         return n == 0L ? null : String.valueOf(n);
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         ResourceKey<Level> dim = mc.level.dimension();
         float delta = event.getDelta();

         for (LogoutSpotsModule.LogoutSpot spot : this.loggedPlayers.values()) {
            if (dim.equals(spot.dimension)) {
               spot.capturedGeometry = WireframeEntityRenderer.render(
                  event.getMatrix(),
                  spot.entity,
                  spot.pos,
                  spot.capturedGeometry,
                  delta,
                  this.sideColor.getValue(),
                  this.spotColor.getValue(),
                  this.lineWidth.getValue()
               );
            }
         }
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (MatrixCapture.projection != null) {
            NametagsModule nametags = KetamineClub.moduleManager.getModuleByClass(NametagsModule.class);
            GuiGraphicsExtractor graphics = event.getContext();
            ResourceKey<Level> dim = mc.level.dimension();

            for (LogoutSpotsModule.LogoutSpot spot : this.loggedPlayers.values()) {
               if (dim.equals(spot.dimension)) {
                  double tagY = spot.pos.y + 1.8 + nametags.gap.getValue().floatValue() * 0.5;
                  double dist = mc.player.position().distanceTo(spot.pos);
                  String timeStr = this.showTime.getValue() ? " " + this.formatElapsed(spot.logoutTime) : "";
                  nametags.renderNametag(
                     graphics,
                     spot.pos.x,
                     tagY,
                     spot.pos.z,
                     dist,
                     spot.name,
                     this.nameColor.getValue().getRGB(),
                     timeStr,
                     spot.totemPops,
                     this.turtleSecondsLeft(spot),
                     spot.armor,
                     spot.mainHand,
                     spot.offHand
                  );
               }
            }
         }
      }
   }

   @Subscribe
   public void onDisconnect(DisconnectEvent event) {
      this.resetState();
   }

   private void resetState() {
      this.loggedPlayers.clear();
      this.playerCache.clear();
      this.ticksOnPlayerList.clear();
      this.lastDimension = null;
      this.lastLevel = null;
   }

   private int turtleSecondsLeft(LogoutSpotsModule.LogoutSpot spot) {
      if (!this.showTurtle.getValue() || spot.turtleExpiresAtMs <= 0L) {
         return -1;
      } else if (spot.turtleExpiresAtMs == Long.MAX_VALUE) {
         return Integer.MAX_VALUE;
      } else {
         long remainingMs = spot.turtleExpiresAtMs - System.currentTimeMillis();
         return remainingMs <= 0L ? -1 : (int)Math.ceil(remainingMs / 1000.0);
      }
   }

   private String formatElapsed(long logoutTime) {
      long secs = (System.currentTimeMillis() - logoutTime) / 1000L;
      return String.format("%02d:%02d", secs / 60L, secs % 60L);
   }

   public static class LogoutSpot {
      public final String name;
      public final UUID uuid;
      public final Vec3 pos;
      public final Player entity;
      public final Map<EquipmentSlot, ItemStack> armor;
      public final ItemStack mainHand;
      public final ItemStack offHand;
      public final int totemPops;
      public final long turtleExpiresAtMs;
      public final long logoutTime;
      public final ResourceKey<Level> dimension;
      public List<float[][]> capturedGeometry = null;

      LogoutSpot(LogoutSpotsModule.SpotData data) {
         this.turtleExpiresAtMs = data.turtleExpiresAtMs;
         this.name = data.name;
         this.uuid = data.uuid;
         this.pos = data.pos;
         this.entity = data.entity;
         this.armor = data.armor;
         this.mainHand = data.mainHand;
         this.offHand = data.offHand;
         this.totemPops = data.totemPops;
         this.dimension = data.dimension;
         this.logoutTime = System.currentTimeMillis();
      }
   }

   private static class SpotData {
      final String name;
      final UUID uuid;
      final Vec3 pos;
      final Player entity;
      final Map<EquipmentSlot, ItemStack> armor;
      final ItemStack mainHand;
      final ItemStack offHand;
      final int totemPops;
      final long turtleExpiresAtMs;
      final ResourceKey<Level> dimension;

      SpotData(Player player, ResourceKey<Level> dimension) {
         this.name = player.getGameProfile().name();
         this.uuid = player.getUUID();
         this.pos = player.position();
         this.entity = player;
         this.dimension = dimension;
         this.totemPops = KetamineClub.playerInfoManager.getTotemPops(player.getUUID());
         this.turtleExpiresAtMs = KetamineClub.turtleMasterManager.getExpiryMs(player.getUUID());
         this.mainHand = player.getMainHandItem().copy();
         this.offHand = player.getOffhandItem().copy();
         this.armor = new EnumMap<>(EquipmentSlot.class);

         for (EquipmentSlot slot : LogoutSpotsModule.ARMOR_SLOTS) {
            this.armor.put(slot, player.getItemBySlot(slot).copy());
         }
      }
   }
}
