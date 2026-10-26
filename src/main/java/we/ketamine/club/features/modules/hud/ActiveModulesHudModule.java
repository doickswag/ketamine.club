package we.ketamine.club.features.modules.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.HudClientModule;
import we.ketamine.club.features.modules.client.HudModule;
import we.ketamine.club.features.modules.client.HudPosition;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.pingbypass.PingBypassModules;
import we.ketamine.club.util.ColorUtil;
import we.ketamine.club.util.traits.Jsonable;

public class ActiveModulesHudModule extends HudModule implements Jsonable {
   private static final int STACK_GAP = 2;
   private static final int GRAY = -5592406;
   private static ActiveModulesHudModule INSTANCE;
   private final LinkedHashSet<String> entries = new LinkedHashSet<>();

   public ActiveModulesHudModule() {
      super("ActiveModules");
      INSTANCE = this;
      KetamineClub.configManager.addConfig(this);
   }

   public static ActiveModulesHudModule getInstance() {
      return INSTANCE;
   }

   public boolean add(String name) {
      Module module = KetamineClub.moduleManager.getModuleByName(name);
      return module == null ? false : this.entries.add(module.getName());
   }

   public boolean remove(String name) {
      Module module = KetamineClub.moduleManager.getModuleByName(name);
      String key = module != null ? module.getName() : name;
      return this.entries.removeIf(e -> e.equalsIgnoreCase(key));
   }

   public void clear() {
      this.entries.clear();
   }

   public List<String> getEntries() {
      return new ArrayList<>(this.entries);
   }

   public List<Module> getVisibleEntries() {
      HudClientModule hudClient = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
      List<Module> visible = new ArrayList<>();

      for (String name : this.entries) {
         Module module = KetamineClub.moduleManager.getModuleByName(name);
         if (module != null) {
            visible.add(module);
         }
      }

      if (hudClient != null && hudClient.activeModulesMode.getValue() != HudClientModule.ActiveModulesMode.CURRENT) {
         visible.removeIf(modulex -> !modulex.isToggled());
         List<Module> sorted = new ArrayList<>(visible);
         if (hudClient.activeModulesSorting.getValue() == HudClientModule.ModuleListSorting.ALPHABETICAL) {
            sorted.sort(Comparator.comparing(modulex -> modulex.getDisplayName().toLowerCase(Locale.ROOT)));
         } else {
            sorted.sort(Comparator.comparingInt(this::sydneyWidth).reversed());
         }

         return sorted;
      } else {
         return visible;
      }
   }

   @Override
   public void render(Render2DEvent event) {
      GuiGraphicsExtractor ctx = event.getContext();
      HudClientModule hudClient = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
      List<Module> modules = this.getVisibleEntries();
      if (!modules.isEmpty()) {
         int activeColor = this.currentColor(hudClient);
         HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.CENTER_RIGHT;
         int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
         int gap = pos.isBottom() && linesBelow > 0 ? 2 : 0;
         int y = this.blockTop(pos, modules.size(), linesBelow, gap);

         for (int index = 0; index < modules.size(); index++) {
            Module module = modules.get(index);
            boolean sydney = hudClient != null && hudClient.activeModulesMode.getValue() == HudClientModule.ActiveModulesMode.TRADITIONAL;
            String display = module.getDisplayName();
            String meta = sydney ? this.sydneyMeta(module, hudClient) : this.currentMeta(module);
            String suffix = sydney ? "" : this.currentBind(module);
            int width = mc.font.width(display) + mc.font.width(meta) + mc.font.width(suffix);
            int x = this.lineX(pos, width);
            int nameColor = sydney ? this.sydneyColor(module, index, hudClient) : (module.isToggled() ? activeColor : -5592406);
            ctx.text(mc.font, display, x, y, nameColor);
            int cursor = x + mc.font.width(display);
            if (!meta.isEmpty()) {
               ctx.text(mc.font, meta, cursor, y, -5592406);
               cursor += mc.font.width(meta);
            }

            if (!suffix.isEmpty()) {
               ctx.text(mc.font, suffix, cursor, y, activeColor);
            }

            y += 9;
         }
      }
   }

   private String currentMeta(Module module) {
      String metaRaw = PingBypassModules.getMeta(module);
      return metaRaw != null && !metaRaw.isBlank() ? " (" + metaRaw + ")" : "";
   }

   private String currentBind(Module module) {
      Bind bind = module.getBind().getKey() > 0 ? module.getBind() : null;
      return bind != null ? " [" + bind + "]" : "";
   }

   private String sydneyMeta(Module module, HudClientModule hudClient) {
      if (!hudClient.activeModulesMetaData.getValue()) {
         return "";
      } else {
         String info = PingBypassModules.displayInfo(module);
         if (info == null || info.isBlank()) {
            info = PingBypassModules.getMeta(module);
         }

         return info != null && !info.isBlank() ? " [" + info + "]" : "";
      }
   }

   private int currentColor(HudClientModule hudClient) {
      return hudClient != null ? hudClient.activeModuleColor.getValue().getRGB() : KetamineClub.colorManager.getAsIntFullAlpha("chat");
   }

   private int sydneyColor(Module module, int index, HudClientModule hudClient) {
      if (hudClient == null) {
         return this.currentColor(null);
      } else {
         return switch ((HudClientModule.ModuleColor)hudClient.activeModulesColor.getValue()) {
            case RAINBOW -> ColorUtil.rainbow(index * 20).getRGB();
            case RANDOM -> this.randomModuleColor(module).getRGB();
            case DEFAULT -> hudClient.activeModuleColor.getValue().getRGB();
         };
      }
   }

   private Color randomModuleColor(Module module) {
      int hash = module.getName().hashCode() * 73244475;
      float hue = (hash & 2147483647) / 2.1474836E9F;
      return Color.getHSBColor(hue, 0.72F, 0.95F);
   }

   private int sydneyWidth(Module module) {
      HudClientModule hudClient = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
      String display = module.getDisplayName();
      String meta = hudClient != null ? this.sydneyMeta(module, hudClient) : "";
      return mc.font.width(display) + mc.font.width(meta);
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      JsonArray array = new JsonArray();

      for (String entry : this.entries) {
         array.add(entry);
      }

      object.add("entries", array);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      this.entries.clear();
      JsonElement arr = element.getAsJsonObject().get("entries");
      if (arr != null && arr.isJsonArray()) {
         for (JsonElement e : arr.getAsJsonArray()) {
            Module module = KetamineClub.moduleManager.getModuleByName(e.getAsString());
            if (module != null) {
               this.entries.add(module.getName());
            }
         }
      }
   }

   @Override
   public String getFileName() {
      return "active_modules_hud.json";
   }
}
