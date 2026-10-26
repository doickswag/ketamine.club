package we.ketamine.club.pingbypass;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.AttackBlockEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.traits.Util;

public final class RemotePlayerController {
   private final Minecraft minecraft;
   private final Consumer<Packet<?>> sendUpstream;
   private final Consumer<Packet<?>> sendViewer;
   private final PredictionSequenceMap predictions = new PredictionSequenceMap();
   private final MovementAuthority movementAuthority = new MovementAuthority();
   private Consumer<JsonObject> motionSender;
   private boolean attackHeld;
   private boolean useHeld;
   private LocalPlayer player;
   private ClientLevel level;
   private ClientInput originalInput;
   private final RemotePlayerController.RemoteInput remoteInput = new RemotePlayerController.RemoteInput();
   private Vec3 position;
   private Vec3 velocity = Vec3.ZERO;
   private float yaw;
   private float pitch;
   private boolean onGround;
   private boolean horizontalCollision;
   private boolean sprinting;
   private int ticks;
   private int movementTick;
   private int awaitingTeleport = -1;
   private Entity controlledVehicle;
   private Vec3 vehiclePosition;
   private float vehicleYaw;
   private float vehiclePitch;
   private boolean vehicleOnGround;
   private int replayViewerSequence = -1;
   private Class<?> replayPacketClass;
   private boolean replaySent;
   private RemotePlayerController.MenuSnapshot inventorySnapshot;
   private RemotePlayerController.MenuSnapshot containerSnapshot;
   private int mirroredSlot = -1;
   private boolean remotePhysicsRequired;

   public RemotePlayerController(Minecraft minecraft, Consumer<Packet<?>> sendUpstream, Consumer<Packet<?>> sendViewer) {
      this.minecraft = Objects.requireNonNull(minecraft);
      this.sendUpstream = Objects.requireNonNull(sendUpstream);
      this.sendViewer = Objects.requireNonNull(sendViewer);
   }

   public void setMotionSender(Consumer<JsonObject> sender) {
      this.requireClientThread();
      this.motionSender = Objects.requireNonNull(sender);
   }

   public void setRemotePhysicsRequired(boolean required) {
      this.requireClientThread();
      this.remotePhysicsRequired = required;
   }

   public void acknowledgeMotion(int generation) {
      this.requireClientThread();
      this.movementAuthority.acknowledge(generation);
   }

   public void setButtons(boolean attack, boolean use) {
      this.requireClientThread();
      this.attackHeld = attack;
      this.useHeld = use;
      this.applyInputKeys();
   }

   public boolean suppressVanillaMovement() {
      return !this.movementAuthority.worker();
   }

   public boolean beforeVanillaMovement() {
      this.requireClientThread();
      if (!this.suppressVanillaMovement()) {
         return false;
      } else {
         RotationManager rotations = KetamineClub.rotationManager;
         if (this.player != null && rotations != null && (rotations.isRotating() || rotations.isSilentSyncRequired())) {
            float outYaw = rotations.isRotating() ? rotations.getRotationYaw() : this.player.getYRot();
            float outPitch = rotations.isRotating() ? rotations.getRotationPitch() : this.player.getXRot();
            rotations.setServerDeltaYaw(outYaw - rotations.getServerYaw());
            rotations.setServerYaw(outYaw);
            rotations.setServerPitch(outPitch);
            this.sendUpstream.accept(new Rot(outYaw, outPitch, this.player.onGround(), this.player.horizontalCollision));
            rotations.setSilentSyncRequired(false);
         }

         return true;
      }
   }

