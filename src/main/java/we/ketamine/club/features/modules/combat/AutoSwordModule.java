package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.Stage;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.entity.player.UpdateWalkingPlayerEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.util.EnchantmentUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.render.RenderUtil;

public class AutoSwordModule extends Module {
   private static final double RANGE = 3.0;
   private static final int SWAP_PRIORITY = 66;
   private static final int MACE_SWAP_PRIORITY = 100;
   private final Setting<Double> delay = this.num("Delay", 0.92, 0.0, 1.0).setPage("General");
   private final Setting<Boolean> swing = this.bool("Swing", true).setPage("General");
   private final Setting<AutoSwordModule.TpsMode> tpsMode = this.mode("TPS", AutoSwordModule.TpsMode.LATEST).setPage("General");
   private final Setting<Boolean> criticals = this.bool("Criticals", false).setPage("General");
   private final Setting<Boolean> critsWithSword = this.bool("CritsWithSword", true).setVisibility(v -> this.criticals.getValue()).setPage("General");
   private final Setting<Boolean> strict = this.bool("Strict", true).setVisibility(v -> this.criticals.getValue()).setPage("General");
   private final Setting<Boolean> macePriority = this.bool("MacePriority", true).setPage("Mace");
   private final Setting<Boolean> onlySmash = this.bool("OnlySmash", true).setVisibility(v -> this.macePriority.getValue()).setPage("Mace");
   private final Setting<Float> minSmashDist = this.num("MinSmashDist", 3.0F, 1.5F, 30.0F).setVisibility(v -> this.macePriority.getValue()).setPage("Mace");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.2F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> sideColor = this.color("SideColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> lineColor = this.color("LineColor", 130, 80, 255, 255).setPage("Render");
   private Entity currentTarget = null;
   private float attackCooldownTicks = 0.0F;
   private AABB fadeBox = null;
   private long lastTargetTime = 0L;
   private Entity pendingTarget = null;
   private ItemStack pendingWeapon = ItemStack.EMPTY;
   private int pendingWeaponSlot = -1;
   private float pendingTps;
   private float pendingYaw;
   private float pendingPitch;
   private boolean pendingCrit;

   public AutoSwordModule() {
      super("AutoSword", "Automatically attacks nearby players with the best weapon.", Module.Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.currentTarget = null;
      this.attackCooldownTicks = 0.0F;
      this.fadeBox = null;
      this.clearPendingAttack();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         float tps = this.getTps();
         this.attackCooldownTicks -= tps / 20.0F;
         if (this.attackCooldownTicks < 0.0F) {
            this.attackCooldownTicks = 0.0F;
         }

         this.preparePendingAttack();
      } else {
         this.clearPendingAttack();
      }
   }

   @Subscribe
   private void onWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (event.getStage() == Stage.POST) {
         this.performPendingAttack();
      }
   }

