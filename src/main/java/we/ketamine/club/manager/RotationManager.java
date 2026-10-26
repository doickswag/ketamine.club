package we.ketamine.club.manager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.client.RotationsModule;
import we.ketamine.club.util.traits.Util;

public class RotationManager {
   private float realYaw;
   private float realPitch;
   private float rotationYaw;
   private float rotationPitch;
   private float rotationYaw0;
   private float rotationPitch0;
   private float serverYaw;
   private float serverPitch;
   private float serverYaw0;
   private float serverDeltaYaw;
   private boolean bypassUseSpoof;
   private boolean silentSyncRequired;
   private int silentSentPriority;
   private long lastSilentMs;
   private final List<RotationRequest> requests = new ArrayList<>();
   private String lastActiveId;
   private float currentYawSpeed;
   private float currentPitchSpeed;
   private int ticksHolding;
   private boolean returning;

   public RotationManager() {
   }

   public void init() {
      Util.EVENT_BUS.register(this);
   }

   @Subscribe
   private void onDisconnect(DisconnectEvent event) {
      if (Util.mc.isSameThread()) {
         this.resetSessionState();
      } else {
         Util.mc.execute(this::resetSessionState);
      }
   }

   public void resetSessionState() {
      this.realYaw = 0.0F;
      this.realPitch = 0.0F;
      this.rotationYaw = 0.0F;
      this.rotationPitch = 0.0F;
      this.rotationYaw0 = 0.0F;
      this.rotationPitch0 = 0.0F;
      this.serverYaw = 0.0F;
      this.serverPitch = 0.0F;
      this.serverYaw0 = 0.0F;
      this.serverDeltaYaw = 0.0F;
      this.bypassUseSpoof = false;
      this.silentSyncRequired = false;
      this.silentSentPriority = 0;
      this.lastSilentMs = 0L;
      this.requests.clear();
      this.lastActiveId = null;
      this.currentYawSpeed = 0.0F;
      this.currentPitchSpeed = 0.0F;
      this.ticksHolding = 0;
      this.returning = false;
   }

   public boolean submit(RotationRequest request) {
      if (request.mode == RotationRequest.Mode.SILENT) {
         if (Util.mc.player != null && Util.mc.getConnection() != null) {
            this.requests.removeIf(r -> r.id.equals(request.id));
            this.performSilent(request);
            this.silentSentPriority = this.silentSyncRequired ? Math.max(this.silentSentPriority, request.priority) : request.priority;
            this.silentSyncRequired = true;
            this.lastSilentMs = System.currentTimeMillis();
            return true;
         } else {
            return false;
         }
      } else {
         this.requests.removeIf(r -> r.id.equals(request.id));
         this.requests.add(request);
         this.requests.sort(Comparator.<RotationRequest>comparingInt(r -> r.priority).reversed());
         return true;
      }
   }

   public void cancel(String id) {
      this.requests.removeIf(r -> r.id.equals(id));
   }

   public boolean hasRequest(String id) {
      return this.requests.stream().anyMatch(r -> r.id.equals(id));
   }

   public boolean isCompleted(String id) {
      return this.requests
         .stream()
         .filter(r -> r.id.equals(id))
         .findFirst()
         .map(
            r -> Math.abs(yawDifference(r.targetYaw, this.rotationYaw)) <= this.rotationThreshold()
               && Math.abs(r.targetPitch - this.rotationPitch) <= this.rotationThreshold()
         )
         .orElse(false);
   }

   @Subscribe(
      priority = Integer.MIN_VALUE
   )
   private void onPreTick(PreTickEvent event) {
      if (Util.mc.player != null) {
         this.updateRealRotation(Util.mc.player.getYRot(), Util.mc.player.getXRot());
         this.rotationYaw0 = this.rotationYaw;
         this.rotationPitch0 = this.rotationPitch;
         RotationRequest active = this.getActiveRequest();
         if (active != null) {
            this.processRequest(active);
         } else if (this.returning) {
            this.returnToRealRotation();
         } else {
            this.idleReset();
         }
      }
   }

