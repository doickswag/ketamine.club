package we.ketamine.club.features.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.gui.items.Item;
import we.ketamine.club.features.gui.items.SearchBar;
import we.ketamine.club.features.gui.items.TextBox;
import we.ketamine.club.features.gui.items.buttons.ColorButton;
import we.ketamine.club.features.gui.items.buttons.ModuleButton;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.util.render.GuiFade;

public class KetamineClubGui extends Screen {
   private static KetamineClubGui INSTANCE = new KetamineClubGui();
   private static Color colorClipboard = null;
   private float alpha = 0.0F;
   private long openTime = 0L;
   private boolean closing = false;
   private long closeTime = 0L;
   private static final long FADE_DURATION = 150L;
   private final ArrayList<Widget> widgets = new ArrayList<>();
   private SearchBar searchBar;

   public KetamineClubGui() {
      super(Component.literal("ketamine.club"));
      this.setInstance();
      this.load();
   }

   public static KetamineClubGui getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new KetamineClubGui();
      }

      return INSTANCE;
   }

   public static KetamineClubGui getClickGui() {
      return getInstance();
   }

   private void setInstance() {
      INSTANCE = this;
   }

   private void load() {
      int spacing = 120;
      int x = this.firstPanelX() - spacing;

      for (Module.Category category : KetamineClub.moduleManager.getCategories()) {
         if (category != Module.Category.HUD && (category != Module.Category.FUNNY || KetamineClub.commandManager.isFunnyVisible())) {
            Widget panel = new Widget(category.getName(), category, x += spacing, 12, true);
            KetamineClub.moduleManager.stream().filter(m -> m.getCategory() == category && !m.hidden).map(ModuleButton::new).forEach(panel::addButton);
            this.widgets.add(panel);
         }
      }

      this.widgets.forEach(components -> components.getItems().sort(Comparator.comparing(Feature::getName)));

      for (Widget panel : this.widgets) {
         if (panel.getCategory() == Module.Category.CLIENT) {
            this.searchBar = new SearchBar();
            panel.getItems().add(0, this.searchBar);
            break;
         }
      }
   }

   public void init() {
      super.init();
      this.arrangeCompact();
      this.openTime = System.currentTimeMillis();
      this.closing = false;
      this.alpha = 0.0F;
      GuiNavigator.reset();
   }

   public void onClose() {
      if (!this.closing) {
         this.closing = true;
         this.closeTime = System.currentTimeMillis();
         if (this.searchBar != null) {
            this.searchBar.clear();
         }

         GuiNavigator.reset();
      }
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      long now = System.currentTimeMillis();
      if (!this.closing) {
         this.alpha = Math.min(1.0F, (float)(now - this.openTime) / 150.0F);
      } else {
         this.alpha = Math.max(0.0F, 1.0F - (float)(now - this.closeTime) / 150.0F);
         if (this.alpha <= 0.0F) {
            this.minecraft.gui.setScreen(null);
            return;
         }
      }

      GuiFade.alpha = this.alpha;
      Item.context = context;
      context.fill(0, 0, context.guiWidth(), context.guiHeight(), GuiFade.apply(GuiTheme.SCREEN_DIM));
      float s = getScale();
      int sx = (int)(mouseX / s);
      int sy = (int)(mouseY / s);
      context.pose().pushMatrix();
      context.pose().scale(s, s);
      this.widgets.forEach(components -> components.drawScreen(context, sx, sy, delta));
      context.pose().popMatrix();
      GuiFade.alpha = 1.0F;
   }

   public static float getScale() {
      if (Minecraft.getInstance().getWindow() != null && KetamineClub.moduleManager != null) {
         int visiblePanels = 0;

         for (Module.Category category : KetamineClub.moduleManager.getCategories()) {
            if (category != Module.Category.HUD && (category != Module.Category.FUNNY || KetamineClub.commandManager.isFunnyVisible())) {
               visiblePanels++;
            }
         }

         if (visiblePanels <= 1) {
            return 1.0F;
         } else {
            float requiredWidth = 6.0F + visiblePanels * 114 + (visiblePanels - 1) * 6;
            float availableWidth = Math.max(1.0F, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 12.0F);
            return Math.max(0.45F, Math.min(1.0F, availableWidth / requiredWidth));
         }
      } else {
         return 1.0F;
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.closing) {
         return true;
      } else {
         float s = getScale();
         int mx = (int)(click.x() / s);
         int my = (int)(click.y() / s);
         this.widgets.forEach(components -> components.mouseClicked(mx, my, click.button()));
         return super.mouseClicked(click, doubled);
      }
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.closing) {
         return true;
      } else {
         float s = getScale();
         int mx = (int)(click.x() / s);
         int my = (int)(click.y() / s);
         this.widgets.forEach(components -> components.mouseReleased(mx, my, click.button()));
         return super.mouseReleased(click);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.closing) {
         return true;
      } else {
         float s = getScale();
         int mx = (int)(mouseX / s);
         int my = (int)(mouseY / s);

         for (Widget widget : this.widgets) {
            if (widget.isHoveringBody(mx, my)) {
               widget.scroll(verticalAmount);
               return true;
            }
         }

         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public boolean keyPressed(KeyEvent input) {
      if (this.closing) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyPressed(input.input());
         return true;
      } else if (GuiNavigator.handleKey(input.input())) {
         return true;
      } else if (input.input() == ClickGuiModule.getInstance().bind.getValue().getKey()) {
         if (System.currentTimeMillis() - this.openTime > 50L) {
            this.onClose();
         }

         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyPressed(input.input()));
         return super.keyPressed(input);
      }
   }

   public boolean charTyped(CharacterEvent input) {
      if (this.closing) {
         return true;
      } else if (TextBox.hasActiveFocus()) {
         TextBox.routeKeyTyped(input.codepointAsString());
         return true;
      } else {
         this.widgets.forEach(component -> component.onKeyTyped(input.codepointAsString(), 0));
         return super.charTyped(input);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
   }

   public void reload() {
      this.widgets.clear();
      this.load();
   }

   public void setPanelState(String categoryName, Integer x, Integer y, Boolean open) {
      Widget widget = this.findPanel(categoryName);
      if (widget != null) {
         if (x != null && y != null) {
            widget.setPosition(x, y);
         }

         if (open != null) {
            widget.setOpen(open);
         }
      }
   }

   public void arrangeCompact() {
      int x = this.firstPanelX();

      for (Widget widget : this.widgets) {
         widget.setPosition(x, 12);
         x += 120;
      }
   }

   public void collapsePanelsExcept(String categoryName) {
      String wanted = normalize(categoryName);

      for (Widget widget : this.widgets) {
         widget.setOpen(wanted.isEmpty() || normalize(widget.getName()).equals(wanted));
      }
   }

   public boolean setModuleOpen(String moduleName, boolean open) {
      ModuleButton button = this.findModuleButton(moduleName);
      if (button == null) {
         return false;
      } else {
         button.setSubOpen(open);
         return true;
      }
   }

   public boolean setColorPickerOpen(String moduleName, String settingName, boolean open) {
      ModuleButton moduleButton = this.findModuleButton(moduleName);
      if (moduleButton == null) {
         return false;
      } else {
         for (Item item : moduleButton.getSettingItems()) {
            if (item instanceof ColorButton colorButton && normalize(item.getName()).equals(normalize(settingName))) {
               moduleButton.setSubOpen(true);
               colorButton.setOpen(open);
               return true;
            }
         }

         return false;
      }
   }

   public Widget findPanel(String categoryName) {
      String wanted = normalize(categoryName);

      for (Widget widget : this.widgets) {
         if (normalize(widget.getName()).equals(wanted)) {
            return widget;
         }

         if (widget.getCategory() != null && normalize(widget.getCategory().name()).equals(wanted)) {
            return widget;
         }
      }

      return null;
   }

   public ModuleButton findModuleButton(String moduleName) {
      String wanted = normalize(moduleName);

      for (Widget widget : this.widgets) {
         for (Item item : widget.getItems()) {
            if (item instanceof ModuleButton moduleButton && normalize(moduleButton.getModule().getName()).equals(wanted)) {
               return moduleButton;
            }
         }
      }

      return null;
   }

   private static String normalize(String value) {
      return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
   }

   public final ArrayList<Widget> getComponents() {
      return this.widgets;
   }

   private int firstPanelX() {
      if (KetamineClub.moduleManager != null && Minecraft.getInstance().getWindow() != null) {
         int panelCount = 0;

         for (Module.Category category : KetamineClub.moduleManager.getCategories()) {
            if (category != Module.Category.HUD && (category != Module.Category.FUNNY || KetamineClub.commandManager.isFunnyVisible())) {
               panelCount++;
            }
         }

         int totalWidth = panelCount * 114 + Math.max(0, panelCount - 1) * 6;
         float scale = getScale();
         int virtualWidth = (int)(Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale);
         return Math.max(6, (virtualWidth - totalWidth) / 2);
      } else {
         return 6;
      }
   }

   public int getTextOffset() {
      return -6;
   }

   public static Color getColorClipboard() {
      return colorClipboard;
   }

   public static void setColorClipboard(Color color) {
      colorClipboard = color;
   }
}
