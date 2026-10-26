package we.ketamine.club.manager;

import java.util.ArrayDeque;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public class SwapManager extends Feature {
   public static final int CLICK_SLOT_LIMIT = 79;
   public static final int CLICK_SLOT_THRESHOLD = 78;
   public static final long CLICK_SLOT_WINDOW_NANOS = 4020000000L;
   private SwapManager.SwapHandle active;
   private final ArrayDeque<SwapManager.SwapHandle> suspended = new ArrayDeque<>();
   private int serverSlot = -1;
   private int homeSlot = -1;
   private boolean wasUsing = false;
   private Item useItem = null;
   private InteractionHand useHand = null;
   private int useTotalTicks = 0;
   private int lastRemainingTicks = 0;
   private int useElapsedTicks = 0;
   private int useResetCount = 0;
   private int stalledTicks = 0;
   private boolean frozenLogged = false;
   private boolean overdurationLogged = false;
   private final ArrayDeque<Long> clickSlotPackets = new ArrayDeque<>();
   private static final int STALL_WARN_TICKS = 4;
   private static final int OVER_DURATION_SLACK = 2;

   public SwapManager() {
   }

   public void init() {
      EVENT_BUS.register(this);
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      boolean debug = KetamineClub.LOGGER.isDebugEnabled();
      if (event.getPacket() instanceof ServerboundSetCarriedItemPacket pkt) {
         if (debug && pkt.getSlot() != this.serverSlot) {
            String during = this.wasUsing ? " DURING-USE(" + name(this.useItem) + ", " + this.lastRemainingTicks + "t left) by=" + sender() : "";
            log("carried slot " + this.serverSlot + " -> " + pkt.getSlot() + " (activeSwap=" + this.holderStr() + ")" + during);
         }

         this.serverSlot = pkt.getSlot();
      } else if (debug && this.wasUsing && event.getPacket() instanceof ServerboundPlayerActionPacket pa && pa.getAction() == Action.SWAP_ITEM_WITH_OFFHAND) {
         log("offhand-swap action DURING-USE(" + name(this.useItem) + ", " + this.lastRemainingTicks + "t left) by=" + sender());
      } else if (debug && this.wasUsing && event.getPacket() instanceof ServerboundContainerClickPacket cc) {
         log(
            "container-click DURING-USE("
               + name(this.useItem)
               + ", "
               + this.lastRemainingTicks
               + "t left) container="
               + cc.containerId()
               + " slot="
               + cc.slotNum()
               + " button="
               + cc.buttonNum()
               + " (activeSwap="
               + this.holderStr()
               + ") by="
               + sender()
         );
      }
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
      invalidate(this.active);
      this.suspended.forEach(SwapManager::invalidate);
      this.active = null;
      this.suspended.clear();
      this.serverSlot = -1;
      this.homeSlot = -1;
      this.wasUsing = false;
      this.useItem = null;
      this.useHand = null;
      this.useTotalTicks = 0;
      this.lastRemainingTicks = 0;
      this.useElapsedTicks = 0;
      this.useResetCount = 0;
      this.stalledTicks = 0;
      this.frozenLogged = false;
      this.overdurationLogged = false;
      this.clickSlotPackets.clear();
   }

   private static void invalidate(SwapManager.SwapHandle handle) {
      if (handle != null) {
         handle.released = true;
         handle.lease = false;
         handle.onPreempt = null;
      }
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (mc.player == null) {
         this.wasUsing = false;
         this.homeSlot = -1;
      } else {
         if (this.active == null && this.homeSlot != -1) {
            if (InventoryUtil.selected() != this.homeSlot || this.serverSlot() != this.homeSlot) {
               this.selectHotbarSlot(this.homeSlot);
               log("restore home -> " + this.homeSlot);
            }

            this.homeSlot = -1;
         }

         boolean using = mc.player.isUsingItem();
         if (using && !this.wasUsing) {
            ItemStack stack = mc.player.getUseItem();
            this.useItem = stack.getItem();
            this.useHand = mc.player.getUsedItemHand();
            this.useTotalTicks = stack.getItem().getUseDuration(stack, mc.player);
            this.lastRemainingTicks = mc.player.getUseItemRemainingTicks();
            this.useElapsedTicks = 0;
            this.useResetCount = 0;
            this.stalledTicks = 0;
            this.frozenLogged = false;
            this.overdurationLogged = false;
            log("use START " + name(this.useItem) + " hand=" + this.useHand + " duration=" + this.useTotalTicks + "t (activeSwap=" + this.holderStr() + ")");
         } else if (using) {
            this.useElapsedTicks++;
            Item now = mc.player.getUseItem().getItem();
            if (now != this.useItem) {
               log("use ITEM-CHANGED mid-use " + name(this.useItem) + " -> " + name(now) + " (activeSwap=" + this.holderStr() + ")");
               this.useItem = now;
            }

            int remaining = mc.player.getUseItemRemainingTicks();
            if (remaining > this.lastRemainingTicks + 1) {
               this.useResetCount++;
               log(
                  "use RESET "
                     + name(this.useItem)
                     + " countdown "
                     + this.lastRemainingTicks
                     + " -> "
                     + remaining
                     + "t (restart #"
                     + this.useResetCount
                     + ", elapsed="
                     + this.useElapsedTicks
                     + "t, activeSwap="
                     + this.holderStr()
                     + ")"
               );
               this.stalledTicks = 0;
            } else if (remaining == this.lastRemainingTicks) {
               this.stalledTicks++;
               if (this.stalledTicks >= 4 && !this.frozenLogged) {
                  this.frozenLogged = true;
                  log(
                     "use STALL "
                        + name(this.useItem)
                        + " countdown frozen at "
                        + remaining
                        + "t for "
                        + this.stalledTicks
                        + "t (elapsed="
                        + this.useElapsedTicks
                        + "t, resets="
                        + this.useResetCount
                        + ", activeSwap="
                        + this.holderStr()
                        + ") \u2014 likely GAP-FAIL"
                  );
               }
            } else {
               this.stalledTicks = 0;
            }

            if (!this.overdurationLogged && this.useTotalTicks > 0 && this.useElapsedTicks > this.useTotalTicks + 2) {
               this.overdurationLogged = true;
               log(
                  "use OVER-DURATION "
                     + name(this.useItem)
                     + " still using after "
                     + this.useElapsedTicks
                     + "t (expected "
                     + this.useTotalTicks
                     + "t, remaining="
                     + remaining
                     + ", resets="
                     + this.useResetCount
                     + ", activeSwap="
                     + this.holderStr()
                     + ") \u2014 likely GAP-FAIL"
               );
            }

            this.lastRemainingTicks = remaining;
         } else if (this.wasUsing) {
            if (this.lastRemainingTicks > 2) {
               log(
                  "use STOP "
                     + name(this.useItem)
                     + " INTERRUPTED after "
                     + this.useElapsedTicks
                     + "t ("
                     + this.lastRemainingTicks
                     + "t left of "
                     + this.useTotalTicks
                     + ", resets="
                     + this.useResetCount
                     + ", stalls="
                     + this.stalledTicks
                     + ") \u2014 likely GAP-FAIL"
               );
            } else {
               log(
                  "use STOP "
                     + name(this.useItem)
                     + " completed in "
                     + this.useElapsedTicks
                     + "t (duration="
                     + this.useTotalTicks
                     + "t, resets="
                     + this.useResetCount
                     + ")"
               );
            }

            this.useItem = null;
            this.useHand = null;
         }

         this.wasUsing = using;
      }
   }

   private String holderStr() {
      return this.active == null ? "none" : this.active.id + "/" + this.active.priority;
   }

   private static String sender() {
      if (!KetamineClub.LOGGER.isDebugEnabled()) {
         return "?";
      } else {
         for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String cls = f.getClassName();
            if (cls.startsWith("we.ketamine.club.") && !cls.endsWith("SwapManager") && !cls.contains(".inventory.") && !cls.contains(".event.")) {
               int dot = cls.lastIndexOf(46);
               return cls.substring(dot + 1) + "." + f.getMethodName() + ":" + f.getLineNumber();
            }
         }

         return "?";
      }
   }

   private static String name(Item item) {
      if (item == null) {
         return "null";
      } else if (item == Items.TOTEM_OF_UNDYING) {
         return "TOTEM";
      } else if (item == Items.ENCHANTED_GOLDEN_APPLE) {
         return "GAPPLE";
      } else {
         return item == Items.GOLDEN_APPLE ? "APPLE" : item.toString();
      }
   }

   private static void log(String msg) {
      if (KetamineClub.LOGGER.isDebugEnabled()) {
         KetamineClub.LOGGER.debug("[Swap] {}", msg);
      }
   }

   public int serverSlot() {
      if (mc.player == null) {
         return this.serverSlot;
      } else {
         return this.serverSlot == -1 ? InventoryUtil.selected() : this.serverSlot;
      }
   }

   public boolean willClickSlotBeLimited(int additionalPackets) {
      long now = System.nanoTime();
      this.pruneClickSlotPackets(now);
      return this.clickSlotPackets.size() + additionalPackets >= 78;
   }

   public void recordClickSlotPacket() {
      long now = System.nanoTime();
      this.pruneClickSlotPackets(now);
      this.clickSlotPackets.addLast(now);
   }

   private void pruneClickSlotPackets(long now) {
      while (!this.clickSlotPackets.isEmpty() && now - this.clickSlotPackets.peekFirst() >= 4020000000L) {
         this.clickSlotPackets.removeFirst();
      }
   }

   public boolean submit(SwapRequest req) {
      if (!req.target.found()) {
         return false;
      } else if (this.isServerHolding(req.target)) {
         req.action.accept(req.target);
         return true;
      } else if (!req.silent && req.target.type() != ResultType.INVENTORY && !this.blockedByActiveHold(req.id, req.priority)) {
         SwapManager.SwapHandle h = this.acquire(req.id, req.priority, false, false);
         if (h == null) {
            return false;
         } else {
            int last = h.originalSlot;
            int lastServerSlot = this.serverSlot();

            boolean var5;
            try {
               this.selectHotbarSlot(req.target.slot());

               try {
                  req.action.accept(req.target);
               } finally {
                  if (this.holdsActive(h)) {
                     InventoryUtil.swap(last);
                     if (mc.getConnection() != null && this.serverSlot() != lastServerSlot) {
                        mc.getConnection().send(new ServerboundSetCarriedItemPacket(lastServerSlot));
                     }
                  }
               }

               var5 = true;
            } finally {
               this.release(h);
            }

            return var5;
         }
      } else {
         return this.altSwap(req);
      }
   }

   private void selectHotbarSlot(int slot) {
      InventoryUtil.swap(slot);
      if (this.serverSlot() != slot) {
         mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
      }
   }

   private boolean isServerHolding(Result target) {
      return target.type() == ResultType.OFFHAND || target.type() == ResultType.HOTBAR && target.slot() == this.serverSlot();
   }

   private boolean altSwap(SwapRequest req) {
      int held = this.serverSlot();
      if (!InventoryUtil.altSwapInto(req.target, held)) {
         return false;
      } else {
         try {
            req.action.accept(req.target);
         } finally {
            InventoryUtil.altSwapInto(req.target, held);
         }

         return true;
      }
   }

   public boolean isHotbarBusy() {
      return this.active != null && !this.active.released && !this.active.lease;
   }

   private boolean blockedByActiveHold(String id, int priority) {
      return this.active != null && !this.active.released && !this.active.lease && !this.active.id.equals(id) && this.active.priority >= priority;
   }

   public SwapManager.SwapHandle acquire(String id, int priority) {
      return this.acquire(id, priority, false, false);
   }

   public SwapManager.SwapHandle acquireLease(String id, int priority) {
      return this.acquire(id, priority, true, false);
   }

   private SwapManager.SwapHandle acquire(String id, int priority, boolean lease, boolean altSwap) {
      if (nullCheck()) {
         return null;
      } else if (altSwap) {
         if (this.active != null) {
            log("acquire " + id + "/" + priority + " alt-borrows " + this.active.id + "/" + this.active.priority);
         }

         return new SwapManager.SwapHandle(id, priority, InventoryUtil.selected());
      } else {
         if (this.active != null) {
            if (this.active.priority >= priority) {
               log("acquire DENIED " + id + "/" + priority + " \u2014 held by " + this.active.id + "/" + this.active.priority);
               return null;
            }

            if (this.active.lease) {
               this.suspended.push(this.active);
               log("acquire " + id + "/" + priority + " borrows lease " + this.active.id + "/" + this.active.priority);
            } else {
               log("acquire " + id + "/" + priority + " preempts " + this.active.id + "/" + this.active.priority);
               this.active.released = true;
               if (this.active.onPreempt != null) {
                  this.active.onPreempt.run();
               }
            }
         } else {
            log("acquire " + id + "/" + priority);
         }

         if (this.homeSlot == -1) {
            this.homeSlot = InventoryUtil.selected();
         }

         SwapManager.SwapHandle h = new SwapManager.SwapHandle(id, priority, InventoryUtil.selected());
         h.lease = lease;
         this.active = h;
         return h;
      }
   }

   public void release(SwapManager.SwapHandle h) {
      if (h != null && !h.released) {
         h.released = true;
         this.suspended.remove(h);
         if (this.active == h) {
            log("release " + h.id + "/" + h.priority);
            this.resumeOrClear();
         }
      }
   }

   private void resumeOrClear() {
      this.active = null;

      while (!this.suspended.isEmpty()) {
         SwapManager.SwapHandle handle = this.suspended.pop();
         if (!handle.released) {
            this.active = handle;
            log("resume " + this.active.id + "/" + this.active.priority);
            return;
         }
      }
   }

   public boolean holdsActive(SwapManager.SwapHandle h) {
      return h != null && this.active == h && !h.released;
   }

   public boolean holds(SwapManager.SwapHandle h) {
      return h != null && !h.released && (this.active == h || this.suspended.contains(h));
   }

   public boolean isBlocked(int priority) {
      return this.active != null && this.active.priority >= priority;
   }

   public boolean isBlockedByOther(String id, int priority) {
      return this.active != null && !this.active.id.equals(id) && this.active.priority >= priority;
   }

   public static final class SwapHandle {
      public final String id;
      public final int priority;
      public final int originalSlot;
      boolean released;
      boolean lease;
      Runnable onPreempt;

      SwapHandle(String id, int priority, int originalSlot) {
         this.id = id;
         this.priority = priority;
         this.originalSlot = originalSlot;
      }

      public boolean isReleased() {
         return this.released;
      }

      public void onPreempt(Runnable r) {
         this.onPreempt = r;
      }
   }
}
