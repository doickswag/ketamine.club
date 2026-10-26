package we.ketamine.club.features.modules.movement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public class FakeFlyModule extends Module {
   private static final String ID = "FakeFly";
   private static final int ROTATION_PRIORITY = 50;
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private static final int CHEST_MENU_SLOT = 6;
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final int MAX_SAFE_CLICK_PACKETS = 77;
   private static final int PULSE_CLICK_PACKETS = 2;
   private static final int SAFE_PULSES_PER_WINDOW = 38;
   private static final long PULSE_INTERVAL_NANOS = 105789474L;
   private static final double GRIM_AIR_FRICTION = 0.0264444413;
   private final Setting<Boolean> lockPitch = this.bool("LockPitch", true);
   private final Setting<Double> pitch = this.num("Pitch", 0.0, -45.0, 45.0);
   private final Setting<Double> climbPitch = this.num("ClimbPitch", 45.0, 15.0, 89.0);
   private final Setting<Boolean> hover = this.bool("Hover", false);
   private final Setting<Double> hoverPitch = this.num("HoverPitch", -1.3, -45.0, 0.0);
   private final Setting<Boolean> boost = this.bool("Boost", false);
   private final Setting<Double> friction = this.num("Friction", 0.0264444413, 0.001, 0.2);
   private final Setting<Boolean> autoRocket = this.bool("AutoRocket", true);
   private final Setting<Integer> refireLead = this.num("RefireLead", 3, 0, 10);
   private boolean steering;
   private boolean hovering;
   private boolean boosting;
   private float steeringYaw;
   private float steeringPitch;
   private int idlePhase;
   private long nextPulseNanos;
   private int rocketRemaining = -1;
   private int elytraHotbarSlot = -1;
   private int stagedElytraSource = -1;
   private ItemStack stagedHotbarStack = ItemStack.EMPTY;
   private String state;

   public FakeFlyModule() {
      super("FakeFly", "Pulses elytra flight while keeping a chestplate equipped and continuously boosting.", Module.Category.MOVEMENT);
      this.pitch.setVisibility(value -> this.lockPitch.getValue());
      this.hoverPitch.setVisibility(value -> this.hover.getValue());
      this.friction.setVisibility(value -> this.boost.getValue());
   }

   public void requestFlightPulse() {
      this.nextPulseNanos = System.nanoTime();
   }

   @Override
   public void onEnable() {
      this.disableConflict(BhopModule.class);
      this.disableConflict(ElytraFlyModule.class);
      this.disableConflict(ElytraTestModule.class);
      this.nextPulseNanos = System.nanoTime();
      this.idlePhase = 0;
      this.rocketRemaining = -1;
      this.boosting = false;
      this.hovering = false;
      this.clearStaging();
      this.state = null;
   }

   @Override
   public void onDisable() {
      this.stopSteering();
      this.restoreStagedElytra();
      this.rocketRemaining = -1;
      this.boosting = false;
      this.hovering = false;
      this.state = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      long now = System.nanoTime();
      this.boosting = false;
      this.hovering = false;
      this.state = null;
      if (nullCheck()) {
         this.stopSteering();
         this.clearStaging();
         this.state = null;
      } else if (mc.player.onGround()) {
         this.stopSteering();
         this.nextPulseNanos = now;
         this.rocketRemaining = -1;
         this.state = "Airborne";
      } else if (!this.ensureChestplateEquipped()) {
         this.stopSteering();
      } else {
         int hotbarSlot = this.ensureElytraHotbar();
         if (hotbarSlot == -1) {
            this.stopSteering();
         } else {
            this.updateSteering();
            if (this.rocketRemaining >= 0) {
               this.rocketRemaining--;
            }

            if (now >= this.nextPulseNanos && this.pulseFlight(hotbarSlot)) {
               long scheduled = this.nextPulseNanos + 105789474L;
               this.nextPulseNanos = scheduled > now ? scheduled : now + 105789474L;
            }

            if (!mc.player.isFallFlying()) {
               mc.player.startFallFlying();
            }

            if (this.boost.getValue() && !mc.player.isInWater() && !mc.player.isInLava() && mc.player.getFoodData().getFoodLevel() > 6.0F) {
               this.applyBoost();
               this.boosting = true;
            }

            if (this.state == null || this.state.equals("Airborne")) {
               this.state = this.boosting ? "Boost" : "Flying";
            }
         }
      }
   }

   private void updateSteering() {
      boolean forward = mc.options.keyUp.isDown();
      boolean back = mc.options.keyDown.isDown();
      boolean left = mc.options.keyLeft.isDown();
      boolean right = mc.options.keyRight.isDown();
      boolean up = mc.options.keyJump.isDown();
      boolean down = mc.options.keyShift.isDown();
      float realYaw = mc.player.getYRot();
      float strafe = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
      float forwardInput = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
      boolean hasHorizontal = strafe != 0.0F || forwardInput != 0.0F;
      boolean wantUp = up && !down;
      boolean wantDown = down && !up;
      if (hasHorizontal || wantUp || wantDown) {
         this.idlePhase = 0;
      }

      if (hasHorizontal) {
         float yaw = this.headingFromInput(realYaw, strafe, forwardInput);
         float targetPitch;
         if (wantUp) {
            targetPitch = -this.climbPitch.getValue().floatValue();
         } else if (wantDown) {
            targetPitch = this.climbPitch.getValue().floatValue();
         } else {
            targetPitch = this.lockPitch.getValue() ? this.pitch.getValue().floatValue() : mc.player.getXRot();
         }

         this.steerTo(yaw, targetPitch);
      } else if (wantUp) {
         this.steerTo(realYaw, -90.0F);
      } else if (wantDown) {
         this.steerTo(realYaw, 90.0F);
      } else {
         if (this.hover.getValue()) {
            boolean lookBack = (this.idlePhase++ & 1) == 1;
            this.steerTo(lookBack ? Mth.wrapDegrees(realYaw + 180.0F) : realYaw, this.hoverPitch.getValue().floatValue());
            this.hovering = true;
         } else {
            this.stopSteering();
         }
      }
   }

   private float headingFromInput(float cameraYaw, float strafe, float forward) {
      double yawRadians = Math.toRadians(cameraYaw);
      double forwardX = -Math.sin(yawRadians);
      double forwardZ = Math.cos(yawRadians);
      double rightX = -Math.sin(yawRadians + (Math.PI / 2));
      double rightZ = Math.cos(yawRadians + (Math.PI / 2));
      double worldX = forwardX * forward + rightX * strafe;
      double worldZ = forwardZ * forward + rightZ * strafe;
      return (float)Math.toDegrees(Math.atan2(worldZ, worldX)) - 90.0F;
   }

   private void steerTo(float yaw, float targetPitch) {
      this.steeringYaw = yaw;
      this.steeringPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
      KetamineClub.rotationManager.submit(new RotationRequest("FakeFly", 50, this.steeringYaw, this.steeringPitch, RotationRequest.Mode.MOTION, true, true));
      this.steering = true;
   }

   private void stopSteering() {
      this.idlePhase = 0;
      if (this.steering && KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("FakeFly");
         this.steering = false;
      }
   }

   private void applyBoost() {
      boolean ownRotation = this.steering && KetamineClub.rotationManager != null && KetamineClub.rotationManager.isActive("FakeFly");
      boolean otherRotation = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating();
      float yaw = ownRotation ? this.steeringYaw : (otherRotation ? KetamineClub.rotationManager.getRotationYaw() : mc.player.getYRot());
      float boostPitch = ownRotation ? this.steeringPitch : (otherRotation ? KetamineClub.rotationManager.getRotationPitch() : mc.player.getXRot());
      double yawRadians = Math.toRadians(yaw);
      double pitchRadians = Math.toRadians(boostPitch);
      double horizontal = Math.cos(pitchRadians);
      double lookX = -Math.sin(yawRadians) * horizontal;
      double lookY = -Math.sin(pitchRadians);
      double lookZ = Math.cos(yawRadians) * horizontal;
      double acceleration = this.friction.getValue();
      Vec3 velocity = mc.player.getDeltaMovement();
      mc.player.setDeltaMovement(velocity.x + lookX * acceleration, velocity.y + lookY * acceleration, velocity.z + lookZ * acceleration);
   }

   private boolean pulseFlight(int hotbarSlot) {
      if (!this.canClickPlayerInventory()) {
         this.state = "Inventory";
         return false;
      } else {
         boolean shouldFireRocket = this.shouldFireRocket();
         Result rocket = shouldFireRocket ? InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE) : null;
         int clickPackets = 2 + this.rocketClickPackets(rocket);
         if (this.willBeClickLimited(clickPackets)) {
            this.state = "Click Limit";
            return false;
         } else {
            InventoryUtil.click(6, hotbarSlot, ContainerInput.SWAP);

            try {
               if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                  this.state = "Swap Failed";
                  return false;
               }

               mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
               if (!mc.player.isFallFlying()) {
                  mc.player.startFallFlying();
               }

               if (rocket != null) {
                  if (rocket.found() && this.fireRocket(rocket)) {
                     this.rocketRemaining = InventoryUtil.fireworkRefireTicks(rocket.stack());
                     this.state = "Rocket";
                  } else if (!rocket.found()) {
                     this.state = "No Rocket";
                  }
               }
            } finally {
               InventoryUtil.click(6, hotbarSlot, ContainerInput.SWAP);
            }

            if (this.state == null) {
               this.state = "Pulse";
            }

            return true;
         }
      }
   }

   private boolean shouldFireRocket() {
      return this.autoRocket.getValue() && !this.hovering && this.rocketRemaining <= this.refireLead.getValue();
   }

   private int rocketClickPackets(Result rocket) {
      if (rocket != null && rocket.found() && !rocket.holding()) {
         return rocket.type() != ResultType.INVENTORY && !KetamineClub.swapManager.isHotbarBusy() ? 0 : 2;
      } else {
         return 0;
      }
   }

   private boolean fireRocket(Result rocket) {
      if (rocket.holding()) {
         mc.gameMode.useItem(mc.player, rocket.hand());
         return true;
      } else if (KetamineClub.swapManager.isHotbarBusy()) {
         int heldSlot = KetamineClub.swapManager.serverSlot();
         if (heldSlot >= 0 && heldSlot <= 8) {
            InventoryUtil.swapToHotbarSlot(rocket.slot(), heldSlot);

            try {
               mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } finally {
               InventoryUtil.swapToHotbarSlot(rocket.slot(), heldSlot);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return KetamineClub.swapManager.submit(new SwapRequest("FakeFly", 15, rocket, result -> mc.gameMode.useItem(mc.player, result.hand())));
      }
   }

   private int ensureElytraHotbar() {
      if (this.elytraHotbarSlot >= 0 && mc.player.getInventory().getItem(this.elytraHotbarSlot).is(Items.ELYTRA)) {
         return this.elytraHotbarSlot;
      } else {
         if (this.stagedElytraSource != -1) {
            this.clearStaging();
         }

         Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.FULL_SCOPE);
         if (!elytra.found()) {
            this.state = "No Elytra";
            return -1;
         } else if (elytra.type() == ResultType.HOTBAR) {
            this.elytraHotbarSlot = elytra.slot();
            return this.elytraHotbarSlot;
         } else if (!this.canClickPlayerInventory()) {
            this.state = "Inventory";
            return -1;
         } else {
            int hotbarSlot = this.stagingHotbarSlot();
            int sourceSlot = this.containerSlotOf(elytra);
            ItemStack displaced = mc.player.getInventory().getItem(hotbarSlot).copy();
            if (this.willBeClickLimited(1)) {
               this.state = "Click Limit";
               return -1;
            } else {
               InventoryUtil.click(sourceSlot, hotbarSlot, ContainerInput.SWAP);
               if (!mc.player.getInventory().getItem(hotbarSlot).is(Items.ELYTRA)) {
                  this.state = "Swap Failed";
                  return -1;
               } else {
                  this.elytraHotbarSlot = hotbarSlot;
                  this.stagedElytraSource = sourceSlot;
                  this.stagedHotbarStack = displaced;
                  return hotbarSlot;
               }
            }
         }
      }
   }

   private int stagingHotbarSlot() {
      int selected = InventoryUtil.selected();

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && mc.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      if (mc.player.getInventory().getItem(selected).isEmpty()) {
         return selected;
      } else {
         for (int slotx = 0; slotx < 9; slotx++) {
            if (slotx != selected) {
               return slotx;
            }
         }

         return selected;
      }
   }

   private void restoreStagedElytra() {
      if (this.stagedElytraSource != -1 && this.elytraHotbarSlot != -1 && !nullCheck() && this.canClickPlayerInventory()) {
         ItemStack hotbarStack = mc.player.getInventory().getItem(this.elytraHotbarSlot);
         ItemStack sourceStack = this.stackAtContainerSlot(this.stagedElytraSource);
         boolean sourceUnchanged = sourceStack.isEmpty() && this.stagedHotbarStack.isEmpty()
            || sourceStack.getCount() == this.stagedHotbarStack.getCount() && ItemStack.isSameItemSameComponents(sourceStack, this.stagedHotbarStack);
         if (hotbarStack.is(Items.ELYTRA) && sourceUnchanged && !this.willBeClickLimited(1)) {
            InventoryUtil.click(this.stagedElytraSource, this.elytraHotbarSlot, ContainerInput.SWAP);
         }

         this.clearStaging();
      } else {
         this.clearStaging();
      }
   }

   private ItemStack stackAtContainerSlot(int slot) {
      if (slot == 45) {
         return mc.player.getOffhandItem();
      } else if (slot >= 36 && slot < 45) {
         return mc.player.getInventory().getItem(slot - 36);
      } else {
         return slot >= 0 && slot < 36 ? mc.player.getInventory().getItem(slot) : ItemStack.EMPTY;
      }
   }

   private void clearStaging() {
      this.elytraHotbarSlot = -1;
      this.stagedElytraSource = -1;
      this.stagedHotbarStack = ItemStack.EMPTY;
   }

   private boolean ensureChestplateEquipped() {
      if (isChestplate(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
         return true;
      } else if (!this.canClickPlayerInventory()) {
         this.state = "Inventory";
         return false;
      } else {
         Result chestplate = InventoryUtil.find(FakeFlyModule::isChestplate, InventoryUtil.FULL_SCOPE);
         if (!chestplate.found()) {
            this.state = "No Chestplate";
            return false;
         } else if (this.willBeClickLimited(3)) {
            this.state = "Click Limit";
            return false;
         } else {
            this.swapChestArmorWith(this.containerSlotOf(chestplate));
            if (!isChestplate(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
               this.state = "Swap Failed";
               return false;
            } else {
               return true;
            }
         }
      }
   }

   private void swapChestArmorWith(int itemContainerSlot) {
      InventoryUtil.click(itemContainerSlot, 0, ContainerInput.PICKUP);
      InventoryUtil.click(6, 0, ContainerInput.PICKUP);
      InventoryUtil.click(itemContainerSlot, 0, ContainerInput.PICKUP);
   }

   private int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      } else {
         return result.slot() < 9 ? result.slot() + 36 : result.slot();
      }
   }

   private boolean canClickPlayerInventory() {
      return mc.gameMode != null && mc.getConnection() != null && mc.player.containerMenu == mc.player.inventoryMenu && InventoryUtil.cursor().isEmpty();
   }

   private boolean willBeClickLimited(int packets) {
      return KetamineClub.swapManager.willClickSlotBeLimited(packets);
   }

   private static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.is(Items.ELYTRA)) {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private <T extends Module> void disableConflict(Class<T> type) {
      T module = KetamineClub.moduleManager.getModuleByClass(type);
      if (module != null && module.isEnabled()) {
         module.disable();
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.hovering) {
         return "Hover";
      } else if (this.state != null) {
         return this.state;
      } else {
         return this.boosting ? "Boost" : null;
      }
   }
}
