package we.ketamine.club.features.modules.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import java.awt.Color;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.ClientEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.ColorUtil;
import we.ketamine.club.util.render.font.Fonts;

public class ClickGuiModule extends Module {
   private static ClickGuiModule INSTANCE;
   private static KeyMapping keyMapping;
   public Setting<String> prefix = this.str("Prefix", ".");
   public Setting<ClickGuiModule.Theme> theme = this.mode("Theme", ClickGuiModule.Theme.KETAMINECLUB);
   public Setting<ClickGuiModule.Style> style = this.mode("Style", ClickGuiModule.Style.DEFAULT);
   public Setting<Color> customColor = this.color("Custom Color", 159, 194, 216, 255);
   public Setting<Boolean> smooth = this.bool("Smooth", true);
   public Setting<Integer> rainbowHue = this.num("Delay", 240, 0, 600);
   public Setting<Float> rainbowBrightness = this.num("Brightness", 200.0F, 1.0F, 255.0F);
   public Setting<Float> rainbowSaturation = this.num("Saturation", 140.0F, 1.0F, 255.0F);
   public Setting<Boolean> clickGuiFont = this.bool("ClickGUI Font", false);
   public Setting<Float> clickGuiFontScale = this.num("Font Scale", 1.0F, 0.5F, 1.5F);
   public Setting<Boolean> hudFont = this.bool("HUD Font", false);
   public Setting<Boolean> lowercase = this.bool("Lowercase", false);
   public Setting<String> fontName = this.str("Font Name", "");
   private static final Color CAT_COMBAT = new Color(196, 88, 90);
   private static final Color CAT_WORLD = new Color(118, 168, 118);
   private static final Color CAT_RENDER = new Color(98, 166, 200);
   private static final Color CAT_MOVEMENT = new Color(212, 142, 78);
   private static final Color CAT_PLAYER = new Color(202, 180, 92);
   private static final Color CAT_FUNNY = new Color(198, 124, 178);
   private static final Color CAT_SLOP = new Color(142, 176, 110);
   private static final Color CAT_CLIENT = new Color(70, 75, 82);
   private static final Color CAT_HUD = new Color(96, 142, 200);
   private static final Color KETAMINECLUB_ACCENT = new Color(159, 194, 216);

   public Color categoryAccent(Module.Category cat) {
      return this.categoryAccent(cat, 0.0F);
   }

   public Color categoryAccent(Module.Category cat, float yOffset) {
      ClickGuiModule.Theme t = this.theme.getValue();
      if (t == ClickGuiModule.Theme.RAINBOW) {
         return this.rainbowAt(yOffset);
      } else if (t == ClickGuiModule.Theme.CUSTOM) {
         return this.customColor.getValue();
      } else if (t == ClickGuiModule.Theme.KETAMINECLUB) {
         return KETAMINECLUB_ACCENT;
      } else if (cat == null) {
         return CAT_CLIENT;
      } else {
         switch (cat) {
            case COMBAT:
               return CAT_COMBAT;
            case WORLD:
               return CAT_WORLD;
            case RENDER:
               return CAT_RENDER;
            case MOVEMENT:
               return CAT_MOVEMENT;
            case PLAYER:
               return CAT_PLAYER;
            case FUNNY:
               return CAT_FUNNY;
            case SLOP:
               return CAT_SLOP;
            case CLIENT:
               return CAT_CLIENT;
            case HUD:
               return CAT_HUD;
            default:
               return CAT_CLIENT;
         }
      }
   }

   public Color moduleAccent(Module.Category cat, float yOffset) {
      return this.categoryAccent(cat, yOffset);
   }

   public Color chatAccent() {
      ClickGuiModule.Theme t = this.theme.getValue();
      if (t == ClickGuiModule.Theme.RAINBOW) {
         return this.rainbowAt(0.0F);
      } else if (t == ClickGuiModule.Theme.CUSTOM) {
         return this.customColor.getValue();
      } else {
         return t == ClickGuiModule.Theme.KETAMINECLUB ? KETAMINECLUB_ACCENT : Color.WHITE;
      }
   }

