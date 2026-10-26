package we.ketamine.club.features.modules.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.util.inventory.InventoryUtil;

public class OffhandModule extends Module {
   private final Setting<Boolean> antiGhost = this.bool("AntiGhost", true);
   private boolean managingGapple = false;
   private int originalSlot = -1;
   private int movedFromSlot = -1;
   private static final int GAPPLE_SWAP_PRIORITY = 90;
   private SwapManager.SwapHandle gappleLease;
   private boolean eatingGappleLatch = false;
   private int eatGraceTicks = 0;
   private static final int EAT_LATCH_GRACE = 3;
   private static final int MAX_REFILL_ATTEMPTS = 3;

   public OffhandModule() {
      super("Offhand", "Keeps a totem in your offhand and swaps the mainhand to a gapple on right-click.", Module.Category.COMBAT);
   }

   @Override
   public void onDisable() {
      if (!nullCheck() && this.managingGapple) {
         this.restoreGapple();
      }

      this.managingGapple = false;
      this.originalSlot = -1;
      this.movedFromSlot = -1;
      this.eatingGappleLatch = false;
      this.eatGraceTicks = 0;
      this.releaseGappleLease();
   }

   @Subscribe
   private void onTick(PreTickEvent event) {
      if (nullCheck()) {
         this.eatingGappleLatch = false;
         this.eatGraceTicks = 0;
      } else {
         this.updateEatingGappleLatch();
         if (mc.player.containerMenu.containerId == 0) {
            if (InventoryUtil.cursor().isEmpty()) {
               if (!mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                  this.refillOffhandTotem();
               }

               this.handleMainhandGapple();
            }
         }
      }
   }

