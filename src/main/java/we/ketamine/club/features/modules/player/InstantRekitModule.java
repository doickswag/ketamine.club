package we.ketamine.club.features.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.funny.XCarryModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.traits.Util;

public class InstantRekitModule extends Module {
   private final Setting<Boolean> topUpStacks = this.bool("TopUpStacks", true);
   private final Setting<Boolean> closeOnDone = this.bool("CloseOnDone", false);
   private final Setting<Boolean> xcarry = this.bool("XCarry", true);
   private static final int INVENTORY_SIZE = 36;
   private static final int KIT_SLOT_COUNT = 40;
   public static final String DEFAULT_KIT = "default";
   private final Map<String, Map<Integer, String>> kits = new LinkedHashMap<>();
   private String activeKit = "default";
   private boolean firedThisContainer;
   private AbstractContainerMenu lastContainer;
   private InstantRekitModule.OpenSource lastOpenSource;
   private InstantRekitModule.OpenSource containerSource;
   private InstantRekitModule.CraftRestore craftRestore;
   private boolean sendingReopen;

   public InstantRekitModule() {
      super("InstantRekit", "Restores a saved kit from an open container, including XCarry slots.", Module.Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.resetSession();
   }

   @Override
   public void onDisable() {
      this.resetSession();
   }

   private void resetSession() {
      this.firedThisContainer = false;
      this.lastContainer = null;
      this.lastOpenSource = null;
      this.containerSource = null;
      this.craftRestore = null;
   }

   private boolean useXCarry() {
      return this.xcarry.getValue() && XCarryModule.isActive();
   }

   public boolean isRestoringXCarry() {
      return this.isEnabled() && this.craftRestore != null;
   }

   public int saveKit(String name) {
      if (nullCheck()) {
         return 0;
      } else {
         Map<Integer, String> snapshot = new LinkedHashMap<>();

         for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
               snapshot.put(slot, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
         }

         if (this.useXCarry()) {
            for (int slotx = 1; slotx < 5; slotx++) {
               ItemStack stack = mc.player.inventoryMenu.getSlot(slotx).getItem();
               if (!stack.isEmpty()) {
                  snapshot.put(36 + slotx - 1, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
               }
            }
         }

         if (snapshot.isEmpty()) {
            return 0;
         } else {
            this.kits.put(name, snapshot);
            this.craftRestore = null;
            this.activeKit = name;
            return snapshot.size();
         }
      }
   }

   public boolean loadKit(String name) {
      if (!this.kits.containsKey(name)) {
         return false;
      } else {
         this.activeKit = name;
         this.firedThisContainer = false;
         this.craftRestore = null;
         return true;
      }
   }

   public Set<String> listKits() {
      return this.kits.keySet();
   }

   public boolean hasKit() {
      Map<Integer, String> kit = this.kits.get(this.activeKit);
      return kit != null && !kit.isEmpty();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (nullCheck() || mc.player.isDeadOrDying()) {
         this.resetSession();
      } else if (this.craftRestore != null) {
         this.advanceCraftRestore();
      } else if (!this.isContainerOpen()) {
         this.firedThisContainer = false;
         this.lastContainer = null;
         this.containerSource = null;
      } else {
         if (this.lastContainer != mc.player.containerMenu) {
            this.lastContainer = mc.player.containerMenu;
            this.containerSource = this.claimOpenSource();
            this.firedThisContainer = false;
         }

         if (!this.firedThisContainer) {
            if (InventoryUtil.cursor().isEmpty()) {
               this.firedThisContainer = true;
               this.rekit();
            }
         }
      }
   }

   private void rekit() {
      Map<Integer, String> kit = this.kits.get(this.activeKit);
      if (kit == null || kit.isEmpty()) {
         Command.sendMessage("{red} No kit saved. Use .savekit <name> while holding your kit.");
      } else if (InventoryUtil.cursor().isEmpty()) {
         if (this.useXCarry()) {
            InstantRekitModule.OpenSource source = this.containerSource != null && this.canReopen(this.containerSource) ? this.containerSource : null;
            List<InstantRekitModule.CraftMove> crafting = this.stageCrafting(kit, source != null);
            if (!InventoryUtil.cursor().isEmpty()) {
               this.firedThisContainer = false;
               return;
            }

            if (!crafting.isEmpty()) {
               this.craftRestore = new InstantRekitModule.CraftRestore(mc.level, mc.player.containerMenu, new LinkedHashMap<>(kit), crafting, source);
               if (source == null && !this.closeOnDone.getValue()) {
                  Map<Integer, String> regular = new LinkedHashMap<>(kit);

                  for (InstantRekitModule.CraftMove move : crafting) {
                     regular.remove(move.inventorySlot);
                  }

                  this.restoreInventory(regular);
                  Command.sendMessage("XCarry items staged. Close this container to fill the crafting grid; reopen it to finish the inventory.");
               } else {
                  mc.player.closeContainer();
                  this.advanceCraftRestore();
               }

               return;
            }
         }

         this.restoreInventory(kit);
         if (this.closeOnDone.getValue()) {
            mc.player.closeContainer();
         }
      }
   }

   private void restoreInventory(Map<Integer, String> kit) {
      for (InstantRekitModule.Move move : this.plan(kit)) {
         this.execute(move);
      }
   }

   private List<InstantRekitModule.CraftMove> stageCrafting(Map<Integer, String> kit, boolean canReopen) {
      List<InstantRekitModule.CraftMove> moves = new ArrayList<>();
      Set<Integer> reserved = new HashSet<>();

      for (Entry<Integer, String> entry : kit.entrySet()) {
         int logicalSlot = entry.getKey();
         if (logicalSlot >= 36 && logicalSlot < 40) {
            Item want = item(entry.getValue());
            if (want != null) {
               int craftSlot = 1 + logicalSlot - 36;
               ItemStack current = mc.player.inventoryMenu.getSlot(craftSlot).getItem();
               boolean matches = !current.isEmpty() && current.getItem() == want;
               if (!matches || this.topUpStacks.getValue() && current.isStackable() && current.getCount() < current.getMaxStackSize()) {
                  int source = this.findContainerSource(want, matches ? current : null);
                  if (source != -1) {
                     int buffer = this.findStagingSlot(kit, reserved, source, canReopen);
                     if (buffer == -1) {
                        Command.sendMessage("{yellow} XCarry needs an empty inventory slot or a saved-kit slot to stage its items.");
                        break;
                     }

                     this.execute(new InstantRekitModule.Move(source, buffer, false));
                     if (!InventoryUtil.cursor().isEmpty()) {
                        break;
                     }

                     ItemStack staged = mc.player.getInventory().getItem(buffer);
                     if (!staged.isEmpty() && staged.getItem() == want && (!matches || ItemStack.isSameItemSameComponents(staged, current))) {
                        int required = matches ? current.getMaxStackSize() - current.getCount() : staged.getMaxStackSize();

                        while (true) {
                           if (this.topUpStacks.getValue() && staged.isStackable() && staged.getCount() < required) {
                              source = this.findContainerSource(want, staged);
                              if (source != -1) {
                                 int before = staged.getCount();
                                 this.execute(new InstantRekitModule.Move(source, buffer, true));
                                 staged = mc.player.getInventory().getItem(buffer);
                                 if (InventoryUtil.cursor().isEmpty() && staged.getCount() > before) {
                                    continue;
                                 }
                              }
                           }

                           reserved.add(buffer);
                           moves.add(new InstantRekitModule.CraftMove(buffer, craftSlot, staged.copy()));
                           break;
                        }
                     }
                  }
               }
            }
         }
      }

      return moves;
   }

   private int findContainerSource(Item want, ItemStack compatible) {
      AbstractContainerMenu menu = mc.player.containerMenu;
      int count = Math.max(0, menu.slots.size() - 36);
      int best = -1;
      int bestCount = 0;

      for (int slot = 0; slot < count; slot++) {
         ItemStack stack = menu.getSlot(slot).getItem();
         if (!stack.isEmpty()
            && stack.getItem() == want
            && menu.getSlot(slot).mayPickup(mc.player)
            && (compatible == null || ItemStack.isSameItemSameComponents(stack, compatible))
            && stack.getCount() > bestCount) {
            best = slot;
            bestCount = stack.getCount();
         }
      }

      return best;
   }

   private int findStagingSlot(Map<Integer, String> kit, Set<Integer> reserved, int source, boolean canReopen) {
      for (int offset = 0; offset < 36; offset++) {
         int slot = (offset + 9) % 36;
         if (!reserved.contains(slot) && mc.player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      if (!canReopen) {
         return -1;
      } else {
         for (int offsetx = 0; offsetx < 36; offsetx++) {
            int slot = (offsetx + 9) % 36;
            if (kit.containsKey(slot) && !reserved.contains(slot) && mc.player.containerMenu.getSlot(source).mayPlace(mc.player.getInventory().getItem(slot))) {
               return slot;
            }
         }

         return -1;
      }
   }

   private void advanceCraftRestore() {
      InstantRekitModule.CraftRestore restore = this.craftRestore;
      if (restore.level == mc.level && restore.player == mc.player && this.useXCarry()) {
         if (!restore.transferred) {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
               if (mc.player.containerMenu != restore.originalMenu) {
                  this.craftRestore = null;
               }

               return;
            }

            if (!InventoryUtil.cursor().isEmpty()) {
               return;
            }

            for (InstantRekitModule.CraftMove move : restore.moves) {
               ItemStack staged = mc.player.getInventory().getItem(move.inventorySlot);
               if (!staged.isEmpty() && ItemStack.isSameItemSameComponents(staged, move.expected)) {
                  int sourceSlot = move.inventorySlot < 9 ? 36 + move.inventorySlot : move.inventorySlot;
                  InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
                  InventoryUtil.click(move.craftingSlot, 0, ContainerInput.PICKUP);
                  if (!InventoryUtil.cursor().isEmpty()) {
                     InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
                  }
               }
            }

            restore.transferred = true;
            restore.reopenDeadlineMs = System.currentTimeMillis() + 10000L;
            this.firedThisContainer = false;
            this.lastContainer = null;
            if (restore.source == null) {
               this.craftRestore = null;
               return;
            }
         }

         if (this.isContainerOpen()) {
            if (InventoryUtil.cursor().isEmpty()) {
               this.restoreInventory(restore.kit);
               this.craftRestore = null;
               this.firedThisContainer = true;
               this.lastContainer = mc.player.containerMenu;
               this.containerSource = restore.source;
               if (this.closeOnDone.getValue()) {
                  mc.player.closeContainer();
               }
            }
         } else if (mc.player.containerMenu == mc.player.inventoryMenu && !PingBypassInput.isScreenOpen()) {
            long now = System.currentTimeMillis();
            if (now < restore.reopenDeadlineMs && this.canReopen(restore.source) && (restore.openAttempts < 3 || now < restore.nextOpenMs)) {
               if (now >= restore.nextOpenMs) {
                  if (!mc.player.isShiftKeyDown() && !mc.player.isUsingItem()) {
                     restore.openAttempts++;
                     restore.nextOpenMs = now + 1500L;
                     float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(), restore.source.hit.getLocation());
                     KetamineClub.rotationManager
                        .submit(new RotationRequest("InstantRekit", 80, angles[0], angles[1], RotationRequest.Mode.SILENT, false, true, false));
                     this.sendingReopen = true;

                     try {
                        BlockStatePredictionHandler prediction = ((ClientLevelAccessor)mc.level)
                           .ketamineclub$getBlockStatePredictionHandler()
                           .startPredicting();

                        try {
                           mc.getConnection().send(new ServerboundUseItemOnPacket(restore.source.hand, restore.source.hit, prediction.currentSequence()));
                        } catch (Throwable var13) {
                           if (prediction != null) {
                              try {
                                 prediction.close();
                              } catch (Throwable var12) {
                                 var13.addSuppressed(var12);
                              }
                           }

                           throw var13;
                        }

                        if (prediction != null) {
                           prediction.close();
                        }
                     } finally {
                        this.sendingReopen = false;
                     }
                  }
               }
            } else {
               this.craftRestore = null;
               Command.sendMessage("{yellow} XCarry filled. Reopen the source container to finish restoring the inventory.");
            }
         } else {
            this.craftRestore = null;
         }
      } else {
         this.craftRestore = null;
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (!this.sendingReopen && !nullCheck() && event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
         if (!(mc.gui.screen() instanceof AbstractContainerScreen)) {
            BlockHitResult hit = packet.getHitResult();
            if (mc.level.getBlockState(hit.getBlockPos()).getMenuProvider(mc.level, hit.getBlockPos()) != null) {
               if (!mc.player.isShiftKeyDown()) {
                  if (this.craftRestore != null && this.craftRestore.transferred) {
                     this.craftRestore = null;
                  }

                  this.lastOpenSource = new InstantRekitModule.OpenSource(
                     mc.level,
                     new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos().immutable(), hit.isInside()),
                     packet.getHand(),
                     System.currentTimeMillis()
                  );
               }
            }
         }
      }
   }

   private InstantRekitModule.OpenSource claimOpenSource() {
      InstantRekitModule.OpenSource source = this.lastOpenSource;
      this.lastOpenSource = null;
      return source != null && System.currentTimeMillis() - source.sentMs <= 5000L && this.canReopen(source) ? source : null;
   }

   private boolean canReopen(InstantRekitModule.OpenSource source) {
      return source.level == mc.level
         && mc.getConnection() != null
         && mc.player.getEyePosition().distanceTo(source.hit.getLocation()) <= 5.0
         && mc.level.getBlockState(source.hit.getBlockPos()).getMenuProvider(mc.level, source.hit.getBlockPos()) != null;
   }

   private List<InstantRekitModule.Move> plan(Map<Integer, String> kit) {
      AbstractContainerMenu menu = mc.player.containerMenu;
      int containerCount = Math.max(0, menu.slots.size() - 36);
      InstantRekitModule.Usage usage = new InstantRekitModule.Usage(menu, containerCount);
      List<InstantRekitModule.Move> moves = new ArrayList<>();
      Map<Integer, ItemStack> freshlyPlaced = new HashMap<>();

      for (Entry<Integer, String> entry : kit.entrySet()) {
         int target = entry.getKey();
         if (target >= 0 && target < 36) {
            Item want = item(entry.getValue());
            if (want != null) {
               ItemStack cur = mc.player.getInventory().getItem(target);
               if (cur.isEmpty() || cur.getItem() != want) {
                  int src = usage.bestSource(want);
                  if (src != -1) {
                     freshlyPlaced.put(target, menu.getSlot(src).getItem());
                     moves.add(new InstantRekitModule.Move(src, target, false));
                     usage.consumeAll(src);
                  }
               }
            }
         }
      }

      if (this.topUpStacks.getValue()) {
         for (Entry<Integer, String> entryx : kit.entrySet()) {
            int target = entryx.getKey();
            if (target >= 0 && target < 36) {
               Item want = item(entryx.getValue());
               if (want != null) {
                  ItemStack cur = freshlyPlaced.getOrDefault(target, mc.player.getInventory().getItem(target));
                  if (!cur.isEmpty() && cur.getItem() == want && cur.isStackable()) {
                     int need = cur.getMaxStackSize() - cur.getCount();
                     int src;
                     if (need > 0) {
                        while (need > 0 && (src = usage.bestSource(want, cur)) != -1) {
                           int avail = usage.remaining(src);
                           moves.add(new InstantRekitModule.Move(src, target, true));
                           int used = Math.min(need, avail);
                           usage.consume(src, used);
                           need -= used;
                        }
                     }
                  }
               }
            }
         }
      }

      return moves;
   }

   private void execute(InstantRekitModule.Move move) {
      AbstractContainerMenu menu = mc.player.containerMenu;
      int containerCount = Math.max(0, menu.slots.size() - 36);
      ItemStack source = menu.getSlot(move.source).getItem();
      if (!source.isEmpty()) {
         int targetSlot = handlerSlot(move.target, containerCount);
         ItemStack target = menu.getSlot(targetSlot).getItem();
         if (!move.topUp || ItemStack.isSameItemSameComponents(source, target)) {
            boolean fullMerge = move.topUp && source.getCount() <= target.getMaxStackSize() - target.getCount();
            if (target.isEmpty() || fullMerge || menu.getSlot(move.source).mayPlace(target)) {
               if (!move.topUp && move.target <= 8) {
                  InventoryUtil.click(move.source, move.target, ContainerInput.SWAP);
               } else {
                  InventoryUtil.click(move.source, 0, ContainerInput.PICKUP);
                  InventoryUtil.click(targetSlot, 0, ContainerInput.PICKUP);
                  if (!InventoryUtil.cursor().isEmpty()) {
                     InventoryUtil.click(move.source, 0, ContainerInput.PICKUP);
                  }
               }
            }
         }
      }
   }

   private static int handlerSlot(int invIndex, int containerCount) {
      return invIndex <= 8 ? containerCount + 27 + invIndex : containerCount + (invIndex - 9);
   }

   private boolean isContainerOpen() {
      if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen && !(screen instanceof InventoryScreen))) {
         return false;
      } else if (this.isEnderChest(screen)) {
         return false;
      } else {
         AbstractContainerMenu menu = mc.player.containerMenu;
         return menu != null && menu.slots.size() > 36;
      }
   }

