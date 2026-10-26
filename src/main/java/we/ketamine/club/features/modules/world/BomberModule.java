package we.ketamine.club.features.modules.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class BomberModule extends Module {
   private final Setting<Float> radius = this.num("Radius", 4.0F, 1.0F, 6.0F);
   private final Setting<Integer> delay = this.num("Delay", 6, 0, 20);
   private final Setting<Boolean> autoDisable = this.bool("AutoDisable", false);
   private static final int IGNITE_PRIORITY = 40;
   private int ticksWaited = 0;
   private BlockPos lastPos = null;

   public BomberModule() {
      super("Bomber", "Automatically places and lights TNT around you.", Module.Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.ticksWaited = 0;
      this.lastPos = null;
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.ticksWaited < this.delay.getValue()) {
            this.ticksWaited++;
         } else {
            this.ticksWaited = 0;
            Result tnt = this.findHotbar(Items.TNT);
            Result fas = this.findHotbar(Items.FLINT_AND_STEEL);
            if (tnt != null && fas != null) {
               this.placeAndIgnite(tnt, fas);
            } else {
               if (this.autoDisable.getValue()) {
                  this.disable();
               }
            }
         }
      }
   }

   private void placeAndIgnite(Result tnt, Result fas) {
      BlockPos player = mc.player.blockPosition();
      double r = this.radius.getValue().floatValue();

      for (double x = -r; x <= r; x++) {
         for (double z = -r; z <= r; z++) {
            if (!(x * x + z * z > r * r)) {
               BlockPos pos = player.offset((int)x, 0, (int)z);
               if (!pos.equals(this.lastPos) && mc.level.getBlockState(pos).isAir()) {
                  this.lastPos = pos;
                  if (KetamineClub.placementManager.enqueue(pos, tnt.slot())) {
                     KetamineClub.placementManager.flushQueue();
                     this.igniteAt(pos, fas);
                     return;
                  }
               }
            }
         }
      }
   }

   private void igniteAt(BlockPos pos, Result fas) {
      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      KetamineClub.swapManager.submit(new SwapRequest("Bomber_ignite", 40, fas, () -> {
         mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
         mc.player.swing(InteractionHand.MAIN_HAND);
      }));
   }

   private Result findHotbar(Item item) {
      Result r = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      return r.found() ? r : null;
   }
}
