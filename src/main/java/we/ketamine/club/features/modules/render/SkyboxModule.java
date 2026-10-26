package we.ketamine.club.features.modules.render;

import java.awt.Color;
import java.util.function.Predicate;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class SkyboxModule extends Module {
   public Setting<SkyboxModule.Mode> mode = this.mode("Mode", SkyboxModule.Mode.Aurora).setPage("General");
   public Setting<Boolean> replaceCelestial = this.bool("ReplaceCelestial", true).setPage("General");
   public Setting<Color> auroraColor = this.color("AuroraColor", 26, 217, 115, 255).setPage("General");

   public SkyboxModule() {
      super("Skybox", "Replaces the sky with a custom OpenGL shader", Module.Category.RENDER);
      this.auroraColor.setVisibility(v -> this.mode.getValue() == SkyboxModule.Mode.Aurora);
   }

   @Override
   public String getDisplayInfo() {
      return this.mode.getValue().name();
   }

   public static SkyboxModule getInstance() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(SkyboxModule.class);
   }

   public static boolean isActive(Predicate<SkyboxModule> predicate) {
      SkyboxModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }

   public static enum Mode {
      Aurora,
      Nebula,
      Sunset,
      Trippy;

      private Mode() {
      }
   }
}
