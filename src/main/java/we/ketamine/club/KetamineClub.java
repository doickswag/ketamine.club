package we.ketamine.club;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStarted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys.Client;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import we.ketamine.club.features.GuiMove;
import we.ketamine.club.features.gui.GuiDevBridge;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.features.modules.render.FullbrightModule;
import we.ketamine.club.manager.ColorManager;
import we.ketamine.club.manager.CommandManager;
import we.ketamine.club.manager.ConfigManager;
import we.ketamine.club.manager.EnemyManager;
import we.ketamine.club.manager.EventManager;
import we.ketamine.club.manager.FriendManager;
import we.ketamine.club.manager.ModuleManager;
import we.ketamine.club.manager.PlacementManager;
import we.ketamine.club.manager.PlayerInfoManager;
import we.ketamine.club.manager.PositionManager;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.manager.TPSCounterService;
import we.ketamine.club.manager.TurtleMasterManager;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.util.TextUtil;
import we.ketamine.club.util.render.AmbienceRenderer;
import we.ketamine.club.util.render.HandShaderChain;
import we.ketamine.club.util.render.HandShaderRender;
import we.ketamine.club.util.render.SkyboxRenderer;
import we.ketamine.club.util.render.WorldChamsChain;
import we.ketamine.club.util.render.font.Fonts;

public class KetamineClub implements ModInitializer, ClientModInitializer {
   public static final Identifier AUTOHORSE_SOUND_ID = Identifier.fromNamespaceAndPath("ketamineclub", "autohorse");
   public static final SoundEvent AUTOHORSE_SOUND = SoundEvent.createVariableRangeEvent(AUTOHORSE_SOUND_ID);
   private static final Identifier RESOURCE_RELOADER_ID = Identifier.fromNamespaceAndPath("ketamineclub", "render_resources");
   public static final Logger LOGGER = LogManager.getLogger("ketamine.club");
   public static ColorManager colorManager;
   public static PositionManager positionManager;
   public static EventManager eventManager;
   public static CommandManager commandManager;
   public static FriendManager friendManager;
   public static EnemyManager enemyManager;
   public static PlayerInfoManager playerInfoManager;
   public static TurtleMasterManager turtleMasterManager;
   public static ModuleManager moduleManager;
   public static ConfigManager configManager;
   public static PlacementManager placementManager;
   public static RotationManager rotationManager;
   public static SwapManager swapManager;
   public static TPSCounterService tpsCounterService;
   public static GuiMove guiMove;
   private static boolean clientStarted;
   private static boolean clientStopping;

   public KetamineClub() {
   }

   public void onInitialize() {
      LOGGER.info("Pre-initializing {} v{}", "ketamine.club", "1.1.1");
      Registry.register(BuiltInRegistries.SOUND_EVENT, AUTOHORSE_SOUND_ID, AUTOHORSE_SOUND);
      configManager = new ConfigManager();
      eventManager = new EventManager();
      positionManager = new PositionManager();
      friendManager = new FriendManager();
      enemyManager = new EnemyManager();
      playerInfoManager = new PlayerInfoManager();
      turtleMasterManager = new TurtleMasterManager();
      colorManager = new ColorManager();
      commandManager = new CommandManager();
      moduleManager = new ModuleManager();
      placementManager = new PlacementManager();
      rotationManager = new RotationManager();
      swapManager = new SwapManager();
      tpsCounterService = new TPSCounterService();
      guiMove = new GuiMove();
      TextUtil.init();
   }

   public void onInitializeClient() {
      LOGGER.info("Initializing {}", "ketamine.club");
      long startTime = System.nanoTime();
      ClickGuiModule.registerKeyMapping();
      eventManager.init();
      rotationManager.init();
      swapManager.init();
      commandManager.init();
      moduleManager.init();
      friendManager.init();
      enemyManager.init();
      playerInfoManager.init();
      turtleMasterManager.init();
      tpsCounterService.init();
      guiMove.init();
      ResourceLoader resourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
      resourceLoader.registerReloadListener(RESOURCE_RELOADER_ID, (ResourceManagerReloadListener)resourceManager -> cleanupRenderResources());
      resourceLoader.addListenerOrdering(Client.FONTS, RESOURCE_RELOADER_ID);
      resourceLoader.addListenerOrdering(Client.SHADERS, RESOURCE_RELOADER_ID);
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientStarted)client -> finishClientStartup(startTime));
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> stopClient());
   }

   private static void finishClientStartup(long startTime) {
      if (!clientStarted) {
         clientStarted = true;
         configManager.load();
         if (!commandManager.isFunnyVisible()) {
            moduleManager.stream().filter(module -> module.getCategory() == Module.Category.FUNNY).filter(Module::isEnabled).forEach(Module::disable);
         }

         moduleManager.onLoad();
         GuiDevBridge.init();
         PingBypass.get().init();
         LOGGER.info("Initialized {} in {}ms", "ketamine.club", (System.nanoTime() - startTime) / 1000000.0);
      }
   }

   private static void stopClient() {
      if (!clientStopping) {
         clientStopping = true;
         cleanup("PingBypass session", () -> PingBypass.get().close());
         cleanup("config persistence", configManager::save);
         cleanup("gamma restoration", () -> disableModule(FullbrightModule.class));
         cleanupRenderResources();
      }
   }

   private static <T extends Module> void disableModule(Class<T> type) {
      if (moduleManager != null) {
         T module = moduleManager.getModuleByClass(type);
         if (module != null && module.isEnabled()) {
            module.disable();
         }
      }
   }

   private static void cleanupRenderResources() {
      cleanup("custom fonts", Fonts::cleanup);
      cleanup("hand render target", HandShaderRender::cleanup);
      cleanup("hand post chain", HandShaderChain::cleanup);
      cleanup("world post chain", WorldChamsChain::cleanup);
      cleanup("skybox buffers", SkyboxRenderer::cleanup);
      cleanup("ambience buffers", AmbienceRenderer::cleanup);
   }

   private static void cleanup(String description, Runnable action) {
      try {
         action.run();
      } catch (RuntimeException var3) {
         LOGGER.error("Failed to clean up {}", description, var3);
      }
   }
}
