package we.ketamine.club.manager;

import java.util.function.Supplier;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.client.RotationsModule;

public class RotationRequest {
   public final String id;
   public final int priority;
   public final RotationRequest.Mode mode;
   public final boolean sticky;
   public final boolean noJitter;
   public final boolean renderRotation;
   private final boolean dynamic;
   private final Supplier<Float> yawSupplier;
   private final Supplier<Float> pitchSupplier;
   public float targetYaw;
   public float targetPitch;

   public RotationRequest(String id, int priority, float yaw, float pitch, RotationRequest.Mode mode) {
      this(id, priority, yaw, pitch, mode, false);
   }

   public RotationRequest(String id, int priority, float yaw, float pitch, RotationRequest.Mode mode, boolean sticky) {
      this(id, priority, yaw, pitch, mode, sticky, false);
   }

   public RotationRequest(String id, int priority, float yaw, float pitch, RotationRequest.Mode mode, boolean sticky, boolean noJitter) {
      this(id, priority, yaw, pitch, mode, sticky, noJitter, true);
   }

   public RotationRequest(String id, int priority, float yaw, float pitch, RotationRequest.Mode mode, boolean sticky, boolean noJitter, boolean renderRotation) {
      this.id = id;
      this.priority = priority;
      this.mode = mode;
      this.sticky = sticky;
      this.noJitter = noJitter;
      this.renderRotation = renderRotation;
      this.dynamic = false;
      this.targetYaw = yaw;
      this.targetPitch = pitch;
      this.yawSupplier = null;
      this.pitchSupplier = null;
   }

   public RotationRequest(String id, int priority, float yaw, float pitch) {
      this(id, priority, yaw, pitch, defaultMode());
   }

   public RotationRequest(String id, int priority, Supplier<Float> yawSupplier, Supplier<Float> pitchSupplier) {
      this(id, priority, yawSupplier, pitchSupplier, defaultMode());
   }

   public RotationRequest(String id, int priority, Supplier<Float> yawSupplier, Supplier<Float> pitchSupplier, RotationRequest.Mode mode) {
      this.id = id;
      this.priority = priority;
      this.mode = mode;
      this.sticky = false;
      this.noJitter = false;
      this.renderRotation = true;
      this.dynamic = true;
      this.yawSupplier = yawSupplier;
      this.pitchSupplier = pitchSupplier;
      this.updateTarget();
   }

   public boolean shouldUpdate() {
      return this.dynamic;
   }

   public void updateTarget() {
      if (this.dynamic && this.yawSupplier != null && this.pitchSupplier != null) {
         this.targetYaw = this.yawSupplier.get();
         this.targetPitch = this.pitchSupplier.get();
      }
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof RotationRequest other && this.id.equals(other.id);
   }

   @Override
   public int hashCode() {
      return this.id.hashCode();
   }

   private static RotationRequest.Mode defaultMode() {
      if (KetamineClub.moduleManager == null) {
         return RotationRequest.Mode.MOTION;
      } else {
         RotationsModule rotations = KetamineClub.moduleManager.getModuleByClass(RotationsModule.class);
         return rotations == null ? RotationRequest.Mode.MOTION : rotations.rotation.getValue();
      }
   }

   public static enum Mode {
      MOTION,
      SILENT;

      private Mode() {
      }
   }
}
