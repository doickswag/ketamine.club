package we.ketamine.club.features.modules.slop;

import java.awt.Color;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class ColorChangerForStuffModule extends Module {
   public final Setting<Boolean> water = this.bool("Water", true);
   public final Setting<Color> waterColor = this.color("WaterColor", 45, 135, 255, 255);
   public final Setting<Boolean> lava = this.bool("Lava", true);
   public final Setting<Color> lavaColor = this.color("LavaColor", 255, 80, 20, 255);
   public final Setting<Boolean> grass = this.bool("Grass", false);
   public final Setting<Color> grassColor = this.color("GrassColor", 90, 220, 90, 255);
   public final Setting<Boolean> foliage = this.bool("Foliage", false);
   public final Setting<Color> foliageColor = this.color("FoliageColor", 55, 190, 85, 255);

   public ColorChangerForStuffModule() {
      super("ColorChangerForStuff", "Changes the client-side colors of common world tints.", Module.Category.SLOP);
      this.waterColor.setVisibility(value -> this.water.getValue());
      this.lavaColor.setVisibility(value -> this.lava.getValue());
      this.grassColor.setVisibility(value -> this.grass.getValue());
      this.foliageColor.setVisibility(value -> this.foliage.getValue());
   }

   public static ColorChangerForStuffModule active() {
      if (KetamineClub.moduleManager == null) {
         return null;
      } else {
         ColorChangerForStuffModule module = KetamineClub.moduleManager.getModuleByClass(ColorChangerForStuffModule.class);
         return module != null && module.isEnabled() ? module : null;
      }
   }

   public int blockColor(BlockState state, int original) {
      if (!this.water.getValue() || !state.is(Blocks.WATER) && !state.is(Blocks.BUBBLE_COLUMN)) {
         if (!this.grass.getValue()
            || !state.is(Blocks.GRASS_BLOCK)
               && !state.is(Blocks.SHORT_GRASS)
               && !state.is(Blocks.TALL_GRASS)
               && !state.is(Blocks.FERN)
               && !state.is(Blocks.LARGE_FERN)) {
            return this.foliage.getValue() && state.is(BlockTags.LEAVES) ? rgb(this.foliageColor.getValue()) : original;
         } else {
            return rgb(this.grassColor.getValue());
         }
      } else {
         return rgb(this.waterColor.getValue());
      }
   }

   public int waterColor(int original) {
      return this.water.getValue() ? rgb(this.waterColor.getValue()) : original;
   }

   public int grassColor(int original) {
      return this.grass.getValue() ? rgb(this.grassColor.getValue()) : original;
   }

   public int foliageColor(int original) {
      return this.foliage.getValue() ? rgb(this.foliageColor.getValue()) : original;
   }

   public int lavaColor(int original) {
      return this.lava.getValue() ? rgb(this.lavaColor.getValue()) : original;
   }

   private static int rgb(Color color) {
      return color.getRGB() & 16777215;
   }
}
