package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;

public class AutoInfestModule extends Module {
   private static final TagKey<Block> MINECRAFT_INFESTED = tag("minecraft", "infested");
   private static final TagKey<Block> MINECRAFT_INFESTED_BLOCKS = tag("minecraft", "infested_blocks");
   private static final TagKey<Block> COMMON_INFESTED = tag("c", "infested");
   private static final TagKey<Block> COMMON_INFESTED_BLOCKS = tag("c", "infested_blocks");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 110, 110, 110, 55).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 180, 180, 180, 220).setPage("Render");
   private BlockPos renderPos;
   private long renderStart;

   public AutoInfestModule() {
      super("AutoInfest", "infest", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      this.renderPos = null;
      this.renderStart = 0L;
   }

   @Override
   public void onDisable() {
      this.renderPos = null;
      this.renderStart = 0L;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         if (mc.player.containerMenu.containerId == 0) {
            AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
            if (autoMine != null && autoMine.isEnabled()) {
               BlockPos rebreak = autoMine.getReadyRebreakPoint();
               if (rebreak != null && mc.level.getBlockState(rebreak).canBeReplaced()) {
                  Result infested = InventoryUtil.find(
                     (Predicate<ItemStack>)(stack -> stack.getItem() instanceof BlockItem blockItem && this.isInfested(blockItem.getBlock())),
                     InventoryUtil.PLACE_SCOPE
                  );
                  if (infested.found()) {
                     if (KetamineClub.placementManager.placeDirect(rebreak, null, infested.slot())) {
                        this.renderPos = rebreak.immutable();
                        this.renderStart = System.currentTimeMillis();
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isInfested(Block block) {
      BlockState state = block.defaultBlockState();
      return block instanceof InfestedBlock
         || state.is(MINECRAFT_INFESTED)
         || state.is(MINECRAFT_INFESTED_BLOCKS)
         || state.is(COMMON_INFESTED)
         || state.is(COMMON_INFESTED_BLOCKS);
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue() && this.renderPos != null) {
         double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;
         double progress = (System.currentTimeMillis() - this.renderStart) / fadeMs;
         if (progress >= 1.0) {
            this.renderPos = null;
         } else {
            Color fill = this.faded(this.fillColor.getValue(), progress);
            Color outline = this.faded(this.outlineColor.getValue(), progress);
            RenderUtil.drawBoxFilled(event.getMatrix(), this.renderPos, fill);
            RenderUtil.drawBox(event.getMatrix(), this.renderPos, outline, 1.0F);
         }
      }
   }

   private Color faded(Color color, double progress) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp((int)(color.getAlpha() * (1.0 - progress)), 0, 255));
   }

   private static TagKey<Block> tag(String namespace, String path) {
      return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(namespace, path));
   }
}