   private void preparePendingAttack() {
      this.clearPendingAttack();
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         MaceAuraModule maceAura = KetamineClub.moduleManager.getModuleByClass(MaceAuraModule.class);
         if (maceAura != null && maceAura.isEnabled() && maceAura.isPriorityClaimed()) {
            this.currentTarget = null;
         } else {
            if (!this.isMaceAttackReady()) {
               AutoCrystalModule ac = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
               if (ac != null && ac.isEnabled() && ac.getLastBestDamage() > 0.0F) {
                  this.currentTarget = null;
                  return;
               }
            }

            this.currentTarget = this.findTarget();
            if (this.currentTarget != null) {
               if (!(this.attackCooldownTicks > 0.0F)) {
                  int weaponSlot = this.getWeapon();
                  if (weaponSlot != -1) {
                     ItemStack weaponStack = mc.player.getInventory().getItem(weaponSlot);
                     int originalSlot = KetamineClub.swapManager.serverSlot();
                     boolean needSwap = weaponSlot != originalSlot;
                     if (!needSwap || !mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                        OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
                        if (!needSwap || offhand == null || !offhand.shouldDeferForEat()) {
                           Vec3 eyePos = mc.player.getEyePosition(1.0F);
                           Vec3 closestPoint = this.getClosestPointToEye(eyePos, this.currentTarget.getBoundingBox());
                           float[] angles = MathUtil.calcAngle(eyePos, closestPoint);
                           if (!this.currentTarget.getBoundingBox().contains(eyePos)) {
                              Vec3 lookVec = this.getLookVector(angles[0], angles[1]);
                              Vec3 reachEnd = eyePos.add(lookVec.scale(3.0));
                              if (this.currentTarget.getBoundingBox().clip(eyePos, reachEnd).isEmpty()) {
                                 return;
                              }
                           }

                           RotationRequest request = new RotationRequest("AutoSword", 6, angles[0], angles[1]);
                           if (KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request)) {
                              this.pendingTarget = this.currentTarget;
                              this.pendingWeapon = weaponStack;
                              this.pendingWeaponSlot = weaponSlot;
                              this.pendingTps = this.getTps();
                              this.pendingYaw = KetamineClub.rotationManager.getServerYaw();
                              this.pendingPitch = KetamineClub.rotationManager.getServerPitch();
                              this.pendingCrit = this.shouldCrit(weaponStack);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void performPendingAttack() {
      if (this.pendingTarget != null) {
         Entity target = this.pendingTarget;
         ItemStack weaponStack = this.pendingWeapon;
         int weaponSlot = this.pendingWeaponSlot;
         float tps = this.pendingTps;
         boolean doCrit = this.pendingCrit;
         float serverYaw = this.pendingYaw;
         float serverPitch = this.pendingPitch;
         this.clearPendingAttack();
         if (!nullCheck() && !mc.player.isDeadOrDying() && target.isAlive()) {
            int originalSlot = KetamineClub.swapManager.serverSlot();
            boolean needSwap = weaponSlot != originalSlot;
            if (!needSwap || !mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
               OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
               if (!needSwap || offhand == null || !offhand.shouldDeferForEat()) {
                  SwapManager.SwapHandle handle = null;
                  if (needSwap) {
                     int swapPriority = weaponStack.getItem() instanceof MaceItem ? 100 : 66;
                     handle = KetamineClub.swapManager.acquire("AutoSword", swapPriority);
                     if (handle == null) {
                        return;
                     }
                  }

                  try {
                     if (needSwap) {
                        mc.player.getInventory().setSelectedSlot(weaponSlot);
                        mc.gameMode.ensureHasSentCarriedItem();
                     }

                     if (doCrit) {
                        double x = mc.player.getX();
                        double y = mc.player.getY();
                        double z = mc.player.getZ();
                        boolean hc = mc.player.horizontalCollision;
                        mc.getConnection().send(new PosRot(x, y, z, serverYaw, serverPitch, mc.player.onGround(), hc));
                        mc.getConnection().send(new PosRot(x, y + 0.0625, z, serverYaw, serverPitch, false, hc));
                        mc.getConnection().send(new PosRot(x, y + 0.045, z, serverYaw, serverPitch, false, hc));
                     }

                     mc.gameMode.attack(mc.player, target);
                     if (this.swing.getValue()) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                     }
                  } finally {
                     if (needSwap) {
                        mc.player.getInventory().setSelectedSlot(originalSlot);
                        mc.gameMode.ensureHasSentCarriedItem();
                     }

                     if (handle != null) {
                        KetamineClub.swapManager.release(handle);
                     }
                  }

                  this.attackCooldownTicks = this.getBaseCooldownTicks(weaponStack, tps) * this.delay.getValue().floatValue();
               }
            }
         }
      }
   }

   private float getTps() {
      float tps = switch ((AutoSwordModule.TpsMode)this.tpsMode.getValue()) {
         case LATEST -> KetamineClub.tpsCounterService.getLatestTPS();
         case AVERAGE -> KetamineClub.tpsCounterService.getAverageTPS();
         default -> 20.0F;
      };
      return Float.isFinite(tps) && tps >= 1.0F ? tps : 1.0F;
   }

   private void clearPendingAttack() {
      this.pendingTarget = null;
      this.pendingWeapon = ItemStack.EMPTY;
      this.pendingWeaponSlot = -1;
      this.pendingCrit = false;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && !nullCheck() && !mc.player.isDeadOrDying()) {
         AutoCrystalModule ac = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (ac == null || !ac.isEnabled() || !(ac.getLastBestDamage() >= ac.getMinDamage())) {
            long now = System.currentTimeMillis();
            if (this.currentTarget != null) {
               float partialTicks = event.getDelta();
               double interpX = this.currentTarget.xOld + (this.currentTarget.getX() - this.currentTarget.xOld) * partialTicks;
               double interpY = this.currentTarget.yOld + (this.currentTarget.getY() - this.currentTarget.yOld) * partialTicks;
               double interpZ = this.currentTarget.zOld + (this.currentTarget.getZ() - this.currentTarget.zOld) * partialTicks;
               this.fadeBox = this.currentTarget
                  .getBoundingBox()
                  .move(interpX - this.currentTarget.getX(), interpY - this.currentTarget.getY(), interpZ - this.currentTarget.getZ());
               this.lastTargetTime = now;
            }

            if (this.fadeBox != null) {
               double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
               double t = (now - this.lastTargetTime) / fadeMs;
               if (t >= 1.0) {
                  this.fadeBox = null;
               } else {
                  Color sc = this.sideColor.getValue();
                  Color lc = this.lineColor.getValue();
                  RenderUtil.drawBoxFilled(
                     event.getMatrix(), this.fadeBox, new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), Mth.clamp((int)(sc.getAlpha() * (1.0 - t)), 0, 255))
                  );
                  RenderUtil.drawBox(
                     event.getMatrix(),
                     this.fadeBox,
                     new Color(lc.getRed(), lc.getGreen(), lc.getBlue(), Mth.clamp((int)(lc.getAlpha() * (1.0 - t)), 0, 255)),
                     1.5F
                  );
               }
            }
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (mc.player == null) {
         return null;
      } else {
         return this.macePriority.getValue() && mc.player.fallDistance > 0.1F ? String.format("Fall: %.1fm", mc.player.fallDistance) : null;
      }
   }

   public boolean isMaceAttackReady() {
      if (mc.player == null) {
         return false;
      } else if (MaceItem.canSmashAttack(mc.player) && !(mc.player.fallDistance < this.minSmashDist.getValue().floatValue())) {
         for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).getItem() instanceof MaceItem) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean shouldCrit(ItemStack weaponStack) {
      if (!this.criticals.getValue()) {
         return false;
      } else if (!mc.player.onGround()) {
         return false;
      } else if (mc.player.isInWater() || mc.player.isInLava()) {
         return false;
      } else if (mc.player.onClimbable() || mc.player.isPassenger()) {
         return false;
      } else if (mc.player.hasEffect(MobEffects.LEVITATION)) {
         return false;
      } else {
         return this.critsWithSword.getValue() && !weaponStack.is(ItemTags.SWORDS) && !weaponStack.is(ItemTags.AXES)
            ? false
            : !this.strict.getValue() || this.isPlayerPhasedIntoBlock();
      }
   }

   private boolean isPlayerPhasedIntoBlock() {
      AABB bb = mc.player.getBoundingBox();
      double eyeY = mc.player.getEyeY();
      double headMinY = Mth.clamp(eyeY - 0.1, bb.minY, bb.maxY);
      AABB headBox = new AABB(bb.minX, headMinY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
      int minX = Mth.floor(headBox.minX);
      int maxX = Mth.floor(headBox.maxX - 1.0E-7);
      int minY = Mth.floor(headBox.minY);
      int maxY = Mth.floor(headBox.maxY - 1.0E-7);
      int minZ = Mth.floor(headBox.minZ);
      int maxZ = Mth.floor(headBox.maxZ - 1.0E-7);

      for (int x = minX; x <= maxX; x++) {
         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               if (this.blockIntersectsBox(new BlockPos(x, y, z), headBox)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean blockIntersectsBox(BlockPos pos, AABB box) {
      VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
      return shape.isEmpty() ? false : shape.bounds().move(pos).intersects(box);
   }

   private Entity findTarget() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      if (targets == null) {
         return null;
      } else {
         Vec3 eyePos = mc.player.getEyePosition(1.0F);
         Entity best = null;
         double bestDist = Double.MAX_VALUE;

         for (Entity entity : mc.level.getEntities(null, mc.player.getBoundingBox().inflate(4.0))) {
            if (entity != mc.player && targets.isValidTarget(entity)) {
               double dist = eyePos.distanceTo(this.clampToBox(eyePos, entity.getBoundingBox()));
               if (!(dist > 3.0) && dist < bestDist) {
                  bestDist = dist;
                  best = entity;
               }
            }
         }

         return best;
      }
   }

   private float getBaseCooldownTicks(ItemStack stack, float tps) {
      float baseTicks;
      if (stack.is(ItemTags.SWORDS)) {
         baseTicks = 13.0F;
      } else if (stack.is(ItemTags.AXES)) {
         baseTicks = 21.0F;
      } else if (stack.getItem() instanceof TridentItem) {
         baseTicks = 19.0F;
      } else if (stack.getItem() instanceof MaceItem) {
         baseTicks = 34.0F;
      } else {
         baseTicks = 5.0F;
      }

      return baseTicks * (20.0F / tps);
   }

   private int getWeapon() {
      int bestSlot = -1;
      float bestDamage = -1.0F;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack held = mc.player.getInventory().getItem(slot);
         if (!held.isEmpty()) {
            boolean isSword = held.is(ItemTags.SWORDS);
            boolean isAxe = held.is(ItemTags.AXES);
            boolean isTrident = held.getItem() instanceof TridentItem;
            boolean isMace = held.getItem() instanceof MaceItem;
            if (isSword || isAxe || isTrident || isMace) {
               float attackDamage = 0.0F;
               if (held.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
                  ItemAttributeModifiers modifiers = (ItemAttributeModifiers)held.get(DataComponents.ATTRIBUTE_MODIFIERS);

                  for (Entry entry : modifiers.modifiers()) {
                     if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                        attackDamage += (float)entry.modifier().amount();
                     }
                  }
               }

               attackDamage += EnchantmentUtil.getLevel(Enchantments.SHARPNESS, held) * 1.25F;
               attackDamage += EnchantmentUtil.getLevel(Enchantments.SMITE, held) * 2.5F;
               attackDamage += EnchantmentUtil.getLevel(Enchantments.BANE_OF_ARTHROPODS, held) * 2.5F;
               if (isMace) {
                  boolean canSmash = MaceItem.canSmashAttack(mc.player) && mc.player.fallDistance >= this.minSmashDist.getValue().floatValue();
                  if (canSmash) {
                     if (this.macePriority.getValue()) {
                        attackDamage += 1000.0F;
                     }

                     attackDamage += this.getSmashDamageBonus(held, (float)mc.player.fallDistance);
                  } else if (this.macePriority.getValue() && this.onlySmash.getValue()) {
                     continue;
                  }
               }

               if (attackDamage > bestDamage) {
                  bestDamage = attackDamage;
                  bestSlot = slot;
               }
            }
         }
      }

      return bestSlot;
   }

   private Vec3 getClosestPointToEye(Vec3 eye, AABB box) {
      double x = Mth.clamp(eye.x, box.minX, box.maxX);
      double y = Mth.clamp(eye.y, box.minY, box.maxY);
      double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
      double VEC = 0.0625;
      double EPS = 1.0E-9;
      if (Math.abs(x - box.minX) < 1.0E-9) {
         x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
      } else if (Math.abs(x - box.maxX) < 1.0E-9) {
         x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
      }

      if (Math.abs(z - box.minZ) < 1.0E-9) {
         z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
      } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
         z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
      }

      return new Vec3(x, y, z);
   }

   private Vec3 clampToBox(Vec3 point, AABB box) {
      return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
   }

   private Vec3 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3(g * h, i, f * h);
   }

   private float getSmashDamageBonus(ItemStack mace, float fallDistance) {
      float bonus = 0.0F;
      if (fallDistance <= 3.0F) {
         bonus = fallDistance * 3.0F;
      } else if (fallDistance <= 8.0F) {
         bonus = 9.0F + (fallDistance - 3.0F) * 1.5F;
      } else {
         bonus = 16.5F + (fallDistance - 8.0F) * 0.5F;
      }

      int density = EnchantmentUtil.getLevel(Enchantments.DENSITY, mace);
      return bonus + density * fallDistance;
   }

   public static enum TpsMode {
      NONE,
      LATEST,
      AVERAGE;

      private TpsMode() {
      }
   }
}
