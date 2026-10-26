package we.ketamine.club.features.modules.movement;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;

public class NoSlowModule extends Module {
   public final Setting<Boolean> crawl = this.bool("Crawl", true);
   public final Setting<Boolean> eat = this.bool("Eat", true);
   public final Setting<Boolean> bypass = this.register(
         new Setting<>("Bypass", false, "Alternates slowed and fast item-use ticks for Grim before PR #2864. Partial speed; patched checks catch it.")
      )
      .setVisibility(value -> this.eat.getValue());
   public final Setting<Boolean> webs = this.bool("Webs", false);
   private static final int SPOOF_DELAY_MOVES = 1;
   private static final int SPOOF_PACKET_COUNT = 37;
   private static final float MIN_EAT_TPS = 15.0F;
   private int movesUntilSlotSpoof = 0;
   private int slotSpoofsRemaining = 0;
   private boolean useSessionActive = false;
   private boolean ignoreNextMovementPacket = false;
   private boolean wasConsuming = false;
   private final NoSlowBypassState bypassState = new NoSlowBypassState();

   public NoSlowModule() {
      super("NoSlow", "remove slowdown", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.resetSpoof();
      this.bypassState.reset();
      this.wasConsuming = false;
   }

   @Override
   public void onDisable() {
      this.resetSpoof();
      this.bypassState.reset();
      this.wasConsuming = false;
   }

   @Override
   public void onTick() {
      this.sendWebBypassPackets();
      if (!this.bypass.getValue()) {
         this.bypassState.reset();
         boolean consuming = this.isConsumingMainHandItem();
         if (this.wasConsuming && !consuming) {
            this.resetSpoof();
         }

         this.wasConsuming = consuming;
         if (!consuming && !this.isHoldingUseOnConsumable()) {
            this.resetSpoof();
         }
      } else {
         this.resetSpoof();
         this.wasConsuming = false;
         if (nullCheck() || !this.eatEnabled() || !mc.player.isUsingItem()) {
            this.bypassState.reset();
         }
      }
   }

   private void sendWebBypassPackets() {
      if (this.webs.getValue() && !nullCheck() && mc.getConnection() != null) {
         SpeedMineController speedMine = KetamineClub.moduleManager.getSpeedMine();
         if (speedMine == null || !speedMine.hasActiveMining()) {
            AABB box = mc.player.getBoundingBox();

            for (BlockPos pos : BlockPos.betweenClosed(
               Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ)
            )) {
               if (mc.level.getBlockState(pos).is(Blocks.COBWEB)) {
                  mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos.immutable(), Direction.DOWN));
               }
            }
         }
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (this.isEnabled() && this.eatEnabled()) {
         if (this.bypass.getValue()) {
            this.resetSpoof();
         } else {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundUseItemPacket) {
               this.useSessionActive = true;
               this.movesUntilSlotSpoof = 1;
               this.slotSpoofsRemaining = 37;
               this.ignoreNextMovementPacket = true;
            } else {
               if (packet instanceof ServerboundMovePlayerPacket move) {
                  if (nullCheck() || !this.shouldSpoofActive()) {
                     return;
                  }

                  if (!move.hasRotation() && move.hasPosition()) {
                     event.cancel();
                     mc.getConnection()
                        .send(
                           new PosRot(
                              mc.player.getX(),
                              mc.player.getY(),
                              mc.player.getZ(),
                              mc.player.getYRot(),
                              mc.player.getXRot(),
                              move.isOnGround(),
                              mc.player.horizontalCollision
                           )
                        );
                     return;
                  }

                  this.handleMovementPacketSend(move);
               }
            }
         }
      }
   }

   private void handleMovementPacketSend(ServerboundMovePlayerPacket packet) {
      if (this.shouldSpoofActive() && packet.hasPosition() && packet.hasRotation()) {
         if (this.ignoreNextMovementPacket) {
            this.ignoreNextMovementPacket = false;
         } else if (this.movesUntilSlotSpoof > 0) {
            this.movesUntilSlotSpoof--;
         } else if (this.slotSpoofsRemaining > 0) {
            if (mc.player != null && mc.getConnection() != null) {
               int slot = mc.player.getInventory().getSelectedSlot();
               if (slot >= 0 && slot <= 8) {
                  mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                  this.slotSpoofsRemaining--;
               }
            }
         }
      }
   }

   private boolean eatEnabled() {
      return this.eat.getValue() && KetamineClub.tpsCounterService.getLatestTPS() > 15.0F;
   }

   private boolean shouldSpoofActive() {
      return this.eatEnabled() && (this.isConsumingMainHandItem() || this.movesUntilSlotSpoof > 0 || this.slotSpoofsRemaining > 0 || this.useSessionActive);
   }

   private void resetSpoof() {
      this.movesUntilSlotSpoof = 0;
      this.slotSpoofsRemaining = 0;
      this.useSessionActive = false;
      this.ignoreNextMovementPacket = false;
   }

   private boolean isConsumingMainHandItem() {
      if (mc.player == null) {
         return false;
      } else {
         return mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND ? this.isConsumable(mc.player.getUseItem()) : false;
      }
   }

   private boolean isHoldingUseOnConsumable() {
      return mc.player != null && mc.options.keyUse.isDown() ? this.isConsumable(mc.player.getMainHandItem()) : false;
   }

   private boolean isConsumable(ItemStack stack) {
      return !stack.isEmpty() && stack.has(DataComponents.CONSUMABLE);
   }

   private boolean bypassConsumeSlow() {
      if (!nullCheck() && this.eatEnabled() && mc.player.isUsingItem() && !mc.player.isDeadOrDying()) {
         InteractionHand hand = mc.player.getUsedItemHand();
         int slot = hand == InteractionHand.MAIN_HAND ? mc.player.getInventory().getSelectedSlot() : -1;
         NoSlowModule.UseSession session = new NoSlowModule.UseSession(mc.level, mc.player, hand, slot, mc.player.getUseItem().getItem());
         return this.bypassState.cancelSlowdown(session, mc.player.tickCount, mc.player.getUseItemRemainingTicks());
      } else {
         this.bypassState.reset();
         return false;
      }
   }

   public static boolean shouldCancelConsumeSlow() {
      NoSlowModule module = getInstance();
      if (module == null || !module.isEnabled()) {
         return false;
      } else if (module.bypass.getValue()) {
         return module.bypassConsumeSlow();
      } else {
         module.bypassState.reset();
         return module.shouldSpoofActive();
      }
   }

   public static boolean shouldCancelWebSlow(BlockState state) {
      return state.is(Blocks.COBWEB) && isActive(module -> module.webs.getValue());
   }

   public static NoSlowModule getInstance() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(NoSlowModule.class);
   }

   public static boolean isActive(Predicate<NoSlowModule> predicate) {
      NoSlowModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }

   private record UseSession(Level level, Player player, InteractionHand hand, int slot, Item item) {
   }
}
