package we.ketamine.club.features.modules.client;

import java.util.Locale;
import java.util.Set;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class TeleportLoggerModule extends Module {
   private static final double MOVEMENT_EPSILON = 1.0E-4;
   private static final float ROTATION_EPSILON = 0.01F;
   private final Setting<Boolean> tickState = this.bool("TickState", true).setPage("State");
   private final Setting<Integer> tickInterval = this.tickInterval();
   private final Setting<Boolean> onlyWhenMoving = this.onlyWhenMoving();
   private final Setting<Boolean> logMovePackets = this.bool("MovePackets", false).setPage("Packets");
   private final Setting<Boolean> logCorrections = this.bool("Corrections", true).setPage("Packets");
   private final Setting<Boolean> logVelocityPackets = this.bool("VelocityPackets", false).setPage("Packets");
   private double lastX = Double.NaN;
   private double lastY = Double.NaN;
   private double lastZ = Double.NaN;
   private double sentX = Double.NaN;
   private double sentY = Double.NaN;
   private double sentZ = Double.NaN;
   private int ticks;

   public TeleportLoggerModule() {
      super("TeleportLogger", "Logs movement prediction, move packets, and server setbacks/teleports", Module.Category.CLIENT);
   }

   private Setting<Integer> tickInterval() {
      Setting<Integer> s = this.num("TickInterval", 1, 1, 20).setPage("State");
      s.setVisibility(v -> this.tickState.getValue());
      return s;
   }

   private Setting<Boolean> onlyWhenMoving() {
      Setting<Boolean> s = this.bool("OnlyWhenMoving", true).setPage("State");
      s.setVisibility(v -> this.tickState.getValue());
      return s;
   }

   @Override
   public void onEnable() {
      this.ticks = 0;
      this.syncLastPos();
      this.sentX = this.sentY = this.sentZ = Double.NaN;
   }

   @Override
   public void onDisable() {
      this.lastX = this.lastY = this.lastZ = Double.NaN;
      this.sentX = this.sentY = this.sentZ = Double.NaN;
      this.ticks = 0;
   }

   @Override
   public void onTick() {
      if (mc.player != null && mc.level != null) {
         double x = mc.player.getX();
         double y = mc.player.getY();
         double z = mc.player.getZ();
         double dx = Double.isNaN(this.lastX) ? 0.0 : x - this.lastX;
         double dy = Double.isNaN(this.lastY) ? 0.0 : y - this.lastY;
         double dz = Double.isNaN(this.lastZ) ? 0.0 : z - this.lastZ;
         this.lastX = x;
         this.lastY = y;
         this.lastZ = z;
         if (this.tickState.getValue()) {
            this.ticks++;
            if (this.ticks % this.tickInterval.getValue() == 0) {
               Vec3 vel = mc.player.getDeltaMovement();
               Vec2 move = mc.player.input.getMoveVector();
               float forward = move.y;
               float sideways = move.x;
               double pdx = Double.isNaN(this.sentX) ? 0.0 : x - this.sentX;
               double pdy = Double.isNaN(this.sentY) ? 0.0 : y - this.sentY;
               double pdz = Double.isNaN(this.sentZ) ? 0.0 : z - this.sentZ;
               float serverYaw = KetamineClub.rotationManager.getServerYaw();
               float serverPitch = KetamineClub.rotationManager.getServerPitch();
               float yawDesync = Mth.wrapDegrees(serverYaw - mc.player.getYRot());
               float pitchDesync = serverPitch - mc.player.getXRot();
               boolean active = Math.abs(forward) > 1.0E-4
                  || Math.abs(sideways) > 1.0E-4
                  || Math.abs(dx) > 1.0E-4
                  || Math.abs(dy) > 1.0E-4
                  || Math.abs(dz) > 1.0E-4
                  || Math.abs(vel.x) > 1.0E-4
                  || Math.abs(vel.y) > 1.0E-4
                  || Math.abs(vel.z) > 1.0E-4
                  || Math.abs(pdx) > 1.0E-4
                  || Math.abs(pdy) > 1.0E-4
                  || Math.abs(pdz) > 1.0E-4
                  || Math.abs(yawDesync) > 0.01F
                  || Math.abs(pitchDesync) > 0.01F
                  || mc.player.isFallFlying();
               if (!this.onlyWhenMoving.getValue() || active) {
                  this.info(
                     String.format(
                        Locale.ROOT,
                        "tick pos=%s d=%s vel=%s input=(%.2f,%.2f,j=%s,s=%s) ground=%s fly=%s pkt-d=%s rot c=(%.2f,%.2f) s=(%.2f,%.2f) rot-d=(%.2f,%.2f)",
                        this.fmt(x, y, z),
                        this.fmt(dx, dy, dz),
                        this.fmt(vel.x, vel.y, vel.z),
                        forward,
                        sideways,
                        mc.options.keyJump.isDown(),
                        mc.options.keyShift.isDown(),
                        mc.player.onGround(),
                        mc.player.isFallFlying(),
                        this.fmt(pdx, pdy, pdz),
                        mc.player.getYRot(),
                        mc.player.getXRot(),
                        serverYaw,
                        serverPitch,
                        yawDesync,
                        pitchDesync
                     )
                  );
               }
            }
         }
      } else {
         this.lastX = this.lastY = this.lastZ = Double.NaN;
         this.ticks = 0;
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (mc.player != null) {
         if (event.getPacket() instanceof ServerboundMovePlayerPacket move) {
            if (move.hasPosition()) {
               this.sentX = move.getX(mc.player.getX());
               this.sentY = move.getY(mc.player.getY());
               this.sentZ = move.getZ(mc.player.getZ());
            }

            if (this.logMovePackets.getValue()) {
               this.info(
                  String.format(
                     Locale.ROOT,
                     "c2s move[%s] pos=%s rot=(%.2f,%.2f) ground=%s",
                     this.movePacketType(move),
                     this.fmt(move.getX(mc.player.getX()), move.getY(mc.player.getY()), move.getZ(mc.player.getZ())),
                     move.getYRot(mc.player.getYRot()),
                     move.getXRot(mc.player.getXRot()),
                     move.isOnGround()
                  )
               );
            }
         }
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (mc.player != null) {
         if (this.logCorrections.getValue() && event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
            Set<Relative> relatives = packet.relatives();
            PositionMoveRotation change = packet.change();
            Vec3 pos = change.position();
            double tx = relatives.contains(Relative.X) ? mc.player.getX() + pos.x : pos.x;
            double ty = relatives.contains(Relative.Y) ? mc.player.getY() + pos.y : pos.y;
            double tz = relatives.contains(Relative.Z) ? mc.player.getZ() + pos.z : pos.z;
            float tyaw = relatives.contains(Relative.Y_ROT) ? mc.player.getYRot() + change.yRot() : change.yRot();
            float tpitch = relatives.contains(Relative.X_ROT) ? mc.player.getXRot() + change.xRot() : change.xRot();
            this.info(
               String.format(
                  Locale.ROOT,
                  "s2c correction id=%d flags=%s target=%s delta=%s rot=(%.2f,%.2f) rot-d=(%.2f,%.2f)",
                  packet.id(),
                  relatives,
                  this.fmt(tx, ty, tz),
                  this.fmt(tx - mc.player.getX(), ty - mc.player.getY(), tz - mc.player.getZ()),
                  tyaw,
                  tpitch,
                  Mth.wrapDegrees(tyaw - mc.player.getYRot()),
                  tpitch - mc.player.getXRot()
               )
            );
         }

         if (this.logVelocityPackets.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
            Vec3 v = packet.movement();
            this.info(String.format(Locale.ROOT, "s2c velocity vel=%s", this.fmt(v.x, v.y, v.z)));
         }
      }
   }

   private void syncLastPos() {
      if (mc.player == null) {
         this.lastX = this.lastY = this.lastZ = Double.NaN;
      } else {
         this.lastX = mc.player.getX();
         this.lastY = mc.player.getY();
         this.lastZ = mc.player.getZ();
      }
   }

   private String movePacketType(ServerboundMovePlayerPacket packet) {
      if (packet.hasPosition() && packet.hasRotation()) {
         return "full";
      } else if (packet.hasPosition()) {
         return "pos";
      } else {
         return packet.hasRotation() ? "look" : "ground";
      }
   }

   private String fmt(double x, double y, double z) {
      return String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", x, y, z);
   }

   private void info(String message) {
      KetamineClub.LOGGER.info("[TeleportLogger] {}", message);
   }
}
