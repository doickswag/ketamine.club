package we.ketamine.club.manager;

import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.modules.combat.AutoCrystalModule;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.modules.combat.SurroundModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;

public class PlacementManager extends Feature {
   private static final int WINDOW_LIMIT = 9;
   private static final long WINDOW_MS = 300L;
   private static final String MINE_CHAIN_SWAP_ID = "MineCrystalChain";
   private static final int MINE_CHAIN_SWAP_PRIORITY = 122;
   private static final int MINE_CHAIN_CLICK_PACKETS = 2;
   private static final long PER_BLOCK_COOLDOWN_MS = 30L;
   public static final int SURROUND_WALL_PRIORITY = 100;
   public static final int SURROUND_REPAIR_PRIORITY = 200;
   private final PlacementWindow placementWindow = new PlacementWindow(9, 300L);
   private final Map<BlockPos, Long> sentAt = new ConcurrentHashMap<>();
   private final Queue<PlacementManager.PlacementTask> queue = new PriorityQueue<>();
   private final Set<BlockPos> queued = new HashSet<>();
   private long queueOrder;
   private boolean placing = false;
   private boolean wasUsingItem = false;
   private int lastSwapSequenceTick = -1;
   private PlacementManager.SwapSequenceOwner lastSwapSequenceOwner = PlacementManager.SwapSequenceOwner.OTHER;
   private final CopyOnWriteArrayList<PlacementManager.PlacementListener> listeners = new CopyOnWriteArrayList<>();
   private final CopyOnWriteArrayList<PlacementManager.PlacementListener> immediateListeners = new CopyOnWriteArrayList<>();
   private final Queue<PlacementManager.PlacementNotice> pendingNotices = new ConcurrentLinkedQueue<>();

   private boolean swapSequenceAvailable() {
      return mc.player == null || mc.player.tickCount != this.lastSwapSequenceTick;
   }

   private static int containerSlotOf(int slot) {
      return slot < 9 ? slot + 36 : slot;
   }

   private static boolean isInventorySlot(int slot) {
      return slot > 8;
   }

   private boolean canAltSwap() {
      return mc.player != null && mc.player.containerMenu.containerId == 0;
   }

   private int altSwapIn(int slot) {
      if (!isInventorySlot(slot)) {
         return slot;
      } else if (!this.canAltSwap()) {
         return -1;
      } else {
         int selected = InventoryUtil.selected();
         InventoryUtil.click(slot, selected, ContainerInput.SWAP);
         return selected;
      }
   }

   private void altSwapOut(int slot, int hotbarSlot) {
      if (isInventorySlot(slot)) {
         InventoryUtil.click(slot, hotbarSlot, ContainerInput.SWAP);
      }
   }

   private boolean reactiveCrystalSequenceAvailable() {
      return this.swapSequenceAvailable()
         || this.lastSwapSequenceOwner == PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL
         || this.lastSwapSequenceOwner == PlacementManager.SwapSequenceOwner.PISTON_CRYSTAL;
   }

   private boolean crystalSequenceAvailable(PlacementManager.SwapSequenceOwner owner, boolean reactive) {
      if (owner != PlacementManager.SwapSequenceOwner.PISTON_CRYSTAL) {
         return reactive ? this.reactiveCrystalSequenceAvailable() : this.swapSequenceAvailable();
      } else {
         return this.swapSequenceAvailable() || this.lastSwapSequenceOwner == PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL;
      }
   }

   private boolean canSwapForBlock(int slot) {
      if (slot >= 0 && slot < 36 && !mc.player.getInventory().getItem(slot).isEmpty()) {
         int useSlot = isInventorySlot(slot) ? InventoryUtil.selected() : slot;
         int clicks = isInventorySlot(slot) ? 2 : 0;
         if (KetamineClub.swapManager.isHotbarBusy() && useSlot != KetamineClub.swapManager.serverSlot()) {
            clicks += 2;
         }

         return clicks == 0 || this.canAltSwap() && InventoryUtil.cursor().isEmpty() && !KetamineClub.swapManager.willClickSlotBeLimited(clicks);
      } else {
         return false;
      }
   }

   private boolean canSwapForCrystal(int slot) {
      return slot >= 0 && slot < 36 && !mc.player.getInventory().getItem(slot).isEmpty()
         ? !isInventorySlot(slot) || this.canAltSwap() && InventoryUtil.cursor().isEmpty() && !KetamineClub.swapManager.willClickSlotBeLimited(2)
         : false;
   }

   private void markSwapSequenceUsed(PlacementManager.SwapSequenceOwner owner) {
      if (mc.player != null) {
         this.lastSwapSequenceTick = mc.player.tickCount;
         this.lastSwapSequenceOwner = owner;
      }
   }

   private void prunePlacementWindow() {
      this.placementWindow.prune(System.currentTimeMillis());
   }

   public boolean placementWindowAvailable(int additionalPlacements) {
      return this.placementWindow.allows(System.currentTimeMillis(), additionalPlacements, this.surroundReserve());
   }

   public boolean repairPlacementWindowAvailable(int additionalPlacements) {
      return this.placementWindow.allows(System.currentTimeMillis(), additionalPlacements, 0);
   }

   public long repairWindowWaitMs() {
      return this.placementWindow.waitMs(System.currentTimeMillis(), 1, 0);
   }

   private int surroundReserve() {
      if (KetamineClub.moduleManager == null) {
         return 0;
      } else {
         SurroundModule surround = KetamineClub.moduleManager.getModuleByClass(SurroundModule.class);
         return surround == null ? 0 : surround.reservedRepairPlacements();
      }
   }

   public void addListener(PlacementManager.PlacementListener listener) {
      this.listeners.addIfAbsent(listener);
   }

   public void removeListener(PlacementManager.PlacementListener listener) {
      this.listeners.remove(listener);
   }

   public void addImmediateListener(PlacementManager.PlacementListener listener) {
      this.immediateListeners.addIfAbsent(listener);
   }

   public void removeImmediateListener(PlacementManager.PlacementListener listener) {
      this.immediateListeners.remove(listener);
   }

   public PlacementManager() {
      EVENT_BUS.register(this);
   }

   public boolean enqueue(BlockPos pos, int hotbarSlot) {
      pos = pos.immutable();
      if (this.isOnCooldown(pos)) {
         return false;
      } else {
         return !this.queued.add(pos)
            ? false
            : this.queue.offer(new PlacementManager.PlacementTask(pos, null, hotbarSlot, false, false, 0, this.queueOrder++, null));
      }
   }

   public boolean enqueueExtendedReach(BlockPos pos, int hotbarSlot) {
      pos = pos.immutable();
      if (this.isOnCooldown(pos)) {
         return false;
      } else {
         return !this.queued.add(pos)
            ? false
            : this.queue.offer(new PlacementManager.PlacementTask(pos, null, hotbarSlot, true, false, 0, this.queueOrder++, null));
      }
   }

   public boolean enqueue(BlockPos pos, Direction face, int hotbarSlot) {
      pos = pos.immutable();
      if (this.isOnCooldown(pos)) {
         return false;
      } else {
         return !this.queued.add(pos)
            ? false
            : this.queue.offer(new PlacementManager.PlacementTask(pos, face, hotbarSlot, false, false, 0, this.queueOrder++, null));
      }
   }

