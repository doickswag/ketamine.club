package we.ketamine.club.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.features.commands.ModuleCommand;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.features.modules.client.HudClientModule;
import we.ketamine.club.features.modules.client.NotificationsModule;
import we.ketamine.club.features.modules.client.RotationsModule;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.client.TeleportLoggerModule;
import we.ketamine.club.features.modules.combat.AutoAnchorModule;
import we.ketamine.club.features.modules.combat.AutoBedModule;
import we.ketamine.club.features.modules.combat.AutoCrystalModule;
import we.ketamine.club.features.modules.combat.AutoInfestModule;
import we.ketamine.club.features.modules.combat.AutoLogModule;
import we.ketamine.club.features.modules.combat.AutoMineModule;
import we.ketamine.club.features.modules.combat.AutoSwordModule;
import we.ketamine.club.features.modules.combat.AutoTrapModule;
import we.ketamine.club.features.modules.combat.AutoWebModule;
import we.ketamine.club.features.modules.combat.BootSwapModule;
import we.ketamine.club.features.modules.combat.KeyPotionModule;
import we.ketamine.club.features.modules.combat.MaceAuraModule;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.modules.combat.PearlBlockerModule;
import we.ketamine.club.features.modules.combat.PhaseModule;
import we.ketamine.club.features.modules.combat.PistonCrystalModule;
import we.ketamine.club.features.modules.combat.PistonPushModule;
import we.ketamine.club.features.modules.combat.SurroundModule;
import we.ketamine.club.features.modules.funny.AutoHorseModule;
import we.ketamine.club.features.modules.funny.AutoMinecartModule;
import we.ketamine.club.features.modules.funny.DoohickeyTrapModule;
import we.ketamine.club.features.modules.funny.EndermanLookModule;
import we.ketamine.club.features.modules.funny.MoFuckerModule;
import we.ketamine.club.features.modules.funny.TrickshotModule;
import we.ketamine.club.features.modules.funny.XCarryModule;
import we.ketamine.club.features.modules.movement.BhopModule;
import we.ketamine.club.features.modules.movement.ElytraAssistModule;
import we.ketamine.club.features.modules.movement.ElytraDashModule;
import we.ketamine.club.features.modules.movement.ElytraFlyModule;
import we.ketamine.club.features.modules.movement.ElytraModule;
import we.ketamine.club.features.modules.movement.ElytraTestModule;
import we.ketamine.club.features.modules.movement.FakeFlyModule;
import we.ketamine.club.features.modules.movement.NoSlowModule;
import we.ketamine.club.features.modules.movement.SpeedModule;
import we.ketamine.club.features.modules.movement.SprintModule;
import we.ketamine.club.features.modules.movement.VelocityModule;
import we.ketamine.club.features.modules.movement.VerticalDesyncModule;
import we.ketamine.club.features.modules.player.AutoXPModule;
import we.ketamine.club.features.modules.player.FastuseModule;
import we.ketamine.club.features.modules.player.FreecamModule;
import we.ketamine.club.features.modules.player.InstantRekitModule;
import we.ketamine.club.features.modules.player.MiddleClickExtraModule;
import we.ketamine.club.features.modules.player.NoRotateModule;
import we.ketamine.club.features.modules.player.ReplenishModule;
import we.ketamine.club.features.modules.render.AmbienceModule;
import we.ketamine.club.features.modules.render.BlockHighlightModule;
import we.ketamine.club.features.modules.render.BreadcrumbsModule;
import we.ketamine.club.features.modules.render.BreakIndicatorsModule;
import we.ketamine.club.features.modules.render.ChestESPModule;
import we.ketamine.club.features.modules.render.CrystalHandModule;
import we.ketamine.club.features.modules.render.FullbrightModule;
import we.ketamine.club.features.modules.render.LogoutSpotsModule;
import we.ketamine.club.features.modules.render.NametagsModule;
import we.ketamine.club.features.modules.render.NoRenderModule;
import we.ketamine.club.features.modules.render.PopEffectsModule;
import we.ketamine.club.features.modules.render.QuadSupremeModule;
import we.ketamine.club.features.modules.render.SeeThroughModule;
import we.ketamine.club.features.modules.render.ShadersModule;
import we.ketamine.club.features.modules.render.ShulkerPreviewModule;
import we.ketamine.club.features.modules.render.SkyboxModule;
import we.ketamine.club.features.modules.render.TablistModule;
import we.ketamine.club.features.modules.render.ViewModelModule;
import we.ketamine.club.features.modules.slop.ColorChangerForStuffModule;
import we.ketamine.club.features.modules.slop.CrystalChangerModule;
import we.ketamine.club.features.modules.slop.KillEffectsModule;
import we.ketamine.club.features.modules.slop.NoWeatherModule;
import we.ketamine.club.features.modules.world.AutoPortalModule;
import we.ketamine.club.features.modules.world.BomberModule;
import we.ketamine.club.features.modules.world.FastPortalModule;
import we.ketamine.club.features.modules.world.FillerModule;
import we.ketamine.club.features.modules.world.NukerModule;
import we.ketamine.club.features.modules.world.ScaffoldModule;
import we.ketamine.club.features.modules.world.SpeedMine2Module;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.PingBypassRenderState;
import we.ketamine.club.util.traits.Jsonable;
import we.ketamine.club.util.traits.Util;