   private void processRequest(RotationRequest request) {
      if (!request.id.equals(this.lastActiveId)) {
         this.resetRotationToReal();
         this.lastActiveId = request.id;
      }

      boolean updated = false;
      if (request.shouldUpdate()) {
         float oldYaw = request.targetYaw;
         float oldPitch = request.targetPitch;
         request.updateTarget();
         updated = Math.abs(oldYaw - request.targetYaw) > 0.001F || Math.abs(oldPitch - request.targetPitch) > 0.001F;
         if (updated) {
            this.ticksHolding = 0;
         }
      }

      float yawDiff = yawDifference(request.targetYaw, this.rotationYaw);
      float pitchDiff = request.targetPitch - this.rotationPitch;
      boolean reached = Math.abs(yawDiff) <= this.rotationThreshold() && Math.abs(pitchDiff) <= this.rotationThreshold();
      if (!reached || updated) {
         this.ticksHolding = 0;
         this.interpolateRotation(yawDiff, pitchDiff, !request.noJitter);
      } else if (request.sticky) {
         this.ticksHolding = 0;
         this.interpolateRotation(yawDiff, pitchDiff, !request.noJitter);
      } else if (++this.ticksHolding >= this.holdTicks()) {
         this.removeActiveRequest();
         this.ticksHolding = 0;
         this.returning = true;
      }
   }

   private void resetRotationToReal() {
      float targetYaw = alignYaw(this.realYaw, this.rotationYaw);
      this.realYaw = targetYaw;
      Util.mc.player.setYRot(targetYaw);
      this.rotationYaw = this.realYaw;
      this.rotationPitch = this.realPitch;
      this.currentYawSpeed = 0.0F;
      this.currentPitchSpeed = 0.0F;
      this.ticksHolding = 0;
      this.returning = false;
   }

   private void returnToRealRotation() {
      float targetYaw = alignYaw(this.realYaw, this.rotationYaw);
      float yawDiff = targetYaw - this.rotationYaw;
      float pitchDiff = this.realPitch - this.rotationPitch;
      this.interpolateRotation(yawDiff, pitchDiff, true);
      boolean backReached = Math.abs(yawDiff) <= this.rotationThreshold() && Math.abs(pitchDiff) <= this.rotationThreshold();
      if (backReached) {
         this.returning = false;
         this.realYaw = targetYaw;
         Util.mc.player.setYRot(targetYaw);
         this.rotationYaw = this.realYaw;
         this.rotationPitch = this.realPitch;
         this.lastActiveId = null;
      }
   }

   private void interpolateRotation(float yawDiff, float pitchDiff, boolean jitter) {
      this.currentYawSpeed = lerp(this.currentYawSpeed, yawDiff, this.rotationEase());
      this.currentPitchSpeed = lerp(this.currentPitchSpeed, pitchDiff, this.rotationEase());
      float speed = this.rotationSpeed();
      float yawSpeed = Mth.clamp(this.currentYawSpeed, -speed, speed);
      float pitchSpeed = Mth.clamp(this.currentPitchSpeed, -speed, speed);
      float newYaw = this.rotationYaw + yawSpeed;
      float newPitch = this.rotationPitch + pitchSpeed;
      RotationManager.JitterMode jitterMode = this.jitterMode();
      if (jitter && jitterMode == RotationManager.JitterMode.NORMAL) {
         float minJitter = this.rotationThreshold() / 4.0F;
         float maxJitter = this.rotationThreshold() / 2.0F;
         float jitterYaw = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         float jitterPitch = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         jitterYaw *= Math.random() < 0.5 ? -1.0F : 1.0F;
         jitterPitch *= Math.random() < 0.5 ? -1.0F : 1.0F;
         newYaw += jitterYaw;
         newPitch = Mth.clamp(newPitch + jitterPitch, -90.0F, 90.0F);
      } else if (jitter && jitterMode == RotationManager.JitterMode.GRIM) {
         float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
         newPitch = Mth.clamp(newPitch + f, -90.0F, 90.0F);
      }

      this.rotationYaw = newYaw;
      this.rotationPitch = Mth.clamp(newPitch, -90.0F, 90.0F);
   }

