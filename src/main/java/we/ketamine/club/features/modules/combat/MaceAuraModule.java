package we.ketamine.club.features.modules.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class MaceAuraModule extends Module {
   private static final String ID = "MaceAura";
   private static final int ROTATION_PRIORITY = 120;
   private static final int SWAP_PRIORITY = 200;
   private final Setting<Double> range = this.num("Range", 3.0, 1.0, 6.0);
   private final Setting<Double> minFallDistance = this.num("MinFallDistance", 1.5, 0.0, 20.0);
   private final Setting<Boolean> grimVerticalHitbox = this.bool("GrimVerticalHitbox", false);
   private final Setting<Double> verticalExpansion = this.num("VerticalExpansion", 2.0, 0.0, 10.0).setVisibility(value -> this.grimVerticalHitbox.getValue());
   private Entity target;
   private boolean priorityClaimed;

   public MaceAuraModule() {
      super("MaceAura", "Automatically lands charged mace smash attacks on nearby targets.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.target = null;
      this.priorityClaimed = false;
   }

   @Override
   public void onDisable() {
      this.target = null;
      this.priorityClaimed = false;
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("MaceAura");
      }
   }

   public boolean isPriorityClaimed() {
      return this.priorityClaimed;
   }

   @Subscribe(
      priority = 100
   )
   private void onPreTick(PreTickEvent event) {
      this.target = null;
      this.priorityClaimed = false;
      if (!nullCheck() && mc.getConnection() != null && !mc.player.isDeadOrDying()) {
         if (MaceItem.canSmashAttack(mc.player) && !(mc.player.fallDistance < this.minFallDistance.getValue())) {
            if (!KetamineClub.swapManager.isBlockedByOther("MaceAura", 200)) {
               Result mace = InventoryUtil.find(Items.MACE, InventoryUtil.PLACE_SCOPE);
               if (mace.found()) {
                  Entity selected = this.findTarget();
                  if (selected != null) {
                     this.target = selected;
                     this.priorityClaimed = true;
                     Vec3 eye = mc.player.getEyePosition(1.0F);
                     Vec3 hit = closestPointToBox(eye, this.targetBox(selected));
                     float[] angles = MathUtil.calcAngle(eye, hit);
                     KetamineClub.rotationManager.submit(new RotationRequest("MaceAura", 120, angles[0], angles[1], RotationRequest.Mode.SILENT, false, true));
                     if (!(mc.player.getAttackStrengthScale(0.5F) < 1.0F)) {
                        KetamineClub.swapManager.submit(new SwapRequest("MaceAura", 200, mace, () -> this.attack(selected)));
                     }
                  }
               }
            }
         }
      }
   }

   private void attack(Entity attackTarget) {
      if (!nullCheck() && attackTarget.isAlive()) {
         mc.gameMode.attack(mc.player, attackTarget);
         mc.player.swing(InteractionHand.MAIN_HAND);
      }
   }

   private Entity findTarget() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      if (targets == null) {
         return null;
      } else {
         Vec3 eye = mc.player.getEyePosition(1.0F);
         Entity best = null;
         double bestDistance = Double.MAX_VALUE;
         double searchRadius = this.range.getValue() + 1.0;
         double verticalSearchRadius = searchRadius + this.enabledVerticalExpansion();
         AABB searchBox = mc.player.getBoundingBox().inflate(searchRadius, verticalSearchRadius, searchRadius);

         for (Entity entity : mc.level.getEntities(null, searchBox)) {
            if (entity != mc.player && targets.isValidTarget(entity)) {
               double distance = eye.distanceTo(closestPointToBox(eye, this.targetBox(entity)));
               if (!(distance > this.range.getValue()) && !(distance >= bestDistance)) {
                  best = entity;
                  bestDistance = distance;
               }
            }
         }

         return best;
      }
   }

   private AABB targetBox(Entity entity) {
      AABB box = entity.getBoundingBox();
      return entity instanceof Player && this.grimVerticalHitbox.getValue()
         ? new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY + this.verticalExpansion.getValue(), box.maxZ)
         : box;
   }

   private double enabledVerticalExpansion() {
      return this.grimVerticalHitbox.getValue() ? this.verticalExpansion.getValue() : 0.0;
   }

   private static Vec3 closestPointToBox(Vec3 point, AABB box) {
      if (box.contains(point)) {
         return box.getCenter();
      } else {
         double x = Math.max(box.minX, Math.min(point.x, box.maxX));
         double y = Math.max(box.minY, Math.min(point.y, box.maxY));
         double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
         double inset = 0.0625;
         double epsilon = 1.0E-9;
         if (Math.abs(x - box.minX) < 1.0E-9) {
            x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
         } else if (Math.abs(x - box.maxX) < 1.0E-9) {
            x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
         }

         if (Math.abs(y - box.minY) < 1.0E-9) {
            y = Math.min(box.minY + 0.0625, box.maxY - 1.0E-9);
         } else if (Math.abs(y - box.maxY) < 1.0E-9) {
            y = Math.max(box.maxY - 0.0625, box.minY + 1.0E-9);
         }

         if (Math.abs(z - box.minZ) < 1.0E-9) {
            z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
         } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
            z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
         }

         return new Vec3(x, y, z);
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.target == null ? null : this.target.getName().getString();
   }
}