   public boolean enqueueSurround(BlockPos pos, int slot, int priority, PlacementManager.PlacementListener listener) {
      if (this.isOnCooldown(pos)) {
         return false;
      } else {
         PlacementManager.PlacementTask previous = this.queue.stream().filter(task -> task.pos().equals(pos)).findFirst().orElse(null);
         if (previous != null) {
            if (previous.listener() == listener) {
               if (previous.priority() == priority && previous.hotbarSlot() == slot) {
                  return false;
               }
            } else if (previous.priority() >= priority) {
               return false;
            }

            this.queue.remove(previous);
            if (previous.listener() != listener) {
               this.discard(previous);
            }
         }

         this.queued.add(pos.immutable());
         return this.queue
            .offer(
               new PlacementManager.PlacementTask(pos, null, slot, false, false, priority, previous == null ? this.queueOrder++ : previous.order(), listener)
            );
      }
   }

   public void removeQueuedFor(PlacementManager.PlacementListener owner, Predicate<BlockPos> filter) {
      this.queue.removeIf(task -> {
         if (task.listener() == owner && filter.test(task.pos())) {
            this.queued.remove(task.pos());
            this.discard(task);
            return true;
         } else {
            return false;
         }
      });
   }

   private void discard(PlacementManager.PlacementTask task) {
      if (task.listener() != null) {
         task.listener().onPlacementDiscarded(task.pos());
      }
   }

   private boolean isOnCooldown(BlockPos pos) {
      Long last = this.sentAt.get(pos);
      if (last == null) {
         return false;
      } else if (System.currentTimeMillis() - last >= 30L) {
         this.sentAt.remove(pos, last);
         return false;
      } else {
         return true;
      }
   }

   public void clearQueue() {
      for (PlacementManager.PlacementTask task : this.queue) {
         this.discard(task);
      }

      this.queue.clear();
      this.queued.clear();
   }

   public void removeQueuedFor(Predicate<BlockPos> filter) {
      this.queue.removeIf(task -> {
         if (filter.test(task.pos())) {
            this.queued.remove(task.pos());
            this.discard(task);
            return true;
         } else {
            return false;
         }
      });
   }

   public void forceResetPlaceCooldown(BlockPos pos) {
      this.sentAt.remove(pos);
   }

   public boolean hasPending() {
      return !this.queue.isEmpty();
   }

   public boolean isPlacing() {
      return this.placing;
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
      this.placementWindow.clear();
      this.sentAt.clear();
      this.pendingNotices.clear();
      this.clearQueue();
      this.placing = false;
      this.wasUsingItem = false;
      this.lastSwapSequenceTick = -1;
      this.lastSwapSequenceOwner = PlacementManager.SwapSequenceOwner.OTHER;
      this.queueOrder = 0L;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (nullCheck()) {
         this.wasUsingItem = false;
      } else {
         this.dispatchBlockUpdates();
         this.flushQueue();
         this.wasUsingItem = mc.player.isUsingItem();
      }
   }

   private boolean usingItemAnyTick() {
      return mc.player.isUsingItem() || this.wasUsingItem;
   }