   private void handleMainhandGapple() {
      boolean rmb = mc.options.keyUse.isDown();
      boolean onInteractable = rmb && this.isInteractableBlock();
      boolean want = rmb && !onInteractable && (this.isMainhandWeapon() || this.managingGapple);
      if (this.managingGapple) {
         boolean mainhandIsGapple = mc.player.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE);
         if (!rmb || !mainhandIsGapple) {
            if (!this.isEatingGapple() && !this.eatingGappleLatch) {
               this.restoreGapple();
            }
         }
      } else if (want) {
         if (!mc.player.isUsingItem() && this.isMainhandWeapon()) {
            int hotbarGapple = this.findGappleInHotbarExcludingSelected();
            int gappleSlot = hotbarGapple != -1 ? -1 : this.findGappleInInventory();
            if (hotbarGapple != -1 || gappleSlot != -1) {
               if (this.acquireGappleLease()) {
                  this.originalSlot = InventoryUtil.selected();
                  if (hotbarGapple != -1) {
                     this.movedFromSlot = -1;
                     InventoryUtil.swap(hotbarGapple);
                  } else {
                     this.movedFromSlot = gappleSlot;
                     InventoryUtil.swapToHotbarSlot(gappleSlot, this.originalSlot);
                  }

                  this.managingGapple = true;
               }
            }
         }
      }
   }

   private boolean acquireGappleLease() {
      if (this.gappleLease != null && !this.gappleLease.isReleased()) {
         return true;
      } else {
         this.gappleLease = KetamineClub.swapManager.acquireLease("Offhand", 90);
         return this.gappleLease != null;
      }
   }

   private void releaseGappleLease() {
      if (this.gappleLease != null) {
         KetamineClub.swapManager.release(this.gappleLease);
         this.gappleLease = null;
      }
   }

   private void restoreGapple() {
      if (this.movedFromSlot != -1) {
         InventoryUtil.swapToHotbarSlot(this.movedFromSlot, this.originalSlot);
      } else if (this.originalSlot != -1) {
         InventoryUtil.swap(this.originalSlot);
      }

      this.managingGapple = false;
      this.originalSlot = -1;
      this.movedFromSlot = -1;
      this.releaseGappleLease();
   }

   private boolean refillOffhandTotem() {
      if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
         return true;
      } else if (mc.player.containerMenu.containerId != 0) {
         return false;
      } else if (!InventoryUtil.cursor().isEmpty()) {
         return false;
      } else {
         for (int attempt = 0; attempt < 3; attempt++) {
            int slot = this.findTotem();
            if (slot == -1) {
               return false;
            }

            if (mc.player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
               InventoryUtil.swapToOffhand(slot);
               if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   @Subscribe(
      priority = 1
   )
   private void onPacket(PacketEvent.Receive event) {
      if (!nullCheck() && this.antiGhost.getValue()) {
         if (event.getPacket() instanceof ClientboundEntityEventPacket pkt) {
            if (pkt.getEventId() != 35 || pkt.getEntity(mc.level) != mc.player) {
               return;
            }

            if (mc.player.containerMenu.containerId != 0) {
               return;
            }

            mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            this.refillOffhandTotem();
         }
      }
   }

   private int findTotem() {
      for (int i = 35; i >= 9; i--) {
         if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      for (int ix = 8; ix >= 0; ix--) {
         if (mc.player.getInventory().getItem(ix).is(Items.TOTEM_OF_UNDYING)) {
            return ix;
         }
      }

      return -1;
   }

   private int findGappleInHotbarExcludingSelected() {
      int selected = InventoryUtil.selected();

      for (int i = 0; i < 9; i++) {
         if (i != selected && mc.player.getInventory().getItem(i).is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return i;
         }
      }

      return -1;
   }

   private int findGappleInInventory() {
      for (int i = 35; i >= 9; i--) {
         if (mc.player.getInventory().getItem(i).is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return i;
         }
      }

      return -1;
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return null;
      } else {
         int count = 0;

         for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) {
               count += s.getCount();
            }
         }

         return String.valueOf(count);
      }
   }

   private boolean isMainhandWeapon() {
      ItemStack mh = mc.player.getMainHandItem();
      if (mh.isEmpty()) {
         return true;
      } else if (!mh.is(ItemTags.SWORDS) && !mh.is(ItemTags.AXES) && !mh.is(ItemTags.PICKAXES)) {
         Item item = mh.getItem();
         return item instanceof TridentItem || item instanceof MaceItem;
      } else {
         return true;
      }
   }

   private boolean isInteractableBlock() {
      if (mc.hitResult instanceof BlockHitResult bhr) {
         BlockState state = mc.level.getBlockState(bhr.getBlockPos());
         if (state.getMenuProvider(mc.level, bhr.getBlockPos()) != null) {
            return true;
         }

         Block block = state.getBlock();
         if (block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock
            || block instanceof ShulkerBoxBlock
            || block instanceof EnderChestBlock) {
            return true;
         }
      }

      return false;
   }

   public boolean isEatingGapple() {
      return !nullCheck() && mc.player.isUsingItem() ? mc.player.getUseItem().is(Items.ENCHANTED_GOLDEN_APPLE) : false;
   }

   public boolean isManagingGapple() {
      return this.managingGapple || this.eatingGappleLatch;
   }

   public boolean shouldDeferForEat() {
      return this.isManagingGapple() || this.intendingToEat();
   }

   public boolean intendingToEat() {
      return !nullCheck() && mc.options.keyUse.isDown()
         ? this.isConsumable(mc.player.getMainHandItem()) || this.isConsumable(mc.player.getOffhandItem())
         : false;
   }

   private boolean isConsumable(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (stack.has(DataComponents.FOOD)) {
         return true;
      } else {
         ItemUseAnimation anim = stack.getUseAnimation();
         return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
      }
   }

   private void updateEatingGappleLatch() {
      boolean eatingNow = mc.player.isUsingItem()
         && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND
         && mc.player.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE);
      if (eatingNow) {
         this.eatingGappleLatch = true;
         this.eatGraceTicks = 3;
      } else if (this.eatingGappleLatch && --this.eatGraceTicks <= 0) {
         this.eatingGappleLatch = false;
      }
   }
}
