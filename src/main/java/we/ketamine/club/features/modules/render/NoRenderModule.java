package we.ketamine.club.features.modules.render;

import java.util.function.Predicate;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class NoRenderModule extends Module {
   public final Setting<Boolean> noTilt = this.bool("NoTilt", true).setPage("Camera");
   public final Setting<Boolean> noBob = this.bool("NoBob", true).setPage("Camera");
   public final Setting<Boolean> noTotem = this.bool("NoTotem", true).setPage("Camera");
   public final Setting<Boolean> noFire = this.bool("NoFire", true).setPage("Overlays");
   public final Setting<Boolean> noPumpkin = this.bool("NoPumpkin", false).setPage("Overlays");
   public final Setting<Boolean> noPowderedSnow = this.bool("NoPowdered", false).setPage("Overlays");
   public final Setting<Boolean> portalGui = this.bool("PortalGui", true).setPage("Overlays");
   public final Setting<Boolean> noPortal = this.bool("NoPortalGui", true).setPage("Overlays");
   public final Setting<Boolean> noBackground = this.bool("NoBackground", true).setPage("Overlays");
   public final Setting<Boolean> noVignette = this.bool("NoVignette", true).setPage("Overlays");
   public final Setting<Boolean> noBossBar = this.bool("NoBoss", true).setPage("Overlays");
   public final Setting<Boolean> noPotIcon = this.bool("NoPotIcon", true).setPage("Overlays");
   public final Setting<Boolean> noCrosshair = this.bool("NoCrosshair", false).setPage("Overlays");
   public final Setting<Boolean> noCooldown = this.bool("NoCooldown", false).setPage("Overlays");
   public final Setting<Boolean> noItemName = this.bool("NoItemName", false).setPage("Overlays");
   public final Setting<Boolean> noArmor = this.bool("NoArmor", true).setPage("Overlays");
   public final Setting<Boolean> noNausea = this.bool("NoNausea", true).setPage("Overlays");
   public final Setting<Boolean> noTotemParticle = this.bool("NoPopParticle", false).setPage("Particles");
   public final Setting<Boolean> noWaterParticle = this.bool("NoWaterParticle", true).setPage("Particles");
   public final Setting<Boolean> noExplosion = this.bool("NoExplosion", true).setPage("Particles");
   public final Setting<Boolean> noBlockBreak = this.bool("NoBreakParticle", false).setPage("Particles");
   public final Setting<Boolean> noPotionParticle = this.bool("NoPotionParticle", false).setPage("Particles");
   public final Setting<Integer> tileEntity = this.num("TileEntity", 0, 0, 75).setPage("World");
   public final Setting<Boolean> noLiquid = this.bool("NoLiquid", false).setPage("World");
   public final Setting<Boolean> noWall = this.bool("NoWall", false).setPage("World");
   public final Setting<Boolean> noFog = this.bool("NoFog", true).setPage("World");
   public final Setting<Boolean> noDarkness = this.bool("NoDarkness", true).setPage("World");
   public final Setting<Boolean> noCrystalFire = this.bool("NoCrystalFire", false).setPage("World");

   public NoRenderModule() {
      super("NoRender", "Prevent rendering certain overlays and visual effects.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.reloadRenderer();
   }

   @Override
   public void onDisable() {
      this.reloadRenderer();
   }

   private void reloadRenderer() {
      if (mc.level != null) {
         mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
      }
   }

   public static NoRenderModule getInstance() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(NoRenderModule.class);
   }

   public static boolean isActive(Predicate<NoRenderModule> predicate) {
      NoRenderModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }
}
