package we.ketamine.club.features.modules.render;

import java.awt.Color;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class AmbienceModule extends Module {
   public final Setting<Boolean> sky = this.bool("Sky", true).setPage("Sky");
   public final Setting<Color> skyColor = this.color("SkyColor", 159, 194, 216, 255).setPage("Sky");
   public final Setting<Boolean> sunrise = this.bool("Sunrise", false).setPage("Sky");
   public final Setting<Color> sunriseColor = this.color("SunriseColor", 255, 142, 92, 180).setPage("Sky");
   public final Setting<Boolean> fog = this.bool("Fog", true).setPage("Fog");
   public final Setting<Color> fogColor = this.color("FogColor", 159, 194, 216, 255).setPage("Fog");
   public final Setting<Boolean> chunkFade = this.bool("ChunkFade", true).setPage("World");
   public final Setting<Color> chunkFadeColor = this.color("ChunkFadeColor", 159, 194, 216, 255).setPage("World");
   public final Setting<Boolean> clouds = this.bool("Clouds", false).setPage("World");
   public final Setting<Color> cloudColor = this.color("CloudColor", 255, 255, 255, 255).setPage("World");

   public AmbienceModule() {
      super("Ambience", "Overrides ambient world colors.", Module.Category.RENDER);
      this.skyColor.setVisibility(value -> this.sky.getValue());
      this.sunriseColor.setVisibility(value -> this.sunrise.getValue());
      this.fogColor.setVisibility(value -> this.fog.getValue());
      this.chunkFadeColor.setVisibility(value -> this.chunkFade.getValue());
      this.cloudColor.setVisibility(value -> this.clouds.getValue());
   }

   public void applyLevelColors(LevelRenderState state) {
      if (this.sky.getValue()) {
         state.skyRenderState.skyColor = this.skyColor.getValue().getRGB();
      }

      if (this.sunrise.getValue()) {
         state.skyRenderState.sunriseAndSunsetColor = this.sunriseColor.getValue().getRGB();
      }

      if (this.clouds.getValue()) {
         state.cloudColor = this.cloudColor.getValue().getRGB();
      }
   }

   public void applyFogColor(FogData data) {
      if (this.fog.getValue()) {
         Color color = this.fogColor.getValue();
         data.color.set(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
      }
   }

   public static AmbienceModule getInstance() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(AmbienceModule.class);
   }

   public static AmbienceModule active() {
      AmbienceModule module = getInstance();
      return module != null && module.isEnabled() ? module : null;
   }
}