   private void idleReset() {
      this.lastActiveId = null;
      this.rotationYaw = this.realYaw;
      this.rotationPitch = this.realPitch;
      this.currentYawSpeed = 0.0F;
      this.currentPitchSpeed = 0.0F;
   }

   public Input computeMoveFixInput(Input real, float realYawNow) {
      float delta = Mth.wrapDegrees(realYawNow - this.getMovementYaw());
      float inputX = (real.right() ? 1 : 0) - (real.left() ? 1 : 0);
      float inputZ = (real.forward() ? 1 : 0) - (real.backward() ? 1 : 0);
      if (inputX == 0.0F && inputZ == 0.0F) {
         return real;
      } else {
         double moveAngle = Math.toDegrees(Math.atan2(inputX, inputZ));
         double finalAngle = moveAngle + delta;
         int sector = (int)Math.round(finalAngle / 45.0) & 7;
         boolean f = false;
         boolean b = false;
         boolean l = false;
         boolean r = false;
         switch (sector) {
            case 0:
               f = true;
               break;
            case 1:
               f = true;
               r = true;
               break;
            case 2:
               r = true;
               break;
            case 3:
               b = true;
               r = true;
               break;
            case 4:
               b = true;
               break;
            case 5:
               b = true;
               l = true;
               break;
            case 6:
               l = true;
               break;
            case 7:
               f = true;
               l = true;
         }

         return new Input(f, b, l, r, real.jump(), real.shift(), real.sprint());
      }
   }

   private void performSilent(RotationRequest req) {
      float targetYaw = alignYaw(req.targetYaw, Util.mc.player.getYRot());
      float targetPitch = Mth.clamp(req.targetPitch, -90.0F, 90.0F);
      if (!req.noJitter && this.jitterMode() == RotationManager.JitterMode.NORMAL) {
         float minJitter = this.rotationThreshold() / 4.0F;
         float maxJitter = this.rotationThreshold() / 2.0F;
         float yawJitter = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         float pitchJitter = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         targetYaw += Math.random() < 0.5 ? -yawJitter : yawJitter;
         targetPitch += Math.random() < 0.5 ? -pitchJitter : pitchJitter;
         targetPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
      } else if (!req.noJitter && this.jitterMode() == RotationManager.JitterMode.GRIM) {
         float jitter = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
         targetPitch = Mth.clamp(targetPitch + jitter, -90.0F, 90.0F);
      }

      this.setServerDeltaYaw(targetYaw - this.serverYaw);
      this.setServerYaw(targetYaw);
      this.setServerPitch(targetPitch);
      Util.mc
         .getConnection()
         .send(
            new PosRot(
               Util.mc.player.getX(),
               Util.mc.player.getY(),
               Util.mc.player.getZ(),
               targetYaw,
               targetPitch,
               Util.mc.player.onGround(),
               Util.mc.player.horizontalCollision
            )
         );
   }

   private RotationRequest getActiveRequest() {
      return this.requests.isEmpty() ? null : this.requests.get(0);
   }

   private void removeActiveRequest() {
      if (!this.requests.isEmpty()) {
         this.requests.remove(0);
      }
   }

   private void updateRealRotation(float yaw, float pitch) {
      this.realYaw = yaw;
      this.realPitch = Mth.clamp(pitch, -90.0F, 90.0F);
   }

   public boolean isRotating() {
      return this.getActiveRequest() != null || this.returning;
   }

   public boolean shouldRenderRotation() {
      RotationRequest active = this.getActiveRequest();
      return this.renderRotations() && (this.returning || active != null && active.renderRotation);
   }