   private boolean isEnderChest(AbstractContainerScreen<?> screen) {
      Component title = screen.getTitle();
      return title.getContents() instanceof TranslatableContents tc && "container.enderchest".equals(tc.getKey())
         ? true
         : title.getString().equalsIgnoreCase("Ender Chest");
   }

   private static Item item(String id) {
      if (id == null) {
         return null;
      } else {
         Identifier key = Identifier.tryParse(id);
         return key == null ? null : (Item)BuiltInRegistries.ITEM.getValue(key);
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = super.toJson().getAsJsonObject();
      JsonObject kitsObject = new JsonObject();

      for (Entry<String, Map<Integer, String>> kitEntry : this.kits.entrySet()) {
         JsonArray array = new JsonArray();

         for (Entry<Integer, String> entry : kitEntry.getValue().entrySet()) {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", entry.getKey());
            slot.addProperty("item", entry.getValue());
            array.add(slot);
         }

         kitsObject.add(kitEntry.getKey(), array);
      }

      object.add("Kits", kitsObject);
      object.addProperty("ActiveKit", this.activeKit);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      super.fromJson(element);
      this.resetSession();
      this.kits.clear();
      this.activeKit = "default";
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         JsonElement kitsElement = object.get("Kits");
         if (kitsElement != null && kitsElement.isJsonObject()) {
            for (Entry<String, JsonElement> kitEntry : kitsElement.getAsJsonObject().entrySet()) {
               if (kitEntry.getValue().isJsonArray()) {
                  this.kits.put(kitEntry.getKey(), readKit(kitEntry.getValue().getAsJsonArray()));
               }
            }
         }

         JsonElement legacy = object.get("Kit");
         boolean migratedLegacy = false;
         if (legacy != null && legacy.isJsonArray()) {
            Map<Integer, String> kit = readKit(legacy.getAsJsonArray());
            if (!kit.isEmpty()) {
               migratedLegacy = this.kits.putIfAbsent("default", kit) == null;
            }
         }

         JsonElement active = object.get("ActiveKit");
         if (!migratedLegacy && active != null && active.isJsonPrimitive()) {
            this.activeKit = active.getAsString();
         }

         if (!this.kits.isEmpty() && !this.kits.containsKey(this.activeKit)) {
            this.activeKit = this.kits.containsKey("default") ? "default" : this.kits.keySet().iterator().next();
         }
      }
   }