   public void accept(Packet<?> packet) {
      this.requireClientThread();
      if (packet.type().flow() == PacketFlow.SERVERBOUND && !packet.isTerminal()) {
         if (!workerOwns(packet)) {
            if (packet instanceof ServerboundAcceptTeleportationPacket teleport) {
               if (teleport.getId() == this.awaitingTeleport) {
                  this.awaitingTeleport = -1;
               }
            } else if (viewerOwnsChat(packet) || packet instanceof ServerboundResourcePackPacket) {
               this.sendUpstream.accept(packet);
            } else if (packet instanceof ServerboundClientCommandPacket command) {
               if (command.getAction() == Action.PERFORM_RESPAWN) {
                  this.sendUpstream.accept(packet);
               }
            } else if (this.trackPlayer()) {
               if (!this.player.isDeadOrDying()) {
                  if (packet instanceof ServerboundMovePlayerPacket move) {
                     if (this.awaitingTeleport >= 0) {
                        return;
                     }

                     Vec3 next = new Vec3(move.getX(this.position.x), move.getY(this.position.y), move.getZ(this.position.z));
                     float nextYaw = move.getYRot(this.yaw);
                     float nextPitch = move.getXRot(this.pitch);
                     if (!finite(next) || !Float.isFinite(nextYaw) || !Float.isFinite(nextPitch)) {
                        return;
                     }

                     this.yaw = nextYaw;
                     this.pitch = nextPitch;
                     if (this.movementAuthority.worker()) {
                        this.player.setYRot(this.yaw);
                        this.player.setXRot(this.pitch);
                        return;
                     }

                     if (!this.movementAuthority.acceptsViewerPosition()) {
                        return;
                     }

                     this.velocity = move.hasPosition() ? next.subtract(this.position) : Vec3.ZERO;
                     this.position = next;
                     this.onGround = move.isOnGround();
                     this.horizontalCollision = move.horizontalCollision();
                     this.movementTick = this.ticks;
                     this.restoreMovement(true);
                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundPlayerInputPacket input) {
                     this.remoteInput.update(input.input());
                     this.player.setShiftKeyDown(input.input().shift());
                     this.applyInputKeys();
                     if (!this.movementAuthority.worker()) {
                        this.sendUpstream.accept(packet);
                     }
                  } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
                     if (!Inventory.isHotbarSlot(carried.getSlot())) {
                        return;
                     }

                     this.player.getInventory().setSelectedSlot(carried.getSlot());
                     this.mirroredSlot = carried.getSlot();
                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundPlayerCommandPacket command) {
                     if (command.getId() != this.player.getId()) {
                        return;
                     }

                     if (this.movementAuthority.worker()
                        && (
                           command.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_SPRINTING
                              || command.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
                              || command.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
                        )) {
                        return;
                     }

                     switch (command.getAction()) {
                        case START_SPRINTING:
                           this.sprinting = true;
                           this.player.setSprinting(true);
                           break;
                        case STOP_SPRINTING:
                           this.sprinting = false;
                           this.player.setSprinting(false);
                           break;
                        case START_FALL_FLYING:
                           this.player.startFallFlying();
                           break;
                        case STOP_SLEEPING:
                           this.player.stopSleeping();
                     }

                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundPlayerAbilitiesPacket abilities) {
                     if (this.movementAuthority.worker()) {
                        return;
                     }

                     this.player.getAbilities().flying = abilities.isFlying() && this.player.getAbilities().mayfly;
                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundMoveVehiclePacket move) {
                     if (!this.movementAuthority.acceptsViewerPosition()
                        || this.awaitingTeleport >= 0
                        || !finite(move.position())
                        || !Float.isFinite(move.yRot())
                        || !Float.isFinite(move.xRot())) {
                        return;
                     }

                     Entity vehicle = this.player.getRootVehicle();
                     if (vehicle == this.player || vehicle.getControllingPassenger() != this.player) {
                        return;
                     }

                     this.controlledVehicle = vehicle;
                     this.vehiclePosition = move.position();
                     this.vehicleYaw = move.yRot();
                     this.vehiclePitch = move.xRot();
                     this.vehicleOnGround = move.onGround();
                     this.restoreVehicle();
                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundPaddleBoatPacket paddle) {
                     if (this.movementAuthority.worker()) {
                        return;
                     }

                     if (this.player.getVehicle() instanceof AbstractBoat boat) {
                        boat.setPaddleState(paddle.getLeft(), paddle.getRight());
                        this.sendUpstream.accept(packet);
                     }
                  } else if (packet instanceof ServerboundUseItemOnPacket use) {
                     if (this.awaitingTeleport >= 0) {
                        return;
                     }

                     this.replayPrediction(
                        use.getSequence(),
                        ServerboundUseItemOnPacket.class,
                        () -> this.minecraft.gameMode.useItemOn(this.player, use.getHand(), use.getHitResult()),
                        sequence -> new ServerboundUseItemOnPacket(use.getHand(), use.getHitResult(), sequence)
                     );
                  } else if (packet instanceof ServerboundUseItemPacket use) {
                     if (this.awaitingTeleport >= 0 || !Float.isFinite(use.getYRot()) || !Float.isFinite(use.getXRot())) {
                        return;
                     }

                     this.yaw = use.getYRot();
                     this.pitch = use.getXRot();
                     this.player.setYRot(this.yaw);
                     this.player.setXRot(this.pitch);
                     this.replayPrediction(
                        use.getSequence(),
                        ServerboundUseItemPacket.class,
                        () -> this.minecraft.gameMode.useItem(this.player, use.getHand()),
                        sequence -> new ServerboundUseItemPacket(use.getHand(), sequence, use.getYRot(), use.getXRot())
                     );
                  } else if (packet instanceof ServerboundPlayerActionPacket action) {
                     this.handlePlayerAction(action);
                  } else if (packet instanceof ServerboundContainerClickPacket click) {
                     this.handleContainerClick(click);
                  } else if (packet instanceof ServerboundContainerClosePacket close) {
                     if (close.getContainerId() != this.player.containerMenu.containerId) {
                        return;
                     }

                     this.player.clientSideCloseContainer();
                     this.sendUpstream.accept(packet);
                     this.mirrorInventory();
                  } else if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
                     if (!this.player.hasInfiniteMaterials()) {
                        return;
                     }

                     int slot = creative.slotNum();
                     if (slot >= 0 && slot < this.player.inventoryMenu.slots.size()) {
                        this.player.inventoryMenu.getSlot(slot).set(creative.itemStack().copy());
                     }

                     this.sendUpstream.accept(packet);
                     this.mirrorInventory();
                  } else if (packet instanceof ServerboundSwingPacket swing) {
                     this.player.swing(swing.getHand(), true);
                     this.sendUpstream.accept(packet);
                  } else if (packet instanceof ServerboundAttackPacket attack) {
                     Entity target = this.level.getEntity(attack.entityId());
                     if (target != null) {
                        this.minecraft.gameMode.attack(this.player, target);
                     }
                  } else if (packet instanceof ServerboundInteractPacket interact) {
                     Entity target = this.level.getEntity(interact.entityId());
                     if (target != null) {
                        this.player.setShiftKeyDown(interact.usingSecondaryAction());
                        this.minecraft
                           .gameMode
                           .interact(this.player, target, new EntityHitResult(target, target.position().add(interact.location())), interact.hand());
                     }
                  } else {
                     this.sendUpstream.accept(packet);
                  }
               }
            }
         }
      }
   }

   public void beforeTick() {
      this.requireClientThread();
      this.ticks++;
      if (this.trackPlayer()) {
         if (this.player.isDeadOrDying()) {
            this.remoteInput.update(Input.EMPTY);
            this.applyInputKeys();
         } else {
            this.applyInputKeys();
            boolean workerMovement = this.motionSender != null
               && (
                  this.remotePhysicsRequired
                     || KetamineClub.moduleManager != null
                        && KetamineClub.moduleManager
                           .getModules()
                           .stream()
                           .anyMatch(module -> module.getCategory() == Module.Category.MOVEMENT && module.isEnabled())
               );
            if (this.movementAuthority.update(workerMovement)) {
               this.captureMovement();
               this.velocity = this.player.getDeltaMovement();
               this.controlledVehicle = null;
               this.vehiclePosition = null;
               this.sendMotion();
            }

            if (this.movementAuthority.worker()) {
               this.player.setYRot(this.yaw);
               this.player.setXRot(this.pitch);
            } else {
               this.restoreMovement(true);
            }

            this.updateCrosshair();
         }
      }
   }

   public void afterMovementTick() {
      this.requireClientThread();
      if (this.trackPlayer()) {
         if (!this.player.isDeadOrDying()) {
            if (!this.movementAuthority.worker()) {
               this.restoreMovement(false);
            }

            this.updateCrosshair();
         }
      }
   }

   public void afterTick() {
      this.requireClientThread();
      if (this.trackPlayer()) {
         if (!this.player.isDeadOrDying()) {
            if (this.movementAuthority.worker()) {
               this.captureMovement();
               this.velocity = this.player.getDeltaMovement();
               this.sprinting = this.player.isSprinting();
               this.sendMotion();
            } else {
               this.restoreMovement(false);
            }

            this.mirrorInventory();
         }
      }
   }

   public void onTeleport(int teleportId) {
      this.requireClientThread();
      if (this.trackPlayer()) {
         this.captureMovement();
         this.velocity = Vec3.ZERO;
         this.awaitingTeleport = teleportId;
         this.controlledVehicle = null;
         this.vehiclePosition = null;
      }
   }

   public int acknowledge(int sequence) {
      return this.predictions.acknowledge(sequence);
   }

   public void serverHeldSlot(int slot) {
      this.requireClientThread();
      if (this.trackPlayer() && Inventory.isHotbarSlot(slot)) {
         this.mirroredSlot = slot;
      }
   }

   public boolean suppressWorkerPacket(Packet<?> packet) {
      if (this.replayViewerSequence >= 0 && this.replayPacketClass.isInstance(packet)) {
         int sequence = packet instanceof ServerboundUseItemOnPacket use ? use.getSequence() : ((ServerboundUseItemPacket)packet).getSequence();
         this.predictions.register(sequence, this.replayViewerSequence);
         this.replaySent = true;
      }

      if (workerOwns(packet)) {
         return false;
      } else if (viewerOwnsChat(packet)) {
         return true;
      } else if (this.player != null && this.player.isDeadOrDying()) {
         return true;
      } else if (this.movementAuthority.worker()) {
         return false;
      } else {
         return !(packet instanceof ServerboundPlayerInputPacket)
               && !(packet instanceof ServerboundPaddleBoatPacket)
               && !(packet instanceof ServerboundMoveVehiclePacket)
            ? packet instanceof ServerboundPlayerCommandPacket command
               && (
                  command.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_SPRINTING
                     || command.getAction() == net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
               )
            : true;
      }
   }

   public void mirrorInventory() {
      this.requireClientThread();
      if (this.trackPlayer()) {
         this.inventorySnapshot = this.mirrorMenu(this.player.inventoryMenu, this.inventorySnapshot);
         if (this.player.containerMenu != this.player.inventoryMenu) {
            this.containerSnapshot = this.mirrorMenu(this.player.containerMenu, this.containerSnapshot);
         } else {
            this.containerSnapshot = null;
         }

         int selected = this.player.getInventory().getSelectedSlot();
         if (selected != this.mirroredSlot) {
            this.sendViewer.accept(new ClientboundSetHeldSlotPacket(selected));
            this.mirroredSlot = selected;
         }
      }
   }

   public void reset() {
      this.requireClientThread();
      if (this.player != null && this.player.input == this.remoteInput && this.originalInput != null) {
         this.player.input = this.originalInput;
      }

      this.player = null;
      this.level = null;
      this.originalInput = null;
      this.position = null;
      this.velocity = Vec3.ZERO;
      this.awaitingTeleport = -1;
      this.controlledVehicle = null;
      this.vehiclePosition = null;
      this.inventorySnapshot = null;
      this.containerSnapshot = null;
      this.mirroredSlot = -1;
      this.replayViewerSequence = -1;
      this.replayPacketClass = null;
      this.remoteInput.update(Input.EMPTY);
      this.attackHeld = false;
      this.useHeld = false;
      this.applyInputKeys();
      this.movementAuthority.reset();
      this.predictions.reset();
   }

   private void replayPrediction(int viewerSequence, Class<?> packetClass, Runnable action, IntFunction<Packet<?>> fallback) {
      if (viewerSequence >= 0) {
         if (PingBypassProtocol.usesLegacySequencing()) {
            action.run();
            this.sendViewer.accept(new ClientboundBlockChangedAckPacket(viewerSequence));
            this.mirrorInventory();
         } else {
            int previousSequence = this.predictionHandler().currentSequence();
            this.replayViewerSequence = viewerSequence;
            this.replayPacketClass = packetClass;
            this.replaySent = false;

            try {
               action.run();
               if (!this.replaySent) {
                  if (this.predictionHandler().currentSequence() != previousSequence) {
                     this.predictions.register(this.predictionHandler().currentSequence(), viewerSequence);
                  } else {
                     this.forwardPrediction(viewerSequence, fallback);
                  }
               }
            } finally {
               this.replayViewerSequence = -1;
               this.replayPacketClass = null;
            }

            this.mirrorInventory();
         }
      }
   }

   private void handlePlayerAction(ServerboundPlayerActionPacket action) {
      switch (action.getAction()) {
         case START_DESTROY_BLOCK:
            if (this.awaitingTeleport < 0 && this.player != null && this.level != null) {
               AttackBlockEvent event = new AttackBlockEvent(action.getPos(), this.level.getBlockState(action.getPos()), action.getDirection());
               if (Util.EVENT_BUS.post(event)) {
                  this.sendViewer.accept(new ClientboundBlockChangedAckPacket(action.getSequence()));
                  return;
               }

               this.forwardPrediction(
                  action.getSequence(), sequence -> new ServerboundPlayerActionPacket(action.getAction(), action.getPos(), action.getDirection(), sequence)
               );
               return;
            }

            return;
         case ABORT_DESTROY_BLOCK:
         case STOP_DESTROY_BLOCK:
            if (this.awaitingTeleport >= 0) {
               return;
            }

            this.forwardPrediction(
               action.getSequence(), sequence -> new ServerboundPlayerActionPacket(action.getAction(), action.getPos(), action.getDirection(), sequence)
            );
            return;
         case RELEASE_USE_ITEM:
            this.player.releaseUsingItem();
            break;
         case DROP_ITEM:
            this.player.getInventory().removeFromSelected(false);
            break;
         case DROP_ALL_ITEMS:
            this.player.getInventory().removeFromSelected(true);
            break;
         case SWAP_ITEM_WITH_OFFHAND:
            ItemStack main = this.player.getMainHandItem();
            this.player.setItemInHand(InteractionHand.MAIN_HAND, this.player.getOffhandItem());
            this.player.setItemInHand(InteractionHand.OFF_HAND, main);
      }

      this.sendUpstream.accept(action);
      this.mirrorInventory();
   }

   public void onRespawn() {
      this.requireClientThread();
      if (this.player != null && this.player.input == this.remoteInput && this.originalInput != null) {
         this.player.input = this.originalInput;
      }

      this.player = null;
      this.level = null;
      this.originalInput = null;
      this.position = null;
      this.velocity = Vec3.ZERO;
      this.awaitingTeleport = -1;
      this.controlledVehicle = null;
      this.vehiclePosition = null;
      this.inventorySnapshot = null;
      this.containerSnapshot = null;
      this.mirroredSlot = -1;
      this.predictions.reset();
   }

   private void forwardPrediction(int viewerSequence, IntFunction<Packet<?>> packet) {
      if (viewerSequence >= 0) {
         if (PingBypassProtocol.usesLegacySequencing()) {
            this.sendUpstream.accept(packet.apply(0));
            this.sendViewer.accept(new ClientboundBlockChangedAckPacket(viewerSequence));
         } else {
            BlockStatePredictionHandler prediction = this.predictionHandler().startPredicting();

            try {
               this.predictions.register(prediction.currentSequence(), viewerSequence);
               this.sendUpstream.accept(packet.apply(prediction.currentSequence()));
            } catch (Throwable var7) {
               if (prediction != null) {
                  try {
                     prediction.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (prediction != null) {
               prediction.close();
            }
         }
      }
   }

   private void handleContainerClick(ServerboundContainerClickPacket click) {
      AbstractContainerMenu menu = this.player.containerMenu;
      if (click.containerId() == menu.containerId
         && (click.slotNum() >= 0 || click.slotNum() == -999 || click.slotNum() == -1)
         && click.slotNum() < menu.slots.size()) {
         this.minecraft.gameMode.handleContainerInput(click.containerId(), click.slotNum(), click.buttonNum(), click.containerInput(), this.player);
         this.refreshInventory();
      } else {
         this.refreshInventory();
      }
   }

   private boolean trackPlayer() {
      if (this.minecraft.player != null && this.minecraft.level != null && this.minecraft.gameMode != null) {
         if (this.level != this.minecraft.level) {
            this.level = this.minecraft.level;
            this.predictions.reset();
         }

         if (this.player != this.minecraft.player) {
            if (this.player != null && this.player.input == this.remoteInput && this.originalInput != null) {
               this.player.input = this.originalInput;
            }

            this.player = this.minecraft.player;
            this.originalInput = this.player.input;
            this.player.input = this.remoteInput;
            this.remoteInput.update(Input.EMPTY);
            this.captureMovement();
            this.sprinting = this.player.isSprinting();
            this.velocity = Vec3.ZERO;
            this.controlledVehicle = null;
            this.vehiclePosition = null;
            this.inventorySnapshot = null;
            this.containerSnapshot = null;
            this.mirroredSlot = -1;
         }

         return true;
      } else {
         return false;
      }
   }

   private BlockStatePredictionHandler predictionHandler() {
      return ((ClientLevelAccessor)this.level).ketamineclub$getBlockStatePredictionHandler();
   }

   private void captureMovement() {
      this.position = this.player.position();
      this.yaw = this.player.getYRot();
      this.pitch = this.player.getXRot();
      this.onGround = this.player.onGround();
      this.horizontalCollision = this.player.horizontalCollision;
   }

   private void restoreMovement(boolean rotation) {
      this.player.setPos(this.position);
      this.player.setDeltaMovement(this.ticks - this.movementTick <= 1 ? this.velocity : Vec3.ZERO);
      this.player.setOnGround(this.onGround);
      this.player.horizontalCollision = this.horizontalCollision;
      this.player.setShiftKeyDown(this.remoteInput.keyPresses.shift());
      this.player.setSprinting(this.sprinting);
      if (rotation) {
         this.player.setYRot(this.yaw);
         this.player.setXRot(this.pitch);
      }

      this.restoreVehicle();
   }

   private void restoreVehicle() {
      if (this.controlledVehicle != null && this.controlledVehicle == this.player.getRootVehicle() && this.vehiclePosition != null) {
         this.controlledVehicle.setPos(this.vehiclePosition);
         this.controlledVehicle.setYRot(this.vehicleYaw);
         this.controlledVehicle.setXRot(this.vehiclePitch);
         this.controlledVehicle.setOnGround(this.vehicleOnGround);
         this.controlledVehicle.setDeltaMovement(Vec3.ZERO);
      }
   }

   private void applyInputKeys() {
      Input input = this.remoteInput.raw;
      this.minecraft.options.keyUp.setDown(input.forward());
      this.minecraft.options.keyDown.setDown(input.backward());
      this.minecraft.options.keyLeft.setDown(input.left());
      this.minecraft.options.keyRight.setDown(input.right());
      this.minecraft.options.keyJump.setDown(input.jump());
      this.minecraft.options.keyShift.setDown(input.shift());
      this.minecraft.options.keySprint.setDown(input.sprint());
      this.minecraft.options.keyAttack.setDown(this.attackHeld);
      this.minecraft.options.keyUse.setDown(this.useHeld);
   }

   private void updateCrosshair() {
      Entity camera = this.minecraft.getCameraEntity();
      this.minecraft.hitResult = this.player.raycastHitResult(1.0F, (Entity)(camera != null ? camera : this.player));
      this.minecraft.crosshairPickEntity = this.minecraft.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
   }

   private void sendMotion() {
      if (this.motionSender != null && this.player != null) {
         JsonObject state = new JsonObject();
         state.addProperty("op", "motion");
         state.addProperty("authoritative", this.movementAuthority.worker());
         state.addProperty("generation", this.movementAuthority.generation());
         state.addProperty("tick", this.ticks);
         state.addProperty("x", this.player.getX());
         state.addProperty("y", this.player.getY());
         state.addProperty("z", this.player.getZ());
         state.addProperty("dx", this.player.getDeltaMovement().x);
         state.addProperty("dy", this.player.getDeltaMovement().y);
         state.addProperty("dz", this.player.getDeltaMovement().z);
         state.addProperty("onGround", this.player.onGround());
         state.addProperty("horizontalCollision", this.player.horizontalCollision);
         state.addProperty("sprinting", this.player.isSprinting());
         state.addProperty("flying", this.player.getAbilities().flying);
         state.addProperty("fallFlying", this.player.isFallFlying());
         Entity vehicle = this.player.getRootVehicle();
         if (vehicle != this.player && vehicle.getControllingPassenger() == this.player) {
            JsonObject mount = new JsonObject();
            mount.addProperty("id", vehicle.getId());
            mount.addProperty("x", vehicle.getX());
            mount.addProperty("y", vehicle.getY());
            mount.addProperty("z", vehicle.getZ());
            mount.addProperty("yaw", vehicle.getYRot());
            mount.addProperty("pitch", vehicle.getXRot());
            mount.addProperty("dx", vehicle.getDeltaMovement().x);
            mount.addProperty("dy", vehicle.getDeltaMovement().y);
            mount.addProperty("dz", vehicle.getDeltaMovement().z);
            state.add("vehicle", mount);
         }

         this.motionSender.accept(state);
      }
   }

   private RemotePlayerController.MenuSnapshot mirrorMenu(AbstractContainerMenu menu, RemotePlayerController.MenuSnapshot previous) {
      List<ItemStack> items = menu.getItems();
      ItemStack carried = menu.getCarried();
      if (previous != null && previous.matches(menu, items, carried)) {
         return previous;
      } else {
         List<ItemStack> copies = items.stream().<ItemStack>map(ItemStack::copy).toList();
         ItemStack cursorCopy = carried.copy();
         this.sendViewer.accept(new ClientboundContainerSetContentPacket(menu.containerId, menu.getStateId(), copies, cursorCopy));
         return new RemotePlayerController.MenuSnapshot(menu, menu.getStateId(), copies, cursorCopy);
      }
   }

   private void refreshInventory() {
      this.inventorySnapshot = null;
      this.containerSnapshot = null;
      this.mirrorInventory();
   }

   private static boolean workerOwns(Packet<?> packet) {
      return packet instanceof ServerboundKeepAlivePacket
         || packet instanceof ServerboundPongPacket
         || packet instanceof ServerboundPingRequestPacket
         || packet instanceof ServerboundChunkBatchReceivedPacket
         || packet instanceof ServerboundClientTickEndPacket
         || packet instanceof ServerboundPlayerLoadedPacket
         || packet instanceof ServerboundClientInformationPacket
         || packet instanceof ServerboundCustomPayloadPacket;
   }

   private static boolean viewerOwnsChat(Packet<?> packet) {
      return packet instanceof ServerboundChatPacket
         || packet instanceof ServerboundChatCommandPacket
         || packet instanceof ServerboundChatCommandSignedPacket
         || packet instanceof ServerboundChatAckPacket
         || packet instanceof ServerboundChatSessionUpdatePacket;
   }

   private static boolean finite(Vec3 position) {
      return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
   }

   private void requireClientThread() {
      if (!this.minecraft.isSameThread()) {
         throw new IllegalStateException("PingBypass actions must run on the client thread");
      }
   }

   private record MenuSnapshot(AbstractContainerMenu menu, int stateId, List<ItemStack> items, ItemStack carried) {
      private boolean matches(AbstractContainerMenu current, List<ItemStack> currentItems, ItemStack currentCarried) {
         if (this.menu == current
            && this.stateId == current.getStateId()
            && this.items.size() == currentItems.size()
            && ItemStack.matches(this.carried, currentCarried)) {
            for (int i = 0; i < this.items.size(); i++) {
               if (!ItemStack.matches(this.items.get(i), currentItems.get(i))) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static final class RemoteInput extends ClientInput {
      private Input raw = Input.EMPTY;

      private RemoteInput() {
      }

      private void update(Input input) {
         this.raw = input;
         this.apply(input);
      }

      private void apply(Input input) {
         this.keyPresses = input;
         float x = input.left() == input.right() ? 0.0F : (input.left() ? 1.0F : -1.0F);
         float y = input.forward() == input.backward() ? 0.0F : (input.forward() ? 1.0F : -1.0F);
         this.moveVector = new Vec2(x, y).normalized();
      }

      public void tick() {
         Options options = Minecraft.getInstance().options;
         this.apply(
            new Input(
               options.keyUp.isDown(),
               options.keyDown.isDown(),
               options.keyLeft.isDown(),
               options.keyRight.isDown(),
               options.keyJump.isDown(),
               options.keyShift.isDown(),
               options.keySprint.isDown()
            )
         );
      }
   }
}