public class ModuleManager implements Jsonable, Util {
   private static final Logger LOGGER = LoggerFactory.getLogger("ModuleManager");
   private final Map<Class<? extends Module>, Module> fastRegistry = new HashMap<>();
   private final Map<String, Module> nameRegistry = new HashMap<>();
   private final List<Module> modules = new ArrayList<>();

   public ModuleManager() {
   }

   public void init() {
      this.register(new HudClientModule());
      this.register(new ClickGuiModule());
      this.register(new NotificationsModule());
      this.register(new RotationsModule());
      this.register(new TargetsModule());
      this.register(new TeleportLoggerModule());
      this.register(new AutoTrapModule());
      this.register(new AutoWebModule());
      this.register(new OffhandModule());
      this.register(new BootSwapModule());
      this.register(new AutoLogModule());
      this.register(new AutoSwordModule());
      this.register(new MaceAuraModule());
      this.register(new AutoCrystalModule());
      this.register(new AutoBedModule());
      this.register(new AutoAnchorModule());
      this.register(new AutoMineModule());
      this.register(new AutoInfestModule());
      this.register(new AutoXPModule());
      this.register(new VelocityModule());
      this.register(new SprintModule());
      this.register(new ElytraAssistModule());
      this.register(new ElytraModule());
      this.register(new ElytraFlyModule());
      this.register(new ElytraDashModule());
      this.register(new ElytraTestModule());
      this.register(new FakeFlyModule());
      this.register(new BhopModule());
      this.register(new VerticalDesyncModule());
      this.register(new NoSlowModule());
      this.register(new SpeedModule());
      this.register(new AutoPortalModule());
      this.register(new FastPortalModule());
      this.register(new FastuseModule());
      this.register(new FreecamModule());
      this.register(new BlockHighlightModule());
      this.register(new ChestESPModule());
      this.register(new BreadcrumbsModule());
      this.register(new BreakIndicatorsModule());
      this.register(new AutoHorseModule());
      this.register(new EndermanLookModule());
      this.register(new CrystalHandModule());
      this.register(new AmbienceModule());
      this.register(new FullbrightModule());
      this.register(new NoRenderModule());
      this.register(new ShadersModule());
      this.register(new SkyboxModule());
      this.register(new SeeThroughModule());
      this.register(new NametagsModule());
      this.register(new TablistModule());
      this.register(new LogoutSpotsModule());
      this.register(new PopEffectsModule());
      this.register(new ShulkerPreviewModule());
      this.register(new QuadSupremeModule());
      this.register(new ViewModelModule());
      this.register(new MiddleClickExtraModule());
      this.register(new KeyPotionModule());
      this.register(new SurroundModule());
      this.register(new PistonCrystalModule());
      this.register(new PistonPushModule());
      this.register(new PearlBlockerModule());
      this.register(new PhaseModule());
      this.register(new TrickshotModule());
      this.register(new MoFuckerModule());
      this.register(new AutoMinecartModule());
      this.register(new DoohickeyTrapModule());
      this.register(new XCarryModule());
      this.register(new BomberModule());
      this.register(new SpeedMine2Module());
      this.register(new NukerModule());
      this.register(new FillerModule());
      this.register(new ScaffoldModule());
      this.register(new ReplenishModule());
      this.register(new InstantRekitModule());
      this.register(new NoRotateModule());
      this.register(new CrystalChangerModule());
      this.register(new ColorChangerForStuffModule());
      this.register(new KillEffectsModule());
      this.register(new NoWeatherModule());
      LOGGER.info("Registered {} modules", this.modules.size());

      for (Module module : this.modules) {
         KetamineClub.commandManager.register(new ModuleCommand(module));
      }

      KetamineClub.configManager.addConfig(this);
   }