   public boolean hasMovementRotation() {
      return this.isRotating();
   }

   public float getMovementYaw() {
      return this.rotationYaw;
   }

   public float getMovementPitch() {
      return this.rotationPitch;
   }

   public boolean isActive(String id) {
      RotationRequest active = this.getActiveRequest();
      return active != null && active.id.equals(id);
   }

   public boolean isMoveFixEnabled() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null || rotations.moveFix.getValue();
   }

   public float getRealYaw() {
      return this.realYaw;
   }

   public float getRealPitch() {
      return this.realPitch;
   }

   public float getRotationYaw() {
      return this.rotationYaw;
   }

   public float getRotationPitch() {
      return this.rotationPitch;
   }

   public float getRenderYaw(float partialTicks) {
      return Mth.rotLerp(partialTicks, this.rotationYaw0, this.rotationYaw);
   }

   public float getRenderPitch(float partialTicks) {
      return this.rotationPitch0 + (this.rotationPitch - this.rotationPitch0) * partialTicks;
   }

   public float getServerYaw() {
      return this.serverYaw;
   }

   public float getServerPitch() {
      return this.serverPitch;
   }

   public float getServerYaw0() {
      return this.serverYaw0;
   }

   public float getServerDeltaYaw() {
      return this.serverDeltaYaw;
   }

   public void setServerYaw(float yaw) {
      this.serverYaw0 = this.serverYaw;
      this.serverYaw = yaw;
   }

   public void setServerPitch(float pitch) {
      this.serverPitch = pitch;
   }

   public void setServerDeltaYaw(float delta) {
      this.serverDeltaYaw = delta;
   }

   public boolean isBypassUseSpoof() {
      return this.bypassUseSpoof;
   }

   public void setBypassUseSpoof(boolean bypass) {
      this.bypassUseSpoof = bypass;
   }

   public boolean isSilentSyncRequired() {
      return this.silentSyncRequired;
   }

   public boolean isSilentSyncRequiredAtLeast(int priority) {
      return this.silentSyncRequired && this.silentSentPriority >= priority;
   }

   public void setSilentSyncRequired(boolean required) {
      this.silentSyncRequired = required;
      if (!required) {
         this.silentSentPriority = 0;
      }
   }

   public boolean isSilentActive() {
      return System.currentTimeMillis() - this.lastSilentMs < 500L;
   }

   public boolean isServerRotationReady(RotationRequest request) {
      return Math.abs(yawDifference(request.targetYaw, this.serverYaw)) <= this.rotationThreshold()
         && Math.abs(request.targetPitch - this.serverPitch) <= this.rotationThreshold();
   }

   private RotationsModule rotationsModule() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(RotationsModule.class);
   }

   private float rotationSpeed() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null ? 360.0F : rotations.speed.getValue();
   }

   private float rotationEase() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null ? 1.0F : rotations.ease.getValue();
   }

   private float rotationThreshold() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null ? 5.0F : rotations.threshold.getValue();
   }

   private int holdTicks() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null ? 0 : rotations.hold.getValue();
   }

   private RotationManager.JitterMode jitterMode() {
      RotationsModule rotations = this.rotationsModule();
      return rotations == null ? RotationManager.JitterMode.NORMAL : rotations.jitter.getValue();
   }

   private boolean renderRotations() {
      RotationsModule rotations = this.rotationsModule();
      return rotations != null && rotations.render.getValue();
   }

   private static float yawDifference(float target, float current) {
      return Mth.wrapDegrees(target - current);
   }

   private static float alignYaw(float base, float reference) {
      int wraps = Math.round((reference - base) / 360.0F);
      return base + wraps * 360.0F;
   }

   private static float lerp(float from, float to, float factor) {
      return from + (to - from) * factor;
   }

   public static enum JitterMode {
      NONE,
      GRIM,
      NORMAL;

      private JitterMode() {
      }
   }
}
