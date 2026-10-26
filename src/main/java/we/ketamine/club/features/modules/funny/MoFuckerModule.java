package we.ketamine.club.features.modules.funny;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class MoFuckerModule extends Module {
   private final Setting<Float> range = this.num("Range", 5.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 2, 0, 20);
   private static final int PRIORITY = 100;
   private MoFuckerModule.State state;
   private BlockPos targetPos;
   private int ticks;

   public MoFuckerModule() {
      super("MoFucker", "On keybind, dumps a water bucket on the nearest enemy and scoops it back up.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.state = MoFuckerModule.State.PLACE;
      this.targetPos = null;
      this.ticks = 0;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && !PingBypassInput.isScreenOpen()) {
         switch (this.state) {
            case PLACE:
               this.place();
               break;
            case COLLECT:
               this.collect();
         }
      } else {
         this.disable();
      }
   }

   private void place() {
      Player target = this.findTarget();
      if (target == null) {
         this.disable();
      } else {
         Result water = InventoryUtil.find(Items.WATER_BUCKET, InventoryUtil.FULL_SCOPE);
         if (!water.found()) {
            this.disable();
         } else {
            this.targetPos = target.blockPosition();
            this.useAt(Vec3.atBottomCenterOf(this.targetPos), water, "MoFucker_place");
            this.state = MoFuckerModule.State.COLLECT;
            this.ticks = 0;
         }
      }
   }

   private void collect() {
      if (this.ticks++ >= this.delay.getValue()) {
         Result bucket = InventoryUtil.find(Items.BUCKET, InventoryUtil.FULL_SCOPE);
         if (!bucket.found()) {
            this.disable();
         } else {
            this.useAt(Vec3.atCenterOf(this.targetPos), bucket, "MoFucker_collect");
            this.disable();
         }
      }
   }

   private void useAt(Vec3 aim, Result item, String id) {
      float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), aim);
      KetamineClub.rotationManager.submit(new RotationRequest("MoFucker", 100, angles[0], angles[1], RotationRequest.Mode.SILENT));
      KetamineClub.swapManager.submit(new SwapRequest(id, 40, item, () -> {
         float prevYaw = mc.player.getYRot();
         float prevPitch = mc.player.getXRot();
         mc.player.setYRot(angles[0]);
         mc.player.setXRot(angles[1]);

         try {
            mc.gameMode.useItem(mc.player, item.hand());
         } finally {
            mc.player.setYRot(prevYaw);
            mc.player.setXRot(prevPitch);
         }
      }));
   }

   private Player findTarget() {
      double reachSq = this.range.getValue() * this.range.getValue();
      Player best = null;
      double bestSq = Double.MAX_VALUE;

      for (Player p : mc.level.players()) {
         if (p != mc.player && !KetamineClub.friendManager.isFriend(p)) {
            double distSq = mc.player.distanceToSqr(p);
            if (!(distSq > reachSq) && distSq < bestSq) {
               bestSq = distSq;
               best = p;
            }
         }
      }

      return best;
   }

   private static enum State {
      PLACE,
      COLLECT;

      private State() {
      }
   }
}