   public void register(Module module) {
      String normalizedName = module.getName().toLowerCase(Locale.ROOT);
      if (!this.fastRegistry.containsKey(module.getClass()) && !this.nameRegistry.containsKey(normalizedName)) {
         this.modules.add(module);
         this.fastRegistry.put((Class<? extends Module>)module.getClass(), module);
         this.nameRegistry.put(normalizedName, module);
      } else {
         throw new IllegalArgumentException("Duplicate module registration: " + module.getName());
      }
   }

   public List<Module> getModules() {
      return Collections.unmodifiableList(this.modules);
   }

   public Stream<Module> stream() {
      return this.getModules().stream();
   }

   public <T extends Module> T getModuleByClass(Class<T> clazz) {
      return (T)this.fastRegistry.get(clazz);
   }

   public SpeedMineController getSpeedMine() {
      return this.getModuleByClass(SpeedMine2Module.class);
   }

   public Module getModuleByName(String name) {
      return name == null ? null : this.nameRegistry.get(name.toLowerCase(Locale.ROOT));
   }

   public Module getModuleByDisplayName(String display) {
      if (display == null) {
         return null;
      } else {
         for (Module module : this.modules) {
            if (module.getDisplayName().equalsIgnoreCase(display)) {
               return module;
            }
         }

         return null;
      }
   }

   public List<Module> getModulesByCategory(Module.Category category) {
      return this.stream().filter(m -> m.getCategory() == category).toList();
   }

   public List<Module.Category> getCategories() {
      return Arrays.asList(Module.Category.values());
   }

   public void onLoad() {
      this.getModules().forEach(Module::onLoad);
   }

   public void onTick() {
      for (Module module : this.modules) {
         if (module.isEnabled()) {
            module.onTick();
         }
      }

      for (Module modulex : this.modules) {
         if (!PingBypass.isWorker() && modulex.isToggled() && modulex.getBindMode() == Module.BindMode.HOLD && !modulex.getBind().isDown()) {
            modulex.disable();
         }
      }
   }

   public void onRender2D(Render2DEvent event) {
      for (Module module : this.modules) {
         if (module.isEnabled()) {
            module.onRender2D(event);
         }
      }

      PingBypassRenderState.render2D(event);
   }

   public void onRender3D(Render3DEvent event) {
      for (Module module : this.modules) {
         if (module.isEnabled()) {
            module.onRender3D(event);
         }
      }

      PingBypassRenderState.render3D(event);
   }

   public void onUnload() {
      this.getModules().forEach(EVENT_BUS::unregister);
      this.getModules().forEach(Module::onUnload);
   }

   public void onKeyPressed(int key) {
      if (key > 0 && mc.gui.screen() == null) {
         this.stream().filter(module -> !(module instanceof ClickGuiModule) && module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
               if (!module.isToggled()) {
                  module.enable();
               }
            } else {
               module.toggle();
            }
         });
      }
   }

   public void onKeyReleased(int key) {
      if (key > 0) {
         this.stream()
            .filter(module -> module.getBind().getKey() == key && module.getBindMode() == Module.BindMode.HOLD && module.isToggled())
            .forEach(Module::disable);
      }
   }

   public void onMousePressed(int button) {
      if (mc.gui.screen() == null) {
         int key = 1000 + button;
         this.stream().filter(module -> module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
               if (!module.isToggled()) {
                  module.enable();
               }
            } else {
               module.toggle();
            }
         });
      }
   }

   public void onMouseReleased(int button) {
      int key = 1000 + button;
      this.stream()
         .filter(module -> module.getBind().getKey() == key && module.getBindMode() == Module.BindMode.HOLD && module.isToggled())
         .forEach(Module::disable);
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Module module : this.getModules()) {
         object.add(module.getName(), module.toJson());
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      for (Module module : this.getModules()) {
         try {
            module.fromJson(element.getAsJsonObject().get(module.getName()));
         } catch (Throwable var5) {
            LOGGER.error("Failed to load module {}", module.getName(), var5);
         }
      }
   }

   @Override
   public String getFileName() {
      return "modules.json";
   }
}
