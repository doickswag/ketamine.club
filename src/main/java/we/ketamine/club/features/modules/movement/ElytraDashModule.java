package we.ketamine.club.features.modules.movement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.mixin.entity.FireworkRocketEntityAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public class ElytraDashModule extends Module {
   private static final int CHEST_MENU_SLOT = 6;
   private static final double TAKEOFF_VELOCITY = 0.42;
   private static final int OFFHAND_MENU_SLOT = 45;
   private static final int FIRE_TIMEOUT = 40;
   private static final int FRESH_LIFE_TICKS = 6;
   private final Setting<ElytraDashModule.TakeoffMode> takeoffMode = this.mode("Takeoff", ElytraDashModule.TakeoffMode.VELOCITY);
   private final Setting<Integer> minBoostTicks = this.num("MinBoostTicks", 3, 0, 20);
   private ElytraDashModule.Phase phase = ElytraDashModule.Phase.EQUIP;
   private boolean weEquipped;
   private int stowedChestSlot = -1;
   private int ticksSinceRocket = 1073741823;
   private boolean awaitingRocket;

   public ElytraDashModule() {
      super("ElytraDash", "Swap on your elytra, lift straight into flight, and auto-fire fireworks; swaps back on disable.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.phase = ElytraDashModule.Phase.EQUIP;
      this.weEquipped = false;
      this.stowedChestSlot = -1;
      this.ticksSinceRocket = 1073741823;
   }

   @Override
   public void onDisable() {
      if (this.canClickPlayerInventory() && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
         if (this.weEquipped) {
            this.swapChestArmorWith(this.stowedChestSlot);
         } else {
            Result chestplate = InventoryUtil.find(ElytraDashModule::isChestplate, InventoryUtil.FULL_SCOPE);
            if (chestplate.found()) {
               this.swapChestArmorWith(this.containerSlotOf(chestplate));
            }
         }
      }

      this.weEquipped = false;
      this.stowedChestSlot = -1;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         switch (this.phase) {
            case EQUIP:
               this.tickEquip();
               break;
            case TAKEOFF:
               this.tickTakeoff();
               break;
            case FLY:
               this.tickFly();
         }
      }
   }

   private void tickEquip() {
      if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
         this.phase = ElytraDashModule.Phase.TAKEOFF;
      } else if (this.canClickPlayerInventory()) {
         Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.FULL_SCOPE);
         if (elytra.found()) {
            int elytraSlot = this.containerSlotOf(elytra);
            this.swapChestArmorWith(elytraSlot);
            this.stowedChestSlot = elytraSlot;
            this.weEquipped = true;
            this.phase = ElytraDashModule.Phase.TAKEOFF;
         }
      }
   }

   private void tickTakeoff() {
      if (mc.player.isFallFlying()) {
         this.enterFlight();
      } else if (mc.getConnection() != null) {
         if (mc.player.onGround()) {
            switch ((ElytraDashModule.TakeoffMode)this.takeoffMode.getValue()) {
               case VELOCITY:
                  Vec3 vel = mc.player.getDeltaMovement();
                  mc.player.setDeltaMovement(vel.x, 0.42, vel.z);
                  mc.player.setSprinting(true);
                  break;
               case VANILLA:
                  mc.player.jumpFromGround();
            }
         } else {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
            mc.player.startFallFlying();
            this.enterFlight();
         }
      }
   }

   private void tickFly() {
      if (!mc.player.isFallFlying()) {
         this.phase = ElytraDashModule.Phase.TAKEOFF;
      } else {
         this.ticksSinceRocket++;
         if (this.awaitingRocket) {
            if (!this.hasFreshAttachedRocket() && this.ticksSinceRocket < 40) {
               return;
            }

            this.awaitingRocket = false;
         }

         if (!this.hasUsefulBoost()) {
            if (this.fireRocket()) {
               this.awaitingRocket = true;
               this.ticksSinceRocket = 0;
            }
         }
      }
   }

   private boolean hasFreshAttachedRocket() {
      for (Entity e : mc.level.entitiesForRendering()) {
         if (e instanceof FireworkRocketEntity rocket
            && ((FireworkRocketEntityAccessor)rocket).ketamineclub$getAttachedToEntity() == mc.player
            && ((FireworkRocketEntityAccessor)rocket).ketamineclub$getLife() <= 6) {
            return true;
         }
      }

      return false;
   }

   private boolean hasUsefulBoost() {
      int bestRemaining = Integer.MIN_VALUE;

      for (Entity e : mc.level.entitiesForRendering()) {
         if (e instanceof FireworkRocketEntity rocket && ((FireworkRocketEntityAccessor)rocket).ketamineclub$getAttachedToEntity() == mc.player) {
            int remaining = this.estimateLifetime(rocket) - ((FireworkRocketEntityAccessor)rocket).ketamineclub$getLife();
            if (remaining > bestRemaining) {
               bestRemaining = remaining;
            }
         }
      }

      return bestRemaining > this.minBoostTicks.getValue();
   }

   private int estimateLifetime(FireworkRocketEntity rocket) {
      ItemStack stack = (ItemStack)rocket.getEntityData().get(FireworkRocketEntityAccessor.ketamineclub$getFireworksItemData());
      Fireworks fireworks = (Fireworks)stack.get(DataComponents.FIREWORKS);
      int flight = fireworks != null ? fireworks.flightDuration() : 1;
      return 10 * (flight + 1);
   }

   private void enterFlight() {
      this.phase = ElytraDashModule.Phase.FLY;
      this.ticksSinceRocket = 1073741823;
      this.awaitingRocket = false;
   }

   private boolean fireRocket() {
      Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
      if (!rocket.found()) {
         return false;
      } else if (!this.canClickPlayerInventory()) {
         return false;
      } else if (rocket.type() == ResultType.HOTBAR && rocket.slot() == InventoryUtil.selected()) {
         mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
         return true;
      } else {
         int containerSlot = this.containerSlotOf(rocket);
         InventoryUtil.click(containerSlot, InventoryUtil.selected(), ContainerInput.SWAP);

         try {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
         } finally {
            InventoryUtil.click(containerSlot, InventoryUtil.selected(), ContainerInput.SWAP);
         }

         return true;
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
         int slot = result.slot();
         return slot < 9 ? slot + 36 : slot;
      }
   }

   private static boolean isChestplate(ItemStack stack) {
      if (stack.is(Items.ELYTRA)) {
         return false;
      } else {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      }
   }

   private boolean canClickPlayerInventory() {
      return mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu;
   }

   @Override
   public String getDisplayInfo() {
      return switch (this.phase) {
         case EQUIP -> "Equipping";
         case TAKEOFF -> "Takeoff";
         case FLY -> "Flying";
      };
   }

   private static enum Phase {
      EQUIP,
      TAKEOFF,
      FLY;

      private Phase() {
      }
   }

   public static enum TakeoffMode {
      VELOCITY,
      VANILLA;

      private TakeoffMode() {
      }
   }
}