   public void flushQueue() {
      if (!nullCheck() && !this.placing) {
         this.prunePlacementWindow();
         if (!this.usingItemAnyTick()) {
            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            if (offhand == null || !offhand.shouldDeferForEat()) {
               boolean defensive = !this.queue.isEmpty() && this.queue.peek().priority() >= 100;
               boolean regularSequence = this.swapSequenceAvailable();
               if (regularSequence || defensive) {
                  long now = System.currentTimeMillis();
                  int reserve = this.surroundReserve();
                  int budget = this.placementWindow.available(now, 0);
                  if (budget > 0) {
                     List<PlacementManager.PreparedClick> ready = new ArrayList<>();
                     List<PlacementManager.PlacementTask> deferred = new ArrayList<>();
                     int burstSlot = -1;

                     while (ready.size() < budget && !this.queue.isEmpty()) {
                        PlacementManager.PlacementTask task = this.queue.poll();
                        if (task.listener() != null && !task.listener().validateQueuedPlacement(task.pos())) {
                           this.queued.remove(task.pos());
                           this.discard(task);
                        } else {
                           if (task.listener() != null) {
                              int priority = task.listener().queuedPlacementPriority(task.pos(), task.priority());
                              if (priority != task.priority()) {
                                 this.queue
                                    .offer(
                                       new PlacementManager.PlacementTask(
                                          task.pos(),
                                          task.face(),
                                          task.hotbarSlot(),
                                          task.extendedReach(),
                                          task.allowEntityCollision(),
                                          priority,
                                          task.order(),
                                          task.listener()
                                       )
                                    );
                                 continue;
                              }
                           }

                           if (task.priority() < 100 && (!regularSequence || ready.size() >= this.placementWindow.available(now, reserve))) {
                              deferred.add(task);
                              break;
                           }

                           if (task.hotbarSlot() < 0 || task.hotbarSlot() >= 36 || mc.player.getInventory().getItem(task.hotbarSlot()).isEmpty()) {
                              this.queued.remove(task.pos());
                              this.discard(task);
                           } else if (!this.canSwapForBlock(task.hotbarSlot())) {
                              deferred.add(task);
                           } else if (burstSlot != -1 && task.hotbarSlot() != burstSlot) {
                              deferred.add(task);
                           } else {
                              this.queued.remove(task.pos());
                              PlacementManager.PreparedClick prepared = this.prepareClick(task);
                              if (prepared != null) {
                                 ready.add(prepared);
                                 burstSlot = task.hotbarSlot();
                              } else {
                                 this.discard(task);
                              }
                           }
                        }
                     }

                     for (PlacementManager.PlacementTask task : deferred) {
                        this.queue.offer(task);
                     }

                     if (!ready.isEmpty()) {
                        boolean sent = false;
                        this.placing = true;

                        try {
                           int useSlot = this.altSwapIn(burstSlot);
                           if (useSlot >= 0) {
                              try {
                                 this.sendBurst(ready, useSlot);
                                 sent = true;
                              } finally {
                                 this.altSwapOut(burstSlot, useSlot);
                              }
                           }
                        } finally {
                           this.placing = false;
                        }

                        if (!sent) {
                           for (PlacementManager.PreparedClick click : ready) {
                              if (click.listener() != null) {
                                 click.listener().onPlacementDiscarded(click.pos());
                              }
                           }
                        } else {
                           this.markSwapSequenceUsed(PlacementManager.SwapSequenceOwner.OTHER);
                           long stamp = System.currentTimeMillis();

                           for (PlacementManager.PreparedClick p : ready) {
                              this.recordBlockPlacement(p, stamp);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public List<BlockPos> placeBatchOffhand(List<BlockPos> positions, int hotbarSlot) {
      return this.placeBatch(positions, hotbarSlot, false);
   }

   public List<BlockPos> placeBatchExtendedReach(List<BlockPos> positions, int hotbarSlot) {
      return this.hasPending() ? List.of() : this.placeBatch(positions, hotbarSlot, true);
   }

   private List<BlockPos> placeBatch(List<BlockPos> positions, int hotbarSlot, boolean extendedReach) {
      if (nullCheck() || positions.isEmpty()) {
         return List.of();
      } else if (!this.canSwapForBlock(hotbarSlot)) {
         return List.of();
      } else {
         this.prunePlacementWindow();
         if (this.usingItemAnyTick()) {
            return List.of();
         } else {
            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            if (offhand != null && offhand.shouldDeferForEat()) {
               return List.of();
            } else if (!this.swapSequenceAvailable()) {
               return List.of();
            } else {
               int budget = this.placementWindow.available(System.currentTimeMillis(), this.surroundReserve());
               if (budget <= 0) {
                  return List.of();
               } else {
                  List<PlacementManager.PreparedClick> ready = new ArrayList<>();

                  for (BlockPos pos : positions) {
                     if (ready.size() >= budget) {
                        break;
                     }

                     if (!this.isOnCooldown(pos)) {
                        PlacementManager.PreparedClick prepared = this.prepareClick(
                           new PlacementManager.PlacementTask(pos, null, hotbarSlot, extendedReach, false)
                        );
                        if (prepared != null) {
                           ready.add(prepared);
                        }
                     }
                  }

                  if (ready.isEmpty()) {
                     return List.of();
                  } else {
                     boolean sent = false;
                     this.placing = true;

                     try {
                        int useSlot = this.altSwapIn(hotbarSlot);
                        if (useSlot >= 0) {
                           try {
                              this.sendBurst(ready, useSlot);
                              sent = true;
                           } finally {
                              this.altSwapOut(hotbarSlot, useSlot);
                           }
                        }
                     } finally {
                        this.placing = false;
                     }

                     if (!sent) {
                        return List.of();
                     } else {
                        this.markSwapSequenceUsed(PlacementManager.SwapSequenceOwner.OTHER);
                        long stamp = System.currentTimeMillis();
                        List<BlockPos> placed = new ArrayList<>(ready.size());

                        for (PlacementManager.PreparedClick p : ready) {
                           this.recordBlockPlacement(p, stamp);
                           placed.add(p.pos());
                        }

                        return placed;
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   public BlockHitResult prepareAirPlaceHit(BlockPos pos) {
      if (this.isOnCooldown(pos)) {
         return null;
      } else if (!PlaceUtil.canPlace(pos)) {
         return null;
      } else {
         Vec3 eye = mc.player.getEyePosition();
         Direction bestSide = null;
         double bestDistSq = Double.MAX_VALUE;

         for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            BlockState state = mc.level.getBlockState(neighbour);
            if (!state.isAir() && state.getFluidState().isEmpty() && !this.isInteractable(state.getBlock())) {
               Vec3 hit = Vec3.atCenterOf(pos).relative(side, 0.5);
               Vec3 normal = new Vec3(-side.getStepX(), -side.getStepY(), -side.getStepZ());
               if (!(eye.subtract(hit).dot(normal) <= 0.01)) {
                  double distSq = eye.distanceToSqr(hit);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     bestSide = side;
                  }
               }
            }
         }

         if (bestSide != null) {
            return new BlockHitResult(Vec3.atCenterOf(pos).relative(bestSide, 0.5), bestSide.getOpposite(), pos.relative(bestSide), false);
         } else {
            Direction dir = this.getAirPlaceDirection(pos);
            return new BlockHitResult(this.getAirPlaceHitPos(pos, dir), dir, pos, false);
         }
      }
   }

   public void notePlacement(BlockPos pos) {
      long stamp = System.currentTimeMillis();
      this.sentAt.put(pos, stamp);
      this.placementWindow.record(stamp);
   }

   private void recordBlockPlacement(PlacementManager.PreparedClick click, long stamp) {
      this.sentAt.put(click.pos(), stamp);
      this.placementWindow.record(stamp);
      if (click.listener() != null) {
         click.listener().onPlacementSent(click.pos(), stamp);
      }
   }

   @Nullable
   public Direction getPlaceSide(BlockPos pos) {
      Vec3 eye = mc.player.getEyePosition();
      double bestDistSq = Double.MAX_VALUE;
      Direction bestSide = null;

      for (Direction side : Direction.values()) {
         BlockPos neighbour = pos.relative(side);
         BlockState neighborState = mc.level.getBlockState(neighbour);
         if (!neighborState.isAir() && neighborState.getFluidState().isEmpty() && !this.isInteractable(neighborState.getBlock())) {
            double dx = neighbour.getX() + 0.5 - eye.x;
            double dy = neighbour.getY() + 0.5 - eye.y;
            double dz = neighbour.getZ() + 0.5 - eye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               bestSide = side;
            }
         }
      }

      return bestSide;
   }

   public boolean isInteractable(Block block) {
      return block instanceof BaseEntityBlock
         || block instanceof DoorBlock
         || block instanceof TrapDoorBlock
         || block instanceof FenceGateBlock
         || block instanceof ButtonBlock
         || block instanceof LeverBlock
         || block instanceof BedBlock
         || block instanceof NoteBlock
         || block instanceof AnvilBlock;
   }

   @Nullable
   private PlacementManager.PreparedClick prepareClick(PlacementManager.PlacementTask task) {
      BlockPos pos = task.pos();
      if (task.listener() == null && task.priority() < 200) {
         if (task.extendedReach()) {
            if (mc.level.isOutsideBuildHeight(pos)) {
               return null;
            }

            if (!mc.level.getBlockState(pos).canBeReplaced()) {
               return null;
            }

            if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
               return null;
            }

            if (!PlaceUtil.noEntityCollision(pos)) {
               return null;
            }
         } else if (task.allowEntityCollision()) {
            if (mc.level.isOutsideBuildHeight(pos)) {
               return null;
            }

            if (!mc.level.getBlockState(pos).canBeReplaced()) {
               return null;
            }

            if (Vec3.atCenterOf(pos).distanceTo(mc.player.getEyePosition()) > 5.154) {
               return null;
            }

            if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
               return null;
            }
         } else if (!PlaceUtil.canPlace(pos)) {
            return null;
         }

         Direction dir = task.face() != null ? task.face() : this.getPlaceSide(pos);
         BlockPos neighbour;
         Vec3 hitPos;
         Direction hitSide;
         if (dir == null) {
            Direction airDir = this.getAirPlaceDirection(pos);
            neighbour = pos;
            hitPos = this.getAirPlaceHitPos(pos, airDir);
            hitSide = airDir;
         } else {
            neighbour = pos.relative(dir);
            hitPos = Vec3.atCenterOf(pos).relative(dir, 0.5);
            hitSide = dir.getOpposite();
         }

         ItemStack stack = mc.player.getInventory().getItem(task.hotbarSlot());
         if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState desired = blockItem.getBlock().defaultBlockState();
            PlacementManager.AdjustedHit adjusted = this.adjustHitForAxisBlock(pos, desired, hitPos, hitSide);
            if (adjusted != null) {
               hitSide = adjusted.hitSide();
               hitPos = adjusted.hitPos();
            }
         }

         return new PlacementManager.PreparedClick(pos, neighbour, hitPos, hitSide, task.hotbarSlot(), task.listener());
      } else {
         BlockHitResult hit = this.prepareAirPlaceHit(pos);
         return hit == null
            ? null
            : new PlacementManager.PreparedClick(pos, hit.getBlockPos(), hit.getLocation(), hit.getDirection(), task.hotbarSlot(), task.listener());
      }
   }

   private void sendBurst(List<PlacementManager.PreparedClick> group, int slot) {
      ClientPacketListener conn = mc.getConnection();
      int heldSlot = KetamineClub.swapManager.serverSlot();
      boolean altSwap = KetamineClub.swapManager.isHotbarBusy() && heldSlot >= 0 && heldSlot <= 8;
      boolean moved = false;
      if (altSwap) {
         moved = heldSlot != slot;
         if (moved) {
            InventoryUtil.swapToHotbarSlot(slot, heldSlot);
         }
      } else if (slot != heldSlot) {
         conn.send(new ServerboundSetCarriedItemPacket(slot));
      }

      try {
         for (PlacementManager.PreparedClick click : group) {
            BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

            try {
               conn.send(
                  new ServerboundUseItemOnPacket(
                     InteractionHand.MAIN_HAND, new BlockHitResult(click.hitPos(), click.hitSide(), click.neighbour(), false), handler.currentSequence()
                  )
               );
            } catch (Throwable var17) {
               if (handler != null) {
                  try {
                     handler.close();
                  } catch (Throwable var16) {
                     var17.addSuppressed(var16);
                  }
               }

               throw var17;
            }

            if (handler != null) {
               handler.close();
            }
         }
      } finally {
         if (altSwap) {
            if (moved) {
               InventoryUtil.swapToHotbarSlot(slot, heldSlot);
            }
         } else if (KetamineClub.swapManager.serverSlot() != heldSlot) {
            conn.send(new ServerboundSetCarriedItemPacket(heldSlot));
         }
      }
   }

   @Nullable
   private PlacementManager.AdjustedHit adjustHitForAxisBlock(BlockPos pos, BlockState desiredState, Vec3 hitPos, Direction hitSide) {
      if (desiredState == null || pos == null || hitPos == null || hitSide == null) {
         return null;
      } else if (!desiredState.hasProperty(BlockStateProperties.AXIS)) {
         return null;
      } else {
         Axis axis = (Axis)desiredState.getValue(BlockStateProperties.AXIS);

         Direction adjustedSide = switch (axis) {
            case X -> Direction.EAST;
            case Z -> Direction.SOUTH;
            case Y -> Direction.UP;
            default -> throw new MatchException(null, null);
         };
         Vec3 adjustedPos = this.getAirPlaceHitPos(pos, adjustedSide);
         return new PlacementManager.AdjustedHit(adjustedPos, adjustedSide);
      }
   }

   private Direction getAirPlaceDirection(BlockPos pos) {
      if (mc.player == null) {
         return Direction.UP;
      } else {
         Vec3 eyePos = mc.player.getEyePosition();
         Direction bestDir = Direction.UP;
         double bestDist = Double.MAX_VALUE;

         for (Direction d : Direction.values()) {
            Vec3 face = this.getAirPlaceHitPos(pos, d);
            double dist = eyePos.distanceToSqr(face);
            if (dist < bestDist) {
               bestDist = dist;
               bestDir = d;
            }
         }

         return bestDir;
      }
   }

   private Vec3 getAirPlaceHitPos(BlockPos pos, @Nullable Direction dir) {
      Vec3 hit = Vec3.atCenterOf(pos);
      if (dir != null) {
         hit = hit.add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
      }

      return hit;
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      this.handleIncomingPacket(event.getPacket());
   }

   private void handleIncomingPacket(Packet<?> packet) {
      if (packet instanceof ClientboundBlockUpdatePacket bup) {
         this.handleBlockUpdate(bup);
      } else if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
         this.handleSectionBlockUpdate(section);
      } else if (packet instanceof ClientboundExplodePacket explosion) {
         this.handleExplosion(explosion);
      } else if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> sub : bundle.subPackets()) {
            this.handleIncomingPacket(sub);
         }
      }
   }

   private void handleBlockUpdate(ClientboundBlockUpdatePacket packet) {
      BlockPos pos = packet.getPos().immutable();
      this.sentAt.remove(pos);
      if (!this.listeners.isEmpty()) {
         this.pendingNotices.offer(new PlacementManager.BlockUpdateNotice(pos, packet.getBlockState().isAir()));
      }
   }

   private void handleSectionBlockUpdate(ClientboundSectionBlocksUpdatePacket packet) {
      packet.runUpdates((pos, state) -> {
         BlockPos immutable = pos.immutable();
         this.sentAt.remove(immutable);
         if (!this.listeners.isEmpty()) {
            this.pendingNotices.offer(new PlacementManager.BlockUpdateNotice(immutable, state.isAir()));
         }
      });
   }

   private void handleExplosion(ClientboundExplodePacket packet) {
      if (!this.listeners.isEmpty()) {
         Vec3 center = packet.center();
         if (center != null && !(packet.radius() <= 0.0F)) {
            this.pendingNotices.offer(new PlacementManager.ExplosionNotice(new Vec3(center.x, center.y, center.z), packet.radius()));
         }
      }
   }

   private void dispatchBlockUpdates() {
      PlacementManager.PlacementNotice notice;
      while ((notice = this.pendingNotices.poll()) != null) {
         for (PlacementManager.PlacementListener listener : this.listeners) {
            if (notice instanceof PlacementManager.BlockUpdateNotice update) {
               listener.onBlockUpdate(update.pos(), update.nowAir());
            } else if (notice instanceof PlacementManager.ExplosionNotice explosion) {
               listener.onExplosion(explosion.center(), explosion.radius());
            }
         }
      }
   }

   @Subscribe(
      priority = 100
   )
   private void onPacketApplied(PacketEvent.ReceivePost event) {
      if (!nullCheck() && !this.immediateListeners.isEmpty()) {
         this.dispatchImmediatePacket(event.getPacket());
      }
   }

   private void dispatchImmediatePacket(Packet<?> packet) {
      if (packet instanceof ClientboundBlockUpdatePacket update) {
         this.notifyImmediateBlock(update.getPos(), update.getBlockState().isAir());
      } else if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
         section.runUpdates((pos, state) -> this.notifyImmediateBlock(pos, state.isAir()));
      } else if (packet instanceof ClientboundRemoveEntitiesPacket removed) {
         IntListIterator var6 = removed.getEntityIds().iterator();

         while (var6.hasNext()) {
            int id = (Integer)var6.next();

            for (PlacementManager.PlacementListener listener : this.immediateListeners) {
               listener.onEntityRemoved(id);
            }
         }
      } else if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> child : bundle.subPackets()) {
            this.dispatchImmediatePacket(child);
         }
      }
   }

   private void notifyImmediateBlock(BlockPos pos, boolean air) {
      BlockPos immutable = pos.immutable();

      for (PlacementManager.PlacementListener listener : this.immediateListeners) {
         listener.onBlockUpdate(immutable, air);
      }
   }

   public boolean canPistonAct(boolean block) {
      if (nullCheck()
         || this.placing
         || this.hasPending()
         || this.usingItemAnyTick()
         || mc.gui.screen() != null
         || mc.player.containerMenu.containerId != 0
         || !InventoryUtil.cursor().isEmpty()) {
         return false;
      } else if (block && !this.placementWindowAvailable(1)) {
         return false;
      } else {
         AutoCrystalModule crystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (!KetamineClub.swapManager.isBlockedByOther("PistonCrystal", 55) || crystal != null && crystal.hasCompletedPlacementSwap()) {
            SurroundModule surround = KetamineClub.moduleManager.getModuleByClass(SurroundModule.class);
            return surround == null || !surround.hasOpenCoreCell();
         } else {
            return false;
         }
      }
   }

   public boolean placePistonBlock(BlockPos pos, @Nullable Direction face, int slot) {
      if (this.canPistonAct(true) && this.canPistonSwap(slot)) {
         this.releaseCompletedCrystalSwap();
         SwapManager.SwapHandle handle = KetamineClub.swapManager.acquireLease("PistonCrystal", 55);
         if (handle == null) {
            return false;
         } else {
            boolean var5;
            try {
               var5 = this.placeDirect(pos, face, slot);
            } finally {
               KetamineClub.swapManager.release(handle);
            }

            return var5;
         }
      } else {
         return false;
      }
   }

   public boolean placePistonCrystal(BlockPos base, int slot, boolean offhand) {
      if (this.canPistonAct(false) && this.canPistonSwap(slot)) {
         this.releaseCompletedCrystalSwap();
         SwapManager.SwapHandle handle = KetamineClub.swapManager.acquireLease("PistonCrystal", 55);
         if (handle == null) {
            return false;
         } else {
            boolean var5;
            try {
               var5 = offhand
                  ? this.placeCrystalOffhand(base, slot, true, false, PlacementManager.SwapSequenceOwner.PISTON_CRYSTAL)
                  : this.placeCrystal(base, slot, true, true, false, PlacementManager.SwapSequenceOwner.PISTON_CRYSTAL);
            } finally {
               KetamineClub.swapManager.release(handle);
            }

            return var5;
         }
      } else {
         return false;
      }
   }

   private boolean canPistonSwap(int slot) {
      return !this.canSwapForCrystal(slot) ? false : !isInventorySlot(slot) || !KetamineClub.swapManager.willClickSlotBeLimited(10);
   }

   private void releaseCompletedCrystalSwap() {
      AutoCrystalModule crystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
      if (crystal != null) {
         crystal.releaseCompletedPlacementSwap();
      }
   }

   public boolean canPlaceBasePlace(BlockPos pos, double range) {
      if (!nullCheck() && !mc.level.isOutsideBuildHeight(pos) && mc.level.getWorldBorder().isWithinBounds(pos) && mc.level.getBlockState(pos).canBeReplaced()) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         if (mine != null && mine.isEnabled() && mine.alreadyBreaking(pos)) {
            return false;
         } else {
            AABB box = new AABB(pos);
            return box.distanceToSqr(mc.player.getEyePosition()) <= range * range
               && !mc.player.getBoundingBox().intersects(box)
               && PlaceUtil.noEntityCollision(pos)
               && mc.level.getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive).isEmpty();
         }
      } else {
         return false;
      }
   }

   public boolean placeBasePlace(BlockPos pos, int slot, double range) {
      if (this.canPlaceBasePlace(pos, range)
         && !this.placing
         && !this.hasPending()
         && !this.usingItemAnyTick()
         && mc.gui.screen() == null
         && mc.player.containerMenu.containerId == 0
         && InventoryUtil.cursor().isEmpty()
         && this.placementWindowAvailable(1)
         && this.canSwapForCrystal(slot)) {
         SurroundModule surround = KetamineClub.moduleManager.getModuleByClass(SurroundModule.class);
         if (surround != null && surround.hasOpenCoreCell()) {
            return false;
         } else {
            this.releaseCompletedCrystalSwap();
            SwapManager.SwapHandle handle = KetamineClub.swapManager.acquireLease("AutoCrystalBasePlace", 55);
            if (handle == null) {
               return false;
            } else {
               boolean var7;
               try {
                  var7 = this.placeDirect(pos, null, slot, false, true);
               } finally {
                  KetamineClub.swapManager.release(handle);
               }

               return var7;
            }
         }
      } else {
         return false;
      }
   }

   public boolean placeSurroundRepair(BlockPos pos, int slot, Set<Integer> attackedIds) {
      if (!nullCheck() && !this.placing && mc.player.containerMenu.containerId == 0 && InventoryUtil.cursor().isEmpty() && PlaceUtil.noEntityCollision(pos)) {
         if (this.repairPlacementWindowAvailable(1) && this.canSwapForCrystal(slot)) {
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos), Entity::isAlive)) {
               if (!attackedIds.contains(crystal.getId())) {
                  return false;
               }
            }

            SwapManager.SwapHandle handle = KetamineClub.swapManager.acquireLease("SurroundRepair", 130);
            if (handle == null) {
               return false;
            } else {
               boolean var10;
               try {
                  var10 = this.placeDirect(pos, null, slot, true, false, true);
               } finally {
                  KetamineClub.swapManager.release(handle);
               }

               return var10;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public String surroundRepairBlocker(BlockPos pos, int slot, Set<Integer> attackedIds) {
      if (nullCheck()) {
         return "no-world";
      } else if (this.placing) {
         return "placement-in-progress";
      } else if (mc.player.containerMenu.containerId != 0) {
         return "container-open";
      } else if (!InventoryUtil.cursor().isEmpty()) {
         return "cursor-occupied";
      } else if (mc.level.isOutsideBuildHeight(pos)) {
         return "outside-build-height";
      } else if (!mc.level.getBlockState(pos).canBeReplaced()) {
         return "cell-solid";
      } else if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
         return "own-hitbox";
      } else if (!PlaceUtil.noEntityCollision(pos)) {
         return "other-entity";
      } else if (this.usingItemAnyTick()) {
         return "using-item-current-or-previous-tick";
      } else if (!this.repairPlacementWindowAvailable(1)) {
         return "repair-window-full";
      } else if (!this.canSwapForBlock(slot)) {
         return "slot-or-inventory-swap-unavailable";
      } else if (Vec3.atCenterOf(pos).distanceTo(mc.player.getEyePosition()) > 5.154) {
         return "outside-place-range";
      } else {
         for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos), Entity::isAlive)) {
            if (!attackedIds.contains(crystal.getId())) {
               return "unattacked-crystal:" + crystal.getId();
            }
         }

         if (KetamineClub.swapManager.isBlocked(130)) {
            return "higher-priority-swap";
         } else {
            return this.isOnCooldown(pos) ? "placement-cooldown" : "placement-validation";
         }
      }
   }

   public boolean placeDirect(BlockPos pos, @Nullable Direction face, int hotbarSlot) {
      return this.placeDirect(pos, face, hotbarSlot, false);
   }

   public boolean placeDirectInEntity(BlockPos pos, int hotbarSlot) {
      return this.placeDirect(pos, null, hotbarSlot, true);
   }

   private boolean placeDirect(BlockPos pos, @Nullable Direction face, int hotbarSlot, boolean allowEntityCollision) {
      return this.placeDirect(pos, face, hotbarSlot, allowEntityCollision, false);
   }

   private boolean placeDirect(BlockPos pos, @Nullable Direction face, int hotbarSlot, boolean allowEntityCollision, boolean extendedReach) {
      return this.placeDirect(pos, face, hotbarSlot, allowEntityCollision, extendedReach, false);
   }

   private boolean placeDirect(
      BlockPos pos, @Nullable Direction face, int hotbarSlot, boolean allowEntityCollision, boolean extendedReach, boolean urgentRepair
   ) {
      if (nullCheck()) {
         return false;
      } else if (!this.canSwapForBlock(hotbarSlot)) {
         return false;
      } else {
         this.prunePlacementWindow();
         if (this.usingItemAnyTick()) {
            return false;
         } else {
            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            if (offhand != null && offhand.shouldDeferForEat()) {
               return false;
            } else if (urgentRepair ? this.repairPlacementWindowAvailable(1) : this.placementWindowAvailable(1)) {
               if (this.isOnCooldown(pos)) {
                  return false;
               } else {
                  PlacementManager.PreparedClick click = this.prepareClick(
                     new PlacementManager.PlacementTask(pos, face, hotbarSlot, extendedReach, allowEntityCollision, urgentRepair ? 200 : 0, 0L, null)
                  );
                  if (click == null) {
                     return false;
                  } else {
                     boolean sent = false;
                     this.placing = true;

                     try {
                        int useSlot = this.altSwapIn(hotbarSlot);
                        if (useSlot >= 0) {
                           try {
                              this.sendBurst(List.of(click), useSlot);
                              sent = true;
                           } finally {
                              this.altSwapOut(hotbarSlot, useSlot);
                           }
                        }
                     } finally {
                        this.placing = false;
                     }

                     if (!sent) {
                        return false;
                     } else {
                        long stamp = System.currentTimeMillis();
                        this.recordBlockPlacement(click, stamp);
                        return true;
                     }
                  }
               }
            } else {
               return false;
            }
         }
      }
   }

   public boolean placeCrystal(BlockPos base, int hotbarSlot) {
      return this.placeCrystal(base, hotbarSlot, false, false, false);
   }

   public boolean placeCrystal(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystal(base, hotbarSlot, trustBase, false, false);
   }

   public boolean placeCrystalAutoCrystal(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystal(base, hotbarSlot, trustBase, true, false);
   }

   public boolean placeCrystalAutoCrystalReactive(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystal(base, hotbarSlot, trustBase, true, true);
   }

   private boolean placeCrystal(BlockPos base, int hotbarSlot, boolean trustBase, boolean swing, boolean allowExistingCrystal) {
      return this.placeCrystal(base, hotbarSlot, trustBase, swing, allowExistingCrystal, PlacementManager.SwapSequenceOwner.OTHER);
   }

   private boolean placeCrystal(
      BlockPos base, int hotbarSlot, boolean trustBase, boolean swing, boolean allowExistingCrystal, PlacementManager.SwapSequenceOwner owner
   ) {
      if (!nullCheck() && this.canSwapForCrystal(hotbarSlot)) {
         boolean altSwap = isInventorySlot(hotbarSlot);
         if (altSwap) {
            if (this.usingItemAnyTick()) {
               return false;
            }

            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            if (offhand != null && offhand.shouldDeferForEat()) {
               return false;
            }

            if (this.hasPending()) {
               this.flushQueue();
            }

            if (!this.crystalSequenceAvailable(owner, allowExistingCrystal)) {
               return false;
            }
         }

         BlockHitResult hit = this.computeCrystalHit(base, trustBase, allowExistingCrystal);
         if (hit == null) {
            return false;
         } else {
            int useSlot = this.altSwapIn(hotbarSlot);
            if (useSlot < 0) {
               return false;
            } else {
               try {
                  ClientPacketListener conn = mc.getConnection();
                  int originalSlot = KetamineClub.swapManager.serverSlot();
                  boolean needSlotSwap = useSlot != originalSlot;
                  if (needSlotSwap) {
                     conn.send(new ServerboundSetCarriedItemPacket(useSlot));
                  }

                  BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

                  try {
                     conn.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, handler.currentSequence()));
                  } catch (Throwable var21) {
                     if (handler != null) {
                        try {
                           handler.close();
                        } catch (Throwable var20) {
                           var21.addSuppressed(var20);
                        }
                     }

                     throw var21;
                  }

                  if (handler != null) {
                     handler.close();
                  }

                  if (swing) {
                     conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                  }

                  if (needSlotSwap) {
                     conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
                  }
               } finally {
                  this.altSwapOut(hotbarSlot, useSlot);
               }

               if (altSwap) {
                  this.markSwapSequenceUsed(owner);
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean placeCrystalOffhand(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystalOffhand(base, hotbarSlot, trustBase, false, PlacementManager.SwapSequenceOwner.OTHER);
   }

   public boolean placeCrystalOffhandAutoCrystal(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystalOffhand(base, hotbarSlot, trustBase, false, PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL);
   }

   public boolean placeCrystalOffhandReactive(BlockPos base, int hotbarSlot, boolean trustBase) {
      return this.placeCrystalOffhand(base, hotbarSlot, trustBase, true, PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL);
   }

   private boolean placeCrystalOffhand(BlockPos base, int hotbarSlot, boolean trustBase, boolean reactive, PlacementManager.SwapSequenceOwner owner) {
      if (!nullCheck() && this.canSwapForCrystal(hotbarSlot)) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand != null && offhand.shouldDeferForEat()) {
            return false;
         } else {
            if (!reactive) {
               if (this.hasPending()) {
                  this.flushQueue();
               }

               if (!this.crystalSequenceAvailable(owner, false)) {
                  return false;
               }
            } else if (!this.crystalSequenceAvailable(owner, true)) {
               return false;
            }

            BlockHitResult hit = this.computeCrystalHit(base, trustBase, reactive);
            if (hit == null) {
               return false;
            } else {
               int useSlot = this.altSwapIn(hotbarSlot);
               if (useSlot < 0) {
                  return false;
               } else {
                  try {
                     ClientPacketListener conn = mc.getConnection();
                     int originalSlot = KetamineClub.swapManager.serverSlot();
                     boolean needSlotSwap = useSlot != originalSlot;
                     if (needSlotSwap) {
                        conn.send(new ServerboundSetCarriedItemPacket(useSlot));
                     }

                     conn.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));

                     try {
                        BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

                        try {
                           conn.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hit, handler.currentSequence()));
                        } catch (Throwable var26) {
                           if (handler != null) {
                              try {
                                 handler.close();
                              } catch (Throwable var25) {
                                 var26.addSuppressed(var25);
                              }
                           }

                           throw var26;
                        }

                        if (handler != null) {
                           handler.close();
                        }

                        if (owner == PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL) {
                           conn.send(new ServerboundSwingPacket(InteractionHand.OFF_HAND));
                        }
                     } finally {
                        conn.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                        if (needSlotSwap) {
                           conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
                        }
                     }
                  } finally {
                     this.altSwapOut(hotbarSlot, useSlot);
                  }

                  this.markSwapSequenceUsed(owner);
                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   public boolean breakThenPlaceCrystalOffhand(int pickaxeSlot, Runnable breakBurst, int crystalSlot, BlockPos crystalBase, boolean trustBase) {
      if (nullCheck()) {
         return false;
      } else if (mc.player.containerMenu.containerId != 0) {
         return false;
      } else if (!InventoryUtil.cursor().isEmpty()) {
         return false;
      } else if (pickaxeSlot < 0 || pickaxeSlot > 8 || crystalSlot < 0 || crystalSlot > 8) {
         return false;
      } else if (pickaxeSlot == crystalSlot) {
         return false;
      } else if (!mc.player.getInventory().getItem(crystalSlot).is(Items.END_CRYSTAL)) {
         return false;
      } else {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand != null && offhand.shouldDeferForEat()) {
            return false;
         } else if (KetamineClub.swapManager.isHotbarBusy()) {
            return false;
         } else {
            if (this.hasPending()) {
               this.flushQueue();
            }

            if (!this.swapSequenceAvailable()) {
               return false;
            } else {
               BlockHitResult hit = this.computeCrystalHit(crystalBase, trustBase, false);
               if (hit == null) {
                  return false;
               } else {
                  ClientPacketListener conn = mc.getConnection();
                  if (conn == null) {
                     return false;
                  } else {
                     int originalSlot = KetamineClub.swapManager.serverSlot();
                     boolean needSlotSwap = pickaxeSlot != originalSlot;
                     InventoryUtil.swapToOffhand(crystalSlot);
                     if (needSlotSwap) {
                        conn.send(new ServerboundSetCarriedItemPacket(pickaxeSlot));
                     }

                     try {
                        if (breakBurst != null) {
                           breakBurst.run();
                        }

                        BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

                        try {
                           conn.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hit, handler.currentSequence()));
                        } catch (Throwable var19) {
                           if (handler != null) {
                              try {
                                 handler.close();
                              } catch (Throwable var18) {
                                 var19.addSuppressed(var18);
                              }
                           }

                           throw var19;
                        }

                        if (handler != null) {
                           handler.close();
                        }
                     } finally {
                        if (needSlotSwap) {
                           conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
                        }

                        InventoryUtil.swapToOffhand(crystalSlot);
                     }

                     this.markSwapSequenceUsed(PlacementManager.SwapSequenceOwner.OTHER);
                     return true;
                  }
               }
            }
         }
      }
   }

   public boolean mineChainCrystalOffhand(
      int pickaxeSlot,
      @Nullable Runnable stopBurst,
      int crystalEntityId,
      int crystalSlot,
      BlockPos crystalBase,
      boolean trustBase,
      Consumer<String> packetTrace
   ) {
      if (nullCheck()) {
         return false;
      } else if (packetTrace == null || crystalBase == null) {
         return false;
      } else if (mc.player.containerMenu.containerId != 0 || !InventoryUtil.cursor().isEmpty()) {
         return false;
      } else if (crystalSlot >= 0 && crystalSlot <= 8) {
         if (stopBurst == null || pickaxeSlot >= 0 && pickaxeSlot <= 8 && pickaxeSlot != crystalSlot) {
            if (!mc.player.getInventory().getItem(crystalSlot).is(Items.END_CRYSTAL)) {
               return false;
            } else {
               OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
               if (offhand != null && offhand.shouldDeferForEat()) {
                  return false;
               } else {
                  this.prunePlacementWindow();
                  if (!this.placementWindowAvailable(1)) {
                     return false;
                  } else if (this.hasPending()) {
                     return false;
                  } else if (!this.swapSequenceAvailable()) {
                     return false;
                  } else {
                     this.prunePlacementWindow();
                     if (!this.placementWindowAvailable(1)) {
                        return false;
                     } else if (KetamineClub.swapManager.willClickSlotBeLimited(2)) {
                        return false;
                     } else {
                        BlockHitResult hit = this.computeCrystalHit(crystalBase, trustBase, false, true, crystalEntityId);
                        if (hit == null) {
                           return false;
                        } else {
                           ClientPacketListener conn = mc.getConnection();
                           if (conn == null) {
                              return false;
                           } else {
                              int originalSlot = KetamineClub.swapManager.serverSlot();
                              boolean needToolSwap = stopBurst != null && pickaxeSlot != originalSlot;
                              SwapManager.SwapHandle swapHandle = null;
                              if (stopBurst == null) {
                                 if (KetamineClub.swapManager.isHotbarBusy()) {
                                    return false;
                                 }

                                 swapHandle = KetamineClub.swapManager.acquire("MineCrystalChain", 122);
                                 if (swapHandle == null) {
                                    return false;
                                 }
                              }

                              try {
                                 InventoryUtil.swapToOffhand(crystalSlot);
                                 if (needToolSwap) {
                                    conn.send(new ServerboundSetCarriedItemPacket(pickaxeSlot));
                                 }

                                 try {
                                    if (stopBurst != null) {
                                       stopBurst.run();
                                    }

                                    packetTrace.accept("ATTACK_SENT entity=" + crystalEntityId + " hand=MAIN_HAND");
                                    conn.send(new ServerboundAttackPacket(crystalEntityId));
                                    packetTrace.accept("INNER_PLACE_SENT hand=OFF_HAND crystalSlot=" + crystalSlot);
                                    BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level)
                                       .ketamineclub$getBlockStatePredictionHandler()
                                       .startPredicting();

                                    try {
                                       conn.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, hit, handler.currentSequence()));
                                    } catch (Throwable var28) {
                                       if (handler != null) {
                                          try {
                                             handler.close();
                                          } catch (Throwable var27) {
                                             var28.addSuppressed(var27);
                                          }
                                       }

                                       throw var28;
                                    }

                                    if (handler != null) {
                                       handler.close();
                                    }
                                 } finally {
                                    if (needToolSwap) {
                                       conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
                                    }

                                    InventoryUtil.swapToOffhand(crystalSlot);
                                 }
                              } finally {
                                 if (swapHandle != null) {
                                    KetamineClub.swapManager.release(swapHandle);
                                 }
                              }

                              long stamp = System.currentTimeMillis();
                              BlockPos airPos = crystalBase.above().immutable();
                              this.sentAt.put(airPos, stamp);
                              this.placementWindow.record(stamp);
                              this.markSwapSequenceUsed(PlacementManager.SwapSequenceOwner.AUTO_CRYSTAL);
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Nullable
   private BlockHitResult computeCrystalHit(BlockPos base, boolean trustBase, boolean allowExistingCrystal) {
      return this.computeCrystalHit(base, trustBase, allowExistingCrystal, false, -1);
   }

   @Nullable
   private BlockHitResult computeCrystalHit(BlockPos base, boolean trustBase, boolean allowExistingCrystal, boolean anticipatedAir, int ignoredCrystalId) {
      if (!trustBase) {
         BlockState baseState = mc.level.getBlockState(base);
         if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return null;
         }
      }

      BlockPos airPos = base.above();
      BlockState airState = mc.level.getBlockState(airPos);
      if (!anticipatedAir && !airState.isAir()) {
         return null;
      } else {
         Vec3 eye = mc.player.getEyePosition(1.0F);
         AABB checkBox = new AABB(airPos.getX(), airPos.getY(), airPos.getZ(), airPos.getX() + 1.0, airPos.getY() + 2.0, airPos.getZ() + 1.0);

         for (Entity e : mc.level.getEntities(null, checkBox)) {
            if ((!(e instanceof EndCrystal) || e.getId() != ignoredCrystalId)
               && (!anticipatedAir || !(e instanceof ItemEntity item) || !item.getItem().is(Items.OBSIDIAN) && !item.getItem().is(Items.BEDROCK))
               && (!allowExistingCrystal || !(e instanceof EndCrystal))) {
               return null;
            }
         }

         Vec3 baseCenter = Vec3.atCenterOf(base);
         double dx = eye.x - baseCenter.x;
         double dy = eye.y - baseCenter.y;
         double dz = eye.z - baseCenter.z;
         double absX = Math.abs(dx);
         double absY = Math.abs(dy);
         double absZ = Math.abs(dz);
         Direction clickFace;
         if (absY >= absX && absY >= absZ) {
            clickFace = dy > 0.0 ? Direction.UP : Direction.DOWN;
         } else if (absX >= absZ) {
            clickFace = dx > 0.0 ? Direction.EAST : Direction.WEST;
         } else {
            clickFace = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
         }

         Vec3 playerPos = mc.player.position();
         double offX = Math.max(0.2, Math.min(0.8, playerPos.x - Math.floor(playerPos.x)));
         double offY = Math.max(0.2, Math.min(0.8, playerPos.y - Math.floor(playerPos.y)));
         double offZ = Math.max(0.2, Math.min(0.8, playerPos.z - Math.floor(playerPos.z)));
         Vec3 hitVec = baseCenter;
         switch (clickFace) {
            case UP:
            case DOWN:
               hitVec = baseCenter.add(offX - 0.5, clickFace == Direction.UP ? 0.5 : -0.5, offZ - 0.5);
               break;
            case NORTH:
            case SOUTH:
               hitVec = baseCenter.add(offX - 0.5, offY - 0.5, clickFace == Direction.SOUTH ? 0.5 : -0.5);
               break;
            case EAST:
            case WEST:
               hitVec = baseCenter.add(clickFace == Direction.EAST ? 0.5 : -0.5, offY - 0.5, offZ - 0.5);
         }

         float[] angles = MathUtil.calcAngle(eye, hitVec);
         AABB baseBox = new AABB(base);
         boolean insideBox = baseBox.contains(eye);
         if (!insideBox) {
            Vec3 look = this.getLookVector(angles[0], angles[1]);
            Vec3 reachEnd = eye.add(look.scale(6.0));
            if (baseBox.clip(eye, reachEnd).isEmpty()) {
               return null;
            }
         }

         return new BlockHitResult(hitVec, clickFace, base, false);
      }
   }

   public boolean placeFireworksAlt(List<BlockPos> poses, Direction face, int hotbarSlot) {
      if (nullCheck()) {
         return false;
      } else if (poses.isEmpty()) {
         return false;
      } else if (mc.player.containerMenu.containerId != 0) {
         return false;
      } else if (hotbarSlot >= 0 && hotbarSlot <= 35) {
         ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
         if (stack.isEmpty()) {
            return false;
         } else {
            ClientPacketListener conn = mc.getConnection();
            if (conn == null) {
               return false;
            } else {
               int containerSlot = containerSlotOf(hotbarSlot);
               boolean swapped = hotbarSlot != InventoryUtil.selected();
               if (swapped) {
                  InventoryUtil.click(containerSlot, InventoryUtil.selected(), ContainerInput.SWAP);
               }

               try {
                  for (BlockPos pos : poses) {
                     BlockPos neighbour = pos.relative(face);
                     Vec3 hitPos = this.getFireworkHitPos(pos, face);
                     Direction hitSide = face.getOpposite();
                     BlockHitResult hit = new BlockHitResult(hitPos, hitSide, neighbour, false);
                     BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

                     try {
                        conn.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, handler.currentSequence()));
                     } catch (Throwable var22) {
                        if (handler != null) {
                           try {
                              handler.close();
                           } catch (Throwable var21) {
                              var22.addSuppressed(var21);
                           }
                        }

                        throw var22;
                     }

                     if (handler != null) {
                        handler.close();
                     }
                  }
               } finally {
                  if (swapped) {
                     InventoryUtil.click(containerSlot, InventoryUtil.selected(), ContainerInput.SWAP);
                  }
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   private Vec3 getFireworkHitPos(BlockPos pos, Direction face) {
      if (face == Direction.DOWN && mc.player != null) {
         double lean = 0.7;
         Vec3 player = mc.player.position();
         double x = pos.getX() + (player.x >= pos.getX() + 0.5 ? 0.30000000000000004 : 0.7);
         double z = pos.getZ() + (player.z >= pos.getZ() + 0.5 ? 0.30000000000000004 : 0.7);
         return new Vec3(x, pos.getY(), z);
      } else {
         return Vec3.atCenterOf(pos).relative(face, 0.5);
      }
   }

   private Vec3 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3(g * h, i, f * h);
   }

   private record AdjustedHit(Vec3 hitPos, Direction hitSide) {
   }

   private record BlockUpdateNotice(BlockPos pos, boolean nowAir) implements PlacementManager.PlacementNotice {
   }

   private record ExplosionNotice(Vec3 center, float radius) implements PlacementManager.PlacementNotice {
   }

   public interface PlacementListener {
      void onBlockUpdate(BlockPos var1, boolean var2);

      default void onExplosion(Vec3 center, float radius) {
      }

      default void onEntityRemoved(int entityId) {
      }

      default boolean validateQueuedPlacement(BlockPos pos) {
         return true;
      }

      default int queuedPlacementPriority(BlockPos pos, int priority) {
         return priority;
      }

      default void onPlacementSent(BlockPos pos, long sentAt) {
      }

      default void onPlacementDiscarded(BlockPos pos) {
      }
   }

   private interface PlacementNotice {
   }

   record PlacementTask(
      BlockPos pos,
      @Nullable Direction face,
      int hotbarSlot,
      boolean extendedReach,
      boolean allowEntityCollision,
      int priority,
      long order,
      @Nullable PlacementManager.PlacementListener listener
   ) implements Comparable<PlacementManager.PlacementTask> {
      PlacementTask(
         BlockPos pos,
         @Nullable Direction face,
         int hotbarSlot,
         boolean extendedReach,
         boolean allowEntityCollision,
         int priority,
         long order,
         @Nullable PlacementManager.PlacementListener listener
      ) {
         pos = pos.immutable();
         this.pos = pos;
         this.face = face;
         this.hotbarSlot = hotbarSlot;
         this.extendedReach = extendedReach;
         this.allowEntityCollision = allowEntityCollision;
         this.priority = priority;
         this.order = order;
         this.listener = listener;
      }

      PlacementTask(BlockPos pos, @Nullable Direction face, int slot, boolean extended, boolean collision) {
         this(pos, face, slot, extended, collision, 0, 0L, null);
      }

      public int compareTo(PlacementManager.PlacementTask other) {
         int rank = Integer.compare(other.priority, this.priority);
         return rank != 0 ? rank : Long.compare(this.order, other.order);
      }
   }

   private record PreparedClick(
      BlockPos pos, BlockPos neighbour, Vec3 hitPos, Direction hitSide, int hotbarSlot, @Nullable PlacementManager.PlacementListener listener
   ) {
   }

   private static enum SwapSequenceOwner {
      OTHER,
      AUTO_CRYSTAL,
      PISTON_CRYSTAL;

      private SwapSequenceOwner() {
      }
   }
}