   public Color rainbowAt(float yOffset) {
      return ColorUtil.rainbow((int)(yOffset / 10.0F * this.rainbowHue.getValue().intValue()));
   }

   public float getExpandSpeed() {
      return this.smooth.getValue() ? 0.5F : 1.0F;
   }

   public ClickGuiModule() {
      super("ClickGui", "Opens the ClickGui", Module.Category.CLIENT);
      this.bind.setValue(new Bind(344));
      this.bind.setVisibility(v -> true);
      this.bindMode.setVisibility(v -> false);
      this.rainbowHue.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowBrightness.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.rainbowSaturation.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.RAINBOW);
      this.customColor.setVisibility(v -> this.theme.getValue() == ClickGuiModule.Theme.CUSTOM);
      this.clickGuiFontScale.setVisibility(v -> this.clickGuiFont.getValue());
      this.fontName.setVisibility(v -> this.clickGuiFont.getValue() || this.hudFont.getValue());
      INSTANCE = this;
   }

   @Subscribe
   public void onSettingChange(ClientEvent event) {
      if (event.getType() == ClientEvent.Type.SETTING_UPDATE && event.getSetting().getFeature().equals(this)) {
         if (event.getSetting().equals(this.prefix)) {
            if (!KetamineClub.commandManager.setCommandPrefix(this.prefix.getPlannedValue())) {
               event.cancel();
               Command.sendMessage("{red} Prefix must be nonblank, contain no whitespace, and not start with '/'");
               return;
            }

            Command.sendMessage("Prefix set to {global} %s", KetamineClub.commandManager.getCommandPrefix());
         }

         if (event.getSetting().equals(this.clickGuiFont) || event.getSetting().equals(this.hudFont) || event.getSetting().equals(this.fontName)) {
            Fonts.markDirty();
         }
      }
   }

   @Override
   public void onEnable() {
      if (!nullCheck()) {
         mc.gui.setScreen(KetamineClubGui.getClickGui());
      }
   }

   @Override
   public void onLoad() {
      applyModuleBind();
      KetamineClub.colorManager.register("ui", this::chatAccent);
      KetamineClub.colorManager.register("chat", this::chatAccent);
      KetamineClub.colorManager.register("chatBracket", this::chatAccent);
      if (!KetamineClub.commandManager.setCommandPrefix(this.prefix.getValue())) {
         this.prefix.setValueNoEvent(".");
         KetamineClub.commandManager.setCommandPrefix(".");
      }
   }

   @Override
   public void onTick() {
      if (!(mc.gui.screen() instanceof KetamineClubGui)) {
         this.disable();
      }
   }

   public static ClickGuiModule getInstance() {
      return INSTANCE;
   }

   public static String applyTextCase(String text) {
      return text != null && INSTANCE != null && INSTANCE.lowercase.getValue() ? text.toLowerCase(Locale.ROOT) : text;
   }

   public static void registerKeyMapping() {
      if (keyMapping == null) {
         net.minecraft.client.KeyMapping.Category category = net.minecraft.client.KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("ketamineclub", "ketamineclub")
         );
         keyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ketamineclub.open_gui", Type.KEYSYM, 344, category));
      }
   }

   public static boolean matchesKey(int key) {
      return keyMapping != null && keyMapping.matches(new KeyEvent(key, 0, 0));
   }

   public static void syncModuleBind() {
      if (INSTANCE != null && keyMapping != null) {
         INSTANCE.bind.setValue(new Bind(KeyMappingHelper.getBoundKeyOf(keyMapping).getValue()));
      }
   }

   private static void applyModuleBind() {
      if (INSTANCE != null && keyMapping != null) {
         keyMapping.setKey(Type.KEYSYM.getOrCreate(INSTANCE.bind.getValue().getKey()));
         KeyMapping.resetMapping();
      }
   }

   public static enum Style {
      DEFAULT,
      FLAT;

      private Style() {
      }
   }

   public static enum Theme {
      KETAMINECLUB,
      COLORS,
      CUSTOM,
      RAINBOW;

      private Theme() {
      }
   }
}
