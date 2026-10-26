package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.AnchorDamageUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class AutoAnchorModule extends Module {
   private static final String ROTATION_ID = "AutoAnchor";
   private static final int ROTATION_PRIORITY = 62;
   private final Setting<Double> minDamage = this.num("MinDamage", 6.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 4.0, 0.0, 36.0).setPage("General");
   private static final double PLACE_RANGE = 6.0;
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 130, 80, 255, 255).setPage("Render");
   private int lastBurstTick = -1;
   private float lastDamage = 0.0F;
   private BlockPos renderPos = null;
   private long renderStart = 0L;

   public AutoAnchorModule() {
      super("AutoAnchor", "Places, charges and detonates respawn anchors in a single burst.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.lastBurstTick = -1;
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("AutoAnchor");
      this.lastDamage = 0.0F;
      this.renderPos = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         if (!mc.level.dimension().equals(Level.NETHER)) {
            if (mc.player.containerMenu.containerId == 0) {
               int sinceLast = mc.player.tickCount - this.lastBurstTick;
               if (sinceLast < 0 || sinceLast >= this.delay.getValue()) {
                  if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                     OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
                     if (offhand == null || !offhand.shouldDeferForEat()) {
                        Result anchor = this.findResource(Items.RESPAWN_ANCHOR);
                        Result glow = this.findResource(Items.GLOWSTONE);
                        if (anchor.found() && glow.found()) {
                           List<AnchorDamageUtil.Target> targets = this.collectTargets();
                           if (targets.isEmpty()) {
                              this.lastDamage = 0.0F;
                           } else {
                              AutoAnchorModule.Candidate best = this.findBest(targets);
                              if (best == null) {
                                 this.lastDamage = 0.0F;
                              } else {
                                 this.queueBurst(best, anchor, glow);
                              }
                           }
                        } else {
                           this.lastDamage = 0.0F;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void queueBurst(AutoAnchorModule.Candidate candidate, Result anchor, Result glow) {
      Vec3 eye = mc.player.getEyePosition(1.0F);
      BlockHitResult hit = this.anchorHit(candidate.pos(), eye);
      float[] angles = MathUtil.calcAngle(eye, hit.getLocation());
      RotationRequest request = new RotationRequest("AutoAnchor", 62, angles[0], angles[1]);
      if (KetamineClub.rotationManager.submit(request) && KetamineClub.rotationManager.isServerRotationReady(request)) {
         this.finishBurst(candidate, anchor, glow, hit);
      }
   }

   private void finishBurst(AutoAnchorModule.Candidate candidate, Result anchor, Result glow, BlockHitResult hit) {
      if (this.burst(candidate.pos(), candidate.needPlace(), anchor, glow, hit)) {
         this.lastBurstTick = mc.player.tickCount;
         this.lastDamage = candidate.damage();
         if (this.render.getValue()) {
            this.renderPos = candidate.pos();
            this.renderStart = System.currentTimeMillis();
         }
      }
   }

   private boolean burst(BlockPos pos, boolean needPlace, Result anchor, Result glow, BlockHitResult hit) {
      if (!nullCheck() && !mc.player.isDeadOrDying() && !mc.level.dimension().equals(Level.NETHER) && mc.player.containerMenu.containerId == 0) {
         ClientPacketListener conn = mc.getConnection();
         if (conn == null) {
            return false;
         } else if (this.matches(anchor, Items.RESPAWN_ANCHOR) && this.matches(glow, Items.GLOWSTONE)) {
            BlockHitResult placeHit = null;
            if (needPlace) {
               placeHit = KetamineClub.placementManager.prepareAirPlaceHit(pos);
               if (placeHit == null) {
                  return false;
               }
            }

            BlockHitResult placementHit = placeHit;
            int originalSlot = KetamineClub.swapManager.serverSlot();
            if (originalSlot < 0 || originalSlot > 8) {
               return false;
            } else if (needPlace && !this.useResource(conn, anchor, originalSlot, Items.RESPAWN_ANCHOR, () -> {
               this.sendUseItemOn(conn, placementHit, InteractionHand.MAIN_HAND);
               KetamineClub.placementManager.notePlacement(pos);
            })) {
               return false;
            } else if (!this.useResource(conn, glow, originalSlot, Items.GLOWSTONE, () -> this.sendUseItemOn(conn, hit))) {
               return false;
            } else {
               if (this.isGlowstone(originalSlot)) {
                  if (!this.useResource(conn, anchor, originalSlot, null, () -> this.sendUseItemOn(conn, hit))) {
                     return false;
                  }
               } else {
                  this.sendUseItemOn(conn, hit);
               }

               conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean useResource(ClientPacketListener conn, Result resource, int originalSlot, Item expected, Runnable action) {
      if (resource.found() && resource.type() != ResultType.OFFHAND && resource.type() != ResultType.NONE) {
         int slot = resource.slot();
         if (slot < 0 || slot >= 36) {
            return false;
         } else if (expected != null && !mc.player.getInventory().getItem(slot).is(expected)) {
            return false;
         } else {
            boolean altSwap = resource.type() == ResultType.INVENTORY;
            int useSlot = altSwap ? originalSlot : slot;
            if (useSlot >= 0 && useSlot <= 8) {
               if (altSwap) {
                  if (!InventoryUtil.altSwapInto(resource, originalSlot)) {
                     return false;
                  }
               } else if (useSlot != originalSlot) {
                  conn.send(new ServerboundSetCarriedItemPacket(useSlot));
               }

               boolean var9;
               try {
                  action.run();
                  var9 = true;
               } finally {
                  if (altSwap) {
                     InventoryUtil.altSwapInto(resource, originalSlot);
                  } else if (useSlot != originalSlot) {
                     conn.send(new ServerboundSetCarriedItemPacket(originalSlot));
                  }
               }

               return var9;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void sendUseItemOn(ClientPacketListener conn, BlockHitResult hit) {
      this.sendUseItemOn(conn, hit, InteractionHand.MAIN_HAND);
   }

   private void sendUseItemOn(ClientPacketListener conn, BlockHitResult hit, InteractionHand hand) {
      BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

      try {
         conn.send(new ServerboundUseItemOnPacket(hand, hit, handler.currentSequence()));
      } catch (Throwable var8) {
         if (handler != null) {
            try {
               handler.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (handler != null) {
         handler.close();
      }
   }

   private boolean isGlowstone(int slot) {
      if (slot >= 0 && slot <= 8) {
         ItemStack stack = mc.player.getInventory().getItem(slot);
         return stack.is(Items.GLOWSTONE);
      } else {
         return false;
      }
   }

   private Result findResource(Item item) {
      return InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
   }

   private boolean matches(Result result, Item item) {
      if (result.found() && result.type() != ResultType.OFFHAND && result.type() != ResultType.NONE) {
         int slot = result.slot();
         return slot >= 0 && slot < 36 && mc.player.getInventory().getItem(slot).is(item);
      } else {
         return false;
      }
   }

   private BlockHitResult anchorHit(BlockPos pos, Vec3 eye) {
      Vec3 center = Vec3.atCenterOf(pos);
      double dx = eye.x - center.x;
      double dy = eye.y - center.y;
      double dz = eye.z - center.z;
      double ax = Math.abs(dx);
      double ay = Math.abs(dy);
      double az = Math.abs(dz);
      Direction face;
      if (ay >= ax && ay >= az) {
         face = dy > 0.0 ? Direction.UP : Direction.DOWN;
      } else if (ax >= az) {
         face = dx > 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         face = dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
      }

      Vec3 hitVec = center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
      return new BlockHitResult(hitVec, face, pos, false);
   }

   private AutoAnchorModule.Candidate findBest(List<AnchorDamageUtil.Target> targets) {
      Vec3 eye = mc.player.getEyePosition(1.0F);
      BlockPos playerPos = mc.player.blockPosition();
      double maxSelf = this.maxSelfDamage.getValue();
      double min = this.minDamage.getValue();
      double rangeSq = 36.0;
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      int r = (int)Math.ceil(6.0);
      int rr = r * r;
      AutoAnchorModule.Candidate best = null;
      MutableBlockPos cursor = new MutableBlockPos();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  Vec3 center = Vec3.atCenterOf(cursor);
                  if (!(eye.distanceToSqr(center) > rangeSq)) {
                     BlockState state = mc.level.getBlockState(cursor);
                     boolean existing = state.is(Blocks.RESPAWN_ANCHOR);
                     BlockPos pos = null;
                     boolean bounded = false;
                     int i = 0;

                     for (int n = targets.size(); i < n; i++) {
                        AnchorDamageUtil.Target target = targets.get(i);
                        if (!(target.pos().distanceTo(center) > 10.0) && AnchorDamageUtil.maxDamage(target, center) >= min) {
                           bounded = true;
                           break;
                        }
                     }

                     if (bounded) {
                        if (existing) {
                           pos = cursor.immutable();
                        } else {
                           if (!state.canBeReplaced() || !PlaceUtil.canPlace(cursor)) {
                              continue;
                           }

                           pos = cursor.immutable();
                        }

                        float total = 0.0F;
                        boolean any = false;
                        int ix = 0;

                        for (int nx = targets.size(); ix < nx; ix++) {
                           AnchorDamageUtil.Target target = targets.get(ix);
                           if (!(target.pos().distanceTo(center) > 10.0)) {
                              float dmg = AnchorDamageUtil.damage(target, center, pos);
                              if (!(dmg <= 0.0F)) {
                                 total += dmg;
                                 any = true;
                              }
                           }
                        }

                        if (any && !(total < min)) {
                           float self = AnchorDamageUtil.selfDamage(center, pos);
                           if ((best == null || !(total <= best.damage)) && !(self > maxSelf) && (!(self > 0.0F) || !(self + 1.5F >= playerHealth))) {
                              best = new AutoAnchorModule.Candidate(pos, total, !existing);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private List<AnchorDamageUtil.Target> collectTargets() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      List<AnchorDamageUtil.Target> out = new ArrayList<>();
      AABB area = mc.player.getBoundingBox().inflate(16.0);

      for (Entity e : mc.level.getEntities(mc.player, area)) {
         if (e instanceof LivingEntity living && !living.isDeadOrDying() && (targets == null || targets.isValidPlayerTarget(e))) {
            out.add(AnchorDamageUtil.Target.of(living));
         }
      }

      return out;
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPos != null) {
         long age = System.currentTimeMillis() - this.renderStart;
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
         if (age > fadeMs) {
            this.renderPos = null;
         } else {
            double t = age / fadeMs;
            Color fc = this.fillColor.getValue();
            Color oc = this.outlineColor.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderPos, withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
            RenderUtil.drawBox(event.getMatrix(), this.renderPos, withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
         }
      }
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }

   @Override
   public String getDisplayInfo() {
      return this.lastDamage > 0.0F ? String.format("%.1f", this.lastDamage) : null;
   }

   private record Candidate(BlockPos pos, float damage, boolean needPlace) {
   }
}
