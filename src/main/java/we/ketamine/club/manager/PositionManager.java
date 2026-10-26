package we.ketamine.club.manager;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import we.ketamine.club.event.Stage;
import we.ketamine.club.event.impl.entity.player.UpdateWalkingPlayerEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;

public class PositionManager extends Feature {
   private double x;
   private double y;
   private double z;
   private boolean onground;
   private double fallDistance;

   public PositionManager() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   private void onDisconnect(DisconnectEvent event) {
      if (mc.isSameThread()) {
         this.resetSessionState();
      } else {
         mc.execute(this::resetSessionState);
      }
   }

   public void resetSessionState() {
      this.x = 0.0;
      this.y = 0.0;
      this.z = 0.0;
      this.onground = false;
      this.fallDistance = 0.0;
   }

   @Subscribe
   public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (event.getStage() != Stage.POST) {
         double diff = mc.player.yo - mc.player.getY();
         if (!mc.player.onGround() && !(diff <= 0.0)) {
            this.fallDistance += diff;
         } else {
            this.fallDistance = 0.0;
         }
      }
   }

   public void updatePosition() {
      this.x = mc.player.getX();
      this.y = mc.player.getY();
      this.z = mc.player.getZ();
      this.onground = mc.player.onGround();
   }

   public void restorePosition() {
      mc.player.setPos(this.x, this.y, this.z);
      mc.player.setOnGround(this.onground);
   }

   public void setPlayerPosition(double x, double y, double z) {
      mc.player.setPos(x, y, z);
   }

   public void setPlayerPosition(double x, double y, double z, boolean onground) {
      mc.player.setPos(x, y, z);
      mc.player.setOnGround(onground);
   }

   public void setPositionPacket(double x, double y, double z, boolean onGround, boolean setPos, boolean noLagBack) {
      boolean bl = mc.player.horizontalCollision;
      mc.player.connection.send(new Pos(x, y, z, onGround, bl));
      if (setPos) {
         mc.player.setPos(x, y, z);
         if (noLagBack) {
            this.updatePosition();
         }
      }
   }

   public double getX() {
      return this.x;
   }

   public void setX(double x) {
      this.x = x;
   }

   public double getY() {
      return this.y;
   }

   public void setY(double y) {
      this.y = y;
   }

   public double getZ() {
      return this.z;
   }

   public void setZ(double z) {
      this.z = z;
   }

   public double getFallDistance() {
      return this.fallDistance;
   }
}