   private static Map<Integer, String> readKit(JsonArray array) {
      Map<Integer, String> kit = new LinkedHashMap<>();

      for (JsonElement el : array) {
         if (el.isJsonObject()) {
            JsonObject slot = el.getAsJsonObject();
            if (slot.has("slot") && slot.has("item")) {
               int index = slot.get("slot").getAsInt();
               if (index >= 0 && index < 40) {
                  kit.put(index, slot.get("item").getAsString());
               }
            }
         }
      }

      return kit;
   }

   private record CraftMove(int inventorySlot, int craftingSlot, ItemStack expected) {
   }

   private static final class CraftRestore {
      final Level level;
      final Player player;
      final AbstractContainerMenu originalMenu;
      final Map<Integer, String> kit;
      final List<InstantRekitModule.CraftMove> moves;
      final InstantRekitModule.OpenSource source;
      boolean transferred;
      int openAttempts;
      long nextOpenMs;
      long reopenDeadlineMs;

      CraftRestore(
         Level level,
         AbstractContainerMenu originalMenu,
         Map<Integer, String> kit,
         List<InstantRekitModule.CraftMove> moves,
         InstantRekitModule.OpenSource source
      ) {
         this.level = level;
         this.player = Util.mc.player;
         this.originalMenu = originalMenu;
         this.kit = kit;
         this.moves = moves;
         this.source = source;
      }
   }

