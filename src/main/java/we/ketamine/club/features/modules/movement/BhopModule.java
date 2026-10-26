package we.ketamine.club.features.modules.movement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public class BhopModule extends Module {
   private static final String ID = "Bhop";
   private static final int ROTATION_PRIORITY = 55;
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private static final int CHEST_MENU_SLOT = 6;
   private static final int FAKE_DASH_TICKS = 120;
   private static final int FAKE_PULSE_CLICK_PACKETS = 2;
   private static final int FAKE_SAFE_PULSES_PER_WINDOW = 38;
   private static final long FAKE_PULSE_INTERVAL_NANOS = 105789474L;
   private static final int OFFHAND_MENU_SLOT = 45;
   private final Setting<Double> jumpElytraPitch = this.num("JumpElytraPitch", -15.0, -45.0, 45.0);
   private final Setting<Boolean> fake = this.bool("Fake", false);
   private BhopModule.Phase phase = BhopModule.Phase.IDLE;
   private boolean applying;
   private boolean rotating;
   private boolean rocketFired;
   private int hopTicks;
   private int fakeDashTicks;
   private long nextFakePulseNanos;
   private int fakeElytraHotbarSlot = -1;
   private int fakeStagedElytraSource = -1;
   private ItemStack fakeStagedHotbarStack = ItemStack.EMPTY;
   private int fakeChestplateSource = -1;
   private ItemStack fakeOriginalChest = ItemStack.EMPTY;
   private String status;

   public BhopModule() {
      super("Bhop", "Turns jump inputs into longer fake-Elytra hops with automatic fireworks.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.disableConflict(ElytraAssistModule.class);
      this.disableConflict(ElytraFlyModule.class);
      this.disableConflict(ElytraDashModule.class);
      this.disableConflict(FakeFlyModule.class);
      this.disableConflict(VerticalDesyncModule.class);
      this.disableConflict(TestDoohickeyModule.class);
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      this.status = null;
      if (nullCheck()) {
         this.resetState();
      } else if (this.hasEnabledConflict()) {
         this.disable();
      } else if (mc.player.onGround()) {
         this.endHop();
         if (mc.options.keyJump.isDown() && !mc.player.isInWater() && !mc.player.isInLava() && this.prepareHop()) {
            this.phase = BhopModule.Phase.HOP;
         }
      } else if (this.phase == BhopModule.Phase.HOP) {
         this.hopTicks++;
         this.submitArcRotation();
         if (this.rocketFired) {
            if (!this.fake.getValue() && this.hasFakeFlightState()) {
               this.restoreFakeFlightState();
            }

            if (this.fake.getValue() && this.fakeDashTicks < 120) {
               this.fakeDashTicks++;
               if (this.maintainFakeFlight()) {
                  this.applying = true;
                  return;
               }
            } else if (this.fake.getValue()) {
               this.restoreFakeFlightState();
            }

            this.applying = mc.player.isFallFlying();
            if (!this.applying) {
               this.endHop();
            }
         } else if (this.hopTicks >= 2 && !(mc.player.fallDistance < 1.0) && !(mc.player.getDeltaMovement().y >= 0.0)) {
            if (this.pulseFlight()) {
               this.applying = this.rocketFired;
               if (this.fake.getValue()) {
                  this.fakeDashTicks = 0;
                  this.nextFakePulseNanos = System.nanoTime();
               }
            }
         }
      }
   }

   @Subscribe
   private void onDisconnect(DisconnectEvent event) {
      this.resetState();
   }

   private boolean prepareHop() {
      if (!this.canClickPlayerInventory()) {
         this.status = "Inventory";
         return false;
      } else if (!isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST)) && !this.findElytra().found()) {
         this.status = "No Elytra";
         return false;
      } else if (!InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE).found()) {
         this.status = "No Rocket";
         return false;
      } else {
         return true;
      }
   }

   private boolean pulseFlight() {
      if (!this.canClickPlayerInventory()) {
         this.status = "Inventory";
         return false;
      } else if (KetamineClub.swapManager.isBlockedByOther("Bhop", 15)) {
         this.status = "Swap";
         return false;
      } else {
         Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
         if (!rocket.found()) {
            this.status = "No Rocket";
            return false;
         } else {
            boolean chestAlreadyElytra = isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST));
            Result elytra = chestAlreadyElytra ? null : this.findElytra();
            if (!chestAlreadyElytra && !elytra.found()) {
               this.status = "No Elytra";
               return false;
            } else {
               int rocketHotbarSlot = rocket != null && rocket.found() && rocket.type() == ResultType.HOTBAR ? rocket.slot() : -1;
               int clickPackets = chestAlreadyElytra ? 0 : 2;
               if (!chestAlreadyElytra && elytra.type() == ResultType.INVENTORY) {
                  clickPackets += 2;
               }

               if (rocket.type() == ResultType.INVENTORY) {
                  clickPackets += 2;
               }

               if (KetamineClub.swapManager.willClickSlotBeLimited(clickPackets)) {
                  this.status = "Click Limit";
                  return false;
               } else {
                  int elytraSlot = -1;
                  boolean altSwappedElytra = false;
                  if (!chestAlreadyElytra) {
                     if (elytra.type() == ResultType.HOTBAR) {
                        elytraSlot = elytra.slot();
                     } else {
                        if (elytra.type() != ResultType.INVENTORY) {
                           this.status = "No Elytra";
                           return false;
                        }

                        elytraSlot = this.temporaryHotbarSlot(rocketHotbarSlot);
                        if (!InventoryUtil.altSwapInto(elytra, elytraSlot)) {
                           this.status = "Swap";
                           return false;
                        }

                        altSwappedElytra = true;
                     }
                  }

                  boolean chestSwapped = false;

                  boolean var9;
                  try {
                     if (!chestAlreadyElytra) {
                        InventoryUtil.click(6, elytraSlot, ContainerInput.SWAP);
                        chestSwapped = true;
                     }

                     if (!isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
                        this.status = "Swap";
                        return false;
                     }

                     if (!mc.player.tryToStartFallFlying()) {
                        this.status = "Flight";
                        return false;
                     }

                     mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
                     if (this.fireRocket(rocket)) {
                        this.rocketFired = true;
                        return true;
                     }

                     mc.player.stopFallFlying();
                     this.status = "Rocket";
                     var9 = false;
                  } finally {
                     if (chestSwapped) {
                        InventoryUtil.click(6, elytraSlot, ContainerInput.SWAP);
                     }

                     if (altSwappedElytra) {
                        InventoryUtil.altSwapInto(elytra, elytraSlot);
                     }
                  }

                  return var9;
               }
            }
         }
      }
   }

   private boolean fireRocket(Result rocket) {
      return KetamineClub.swapManager.submit(new SwapRequest("Bhop.rocket", 15, rocket, result -> mc.gameMode.useItem(mc.player, result.hand())));
   }

   private boolean maintainFakeFlight() {
      if (!this.ensureFakeFlightState()) {
         return false;
      } else {
         long now = System.nanoTime();
         if (now >= this.nextFakePulseNanos) {
            if (!this.fakePulseFlight()) {
               return false;
            }

            this.nextFakePulseNanos = now + 105789474L;
         }

         if (!mc.player.isFallFlying()) {
            mc.player.startFallFlying();
         }

         return true;
      }
   }

   private boolean ensureFakeFlightState() {
      if (!this.canClickPlayerInventory()) {
         this.status = "Inventory";
         return false;
      } else if (!this.ensureFakeChestplate()) {
         return false;
      } else if (this.fakeElytraHotbarSlot >= 0 && isUsableElytra(mc.player.getInventory().getItem(this.fakeElytraHotbarSlot))) {
         return true;
      } else {
         if (this.fakeStagedElytraSource != -1) {
            this.clearFakeElytraStage();
         }

         Result elytra = this.findElytra();
         if (!elytra.found()) {
            this.status = "No Elytra";
            return false;
         } else if (elytra.type() == ResultType.HOTBAR) {
            this.fakeElytraHotbarSlot = elytra.slot();
            return true;
         } else {
            int hotbarSlot = this.fakeStagingHotbarSlot();
            if (hotbarSlot >= 0 && !KetamineClub.swapManager.willClickSlotBeLimited(1)) {
               int sourceSlot = this.containerSlotOf(elytra);
               this.fakeStagedHotbarStack = mc.player.getInventory().getItem(hotbarSlot).copy();
               InventoryUtil.click(sourceSlot, hotbarSlot, ContainerInput.SWAP);
               if (!isUsableElytra(mc.player.getInventory().getItem(hotbarSlot))) {
                  this.status = "Swap Failed";
                  return false;
               } else {
                  this.fakeElytraHotbarSlot = hotbarSlot;
                  this.fakeStagedElytraSource = sourceSlot;
                  return true;
               }
            } else {
               this.status = hotbarSlot < 0 ? "Hotbar Full" : "Click Limit";
               return false;
            }
         }
      }
   }

   private boolean ensureFakeChestplate() {
      ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
      if (isChestplate(chest)) {
         return true;
      } else if (this.fakeChestplateSource != -1) {
         this.status = "Swap Failed";
         return false;
      } else {
         Result chestplate = InventoryUtil.find(BhopModule::isChestplate, InventoryUtil.FULL_SCOPE);
         if (!chestplate.found()) {
            this.status = "No Chestplate";
            return false;
         } else if (KetamineClub.swapManager.willClickSlotBeLimited(3)) {
            this.status = "Click Limit";
            return false;
         } else {
            int sourceSlot = this.containerSlotOf(chestplate);
            this.fakeChestplateSource = sourceSlot;
            this.fakeOriginalChest = chest.copy();
            this.swapChestArmorWith(sourceSlot);
            if (!isChestplate(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
               this.fakeChestplateSource = -1;
               this.fakeOriginalChest = ItemStack.EMPTY;
               this.status = "Swap Failed";
               return false;
            } else {
               return true;
            }
         }
      }
   }

   private boolean fakePulseFlight() {
      if (!this.canClickPlayerInventory()) {
         this.status = "Inventory";
         return false;
      } else if (this.fakeElytraHotbarSlot < 0 || !isUsableElytra(mc.player.getInventory().getItem(this.fakeElytraHotbarSlot))) {
         this.status = "No Elytra";
         return false;
      } else if (KetamineClub.swapManager.willClickSlotBeLimited(2)) {
         this.status = "Click Limit";
         return false;
      } else {
         InventoryUtil.click(6, this.fakeElytraHotbarSlot, ContainerInput.SWAP);

         boolean var1;
         try {
            if (isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
               mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
               if (!mc.player.isFallFlying()) {
                  mc.player.startFallFlying();
               }

               return true;
            }

            this.status = "Swap Failed";
            var1 = false;
         } finally {
            InventoryUtil.click(6, this.fakeElytraHotbarSlot, ContainerInput.SWAP);
         }

         return var1;
      }
   }

   private void restoreFakeFlightState() {
      if (!nullCheck() && this.canClickPlayerInventory()) {
         if (this.fakeStagedElytraSource != -1) {
            ItemStack hotbar = mc.player.getInventory().getItem(this.fakeElytraHotbarSlot);
            ItemStack source = this.stackAtContainerSlot(this.fakeStagedElytraSource);
            boolean sourceUnchanged = source.getCount() == this.fakeStagedHotbarStack.getCount()
               && ItemStack.isSameItemSameComponents(source, this.fakeStagedHotbarStack);
            if (hotbar.is(Items.ELYTRA) && sourceUnchanged) {
               if (KetamineClub.swapManager.willClickSlotBeLimited(1)) {
                  this.status = "Restore";
                  return;
               }

               InventoryUtil.click(this.fakeStagedElytraSource, this.fakeElytraHotbarSlot, ContainerInput.SWAP);
               this.clearFakeElytraStage();
            } else {
               this.clearFakeElytraStage();
            }
         }

         if (this.fakeChestplateSource != -1) {
            ItemStack source = this.stackAtContainerSlot(this.fakeChestplateSource);
            boolean sourceUnchanged = source.getCount() == this.fakeOriginalChest.getCount()
               && ItemStack.isSameItemSameComponents(source, this.fakeOriginalChest);
            if (!isChestplate(mc.player.getItemBySlot(EquipmentSlot.CHEST)) || !sourceUnchanged) {
               this.clearFakeChestplateStage();
            } else if (!KetamineClub.swapManager.willClickSlotBeLimited(3)) {
               this.swapChestArmorWith(this.fakeChestplateSource);
               this.clearFakeChestplateStage();
            } else {
               this.status = "Restore";
            }
         }
      }
   }

   private void clearFakeElytraStage() {
      this.fakeElytraHotbarSlot = -1;
      this.fakeStagedElytraSource = -1;
      this.fakeStagedHotbarStack = ItemStack.EMPTY;
   }

   private void clearFakeChestplateStage() {
      this.fakeChestplateSource = -1;
      this.fakeOriginalChest = ItemStack.EMPTY;
   }

   private boolean hasFakeFlightState() {
      return this.fakeStagedElytraSource != -1 || this.fakeChestplateSource != -1;
   }

   private int fakeStagingHotbarSlot() {
      int selected = InventoryUtil.selected();

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && mc.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      for (int slotx = 0; slotx < 9; slotx++) {
         if (slotx != selected) {
            return slotx;
         }
      }

      return selected;
   }

   private void swapChestArmorWith(int itemContainerSlot) {
      InventoryUtil.click(itemContainerSlot, 0, ContainerInput.PICKUP);
      InventoryUtil.click(6, 0, ContainerInput.PICKUP);
      InventoryUtil.click(itemContainerSlot, 0, ContainerInput.PICKUP);
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

   private int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      } else {
         return result.slot() < 9 ? result.slot() + 36 : result.slot();
      }
   }

   private static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.is(Items.ELYTRA)) {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private void submitArcRotation() {
      if (KetamineClub.rotationManager != null) {
         double vertical = mc.player.getDeltaMovement().y;
         float pitch = this.jumpElytraPitch.getValue().floatValue();
         if (vertical < -0.02) {
            pitch = -pitch;
         }

         KetamineClub.rotationManager.submit(new RotationRequest("Bhop", 55, mc.player.getYRot(), pitch, RotationRequest.Mode.MOTION, true, true, false));
         this.rotating = true;
      }
   }

   private Result findElytra() {
      return InventoryUtil.find(BhopModule::isUsableElytra, InventoryUtil.PLACE_SCOPE);
   }

   private int temporaryHotbarSlot(int avoidSlot) {
      int selected = InventoryUtil.selected();

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && slot != avoidSlot && mc.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      for (int slotx = 0; slotx < 9; slotx++) {
         if (slotx != selected && slotx != avoidSlot) {
            return slotx;
         }
      }

      for (int slotxx = 0; slotxx < 9; slotxx++) {
         if (slotxx != avoidSlot) {
            return slotxx;
         }
      }

      return selected;
   }

   private boolean canClickPlayerInventory() {
      return mc.gameMode != null && mc.getConnection() != null && mc.player.containerMenu == mc.player.inventoryMenu && InventoryUtil.cursor().isEmpty();
   }

   private void stopRotation() {
      if (this.rotating && KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("Bhop");
      }

      this.rotating = false;
   }

   private void resetState() {
      if (mc.player != null && mc.player.isFallFlying()) {
         mc.player.stopFallFlying();
      }

      this.restoreFakeFlightState();
      this.phase = BhopModule.Phase.IDLE;
      this.applying = false;
      this.rocketFired = false;
      this.hopTicks = 0;
      this.fakeDashTicks = 0;
      this.nextFakePulseNanos = 0L;
      this.status = null;
      this.stopRotation();
   }

   private void endHop() {
      if (mc.player != null && mc.player.isFallFlying()) {
         mc.player.stopFallFlying();
      }

      this.restoreFakeFlightState();
      this.phase = BhopModule.Phase.IDLE;
      this.applying = false;
      this.rocketFired = false;
      this.hopTicks = 0;
      this.fakeDashTicks = 0;
      this.nextFakePulseNanos = 0L;
      this.stopRotation();
   }

   private boolean hasEnabledConflict() {
      return this.isEnabled(ElytraAssistModule.class)
         || this.isEnabled(ElytraFlyModule.class)
         || this.isEnabled(ElytraDashModule.class)
         || this.isEnabled(FakeFlyModule.class)
         || this.isEnabled(VerticalDesyncModule.class)
         || this.isEnabled(TestDoohickeyModule.class);
   }

   private <T extends Module> boolean isEnabled(Class<T> type) {
      T module = KetamineClub.moduleManager.getModuleByClass(type);
      return module != null && module.isEnabled();
   }

   private <T extends Module> void disableConflict(Class<T> type) {
      T module = KetamineClub.moduleManager.getModuleByClass(type);
      if (module != null && module.isEnabled()) {
         module.disable();
      }
   }

   private static boolean isUsableElytra(ItemStack stack) {
      return stack.is(Items.ELYTRA) && LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST);
   }

   public boolean isApplying() {
      return this.applying;
   }

   @Override
   public String getDisplayInfo() {
      if (this.status != null) {
         return this.status;
      } else {
         return this.phase == BhopModule.Phase.HOP ? "Hop" : null;
      }
   }

   private static enum Phase {
      IDLE,
      HOP;

      private Phase() {
      }
   }
}