   private record Move(int source, int target, boolean topUp) {
   }

   private record OpenSource(Level level, BlockHitResult hit, InteractionHand hand, long sentMs) {
   }

   private static final class Usage {
      private final AbstractContainerMenu menu;
      private final int containerCount;
      private final Map<Integer, Integer> remaining = new HashMap<>();

      Usage(AbstractContainerMenu menu, int containerCount) {
         this.menu = menu;
         this.containerCount = containerCount;
      }

      int remaining(int slot) {
         return this.remaining.computeIfAbsent(slot, i -> this.menu.getSlot(i).getItem().getCount());
      }

      int bestSource(Item want) {
         return this.bestSource(want, null);
      }

      int bestSource(Item want, ItemStack compatible) {
         int best = -1;
         int bestCount = 0;

         for (int i = 0; i < this.containerCount; i++) {
            ItemStack s = this.menu.getSlot(i).getItem();
            if (!s.isEmpty()
               && s.getItem() == want
               && this.menu.getSlot(i).mayPickup(Util.mc.player)
               && (compatible == null || ItemStack.isSameItemSameComponents(s, compatible))) {
               int count = this.remaining(i);
               if (count > bestCount) {
                  bestCount = count;
                  best = i;
               }
            }
         }

         return best;
      }

      void consume(int slot, int amount) {
         this.remaining.put(slot, Math.max(0, this.remaining(slot) - amount));
      }

      void consumeAll(int slot) {
         this.remaining.put(slot, 0);
      }
   }
}
