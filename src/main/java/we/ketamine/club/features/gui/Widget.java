package we.ketamine.club.features.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.gui.items.Item;
import we.ketamine.club.features.gui.items.buttons.Button;
import we.ketamine.club.features.gui.items.buttons.ModuleButton;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.util.render.GuiFade;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.ScissorUtil;
import we.ketamine.club.util.render.font.Fonts;

public class Widget extends Feature {
   public static Color currentAccent = null;
   public static Module.Category currentCategory = null;
   private static float currentClipTop = Float.NEGATIVE_INFINITY;
   private static float currentClipBottom = Float.POSITIVE_INFINITY;
   public static final int PANEL_WIDTH = 114;
   public static final int HEADER_HEIGHT = 16;
   private final Module.Category category;
   protected GuiGraphicsExtractor context;
   private final List<Item> items = new ArrayList<>();
   public boolean drag;
   private int x;
   private int y;
   private int x2;
   private int y2;
   private int width;
   private int height;
   private boolean open;
   private boolean hidden = false;
   private float animatedHeight = 0.0F;
   private float scrollOffset = 0.0F;

   public Widget(String name, Module.Category category, int x, int y, boolean open) {
      super(name);
      this.category = category;
      this.x = x;
      this.y = y;
      this.width = 114;
      this.height = 16;
      this.open = open;
   }

   public Widget(String name, int x, int y, boolean open) {
      this(name, null, x, y, open);
   }

   public Color accent() {
      return GuiTheme.categoryColor(this.category);
   }

   public Color accentAt(float yOffset) {
      return GuiTheme.categoryColor(this.category, yOffset);
   }

   public Module.Category getCategory() {
      return this.category;
   }

   public static boolean isWithinCurrentClip(float top, float bottom) {
      return bottom > currentClipTop && top < currentClipBottom;
   }

   private void drag(int mouseX, int mouseY) {
      if (this.drag) {
         this.x = this.x2 + mouseX;
         this.y = this.y2 + mouseY;
      }
   }

   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      this.context = context;
      Item.context = context;
      this.drag(mouseX, mouseY);
      int headerTop = this.y;
      int headerBottom = this.y + this.height;
      int bodyTop = headerBottom;
      int screenHeight = (int)(mc.getWindow().getGuiScaledHeight() / KetamineClubGui.getScale());
      int availableHeight = Math.max(0, screenHeight - headerBottom - 6);
      float contentHeight = this.getTotalItemHeight();
      float bodyCapHeight = contentHeight > 0.0F ? 3.0F : 0.0F;
      float targetHeight = this.open ? Math.min(contentHeight + bodyCapHeight, (float)availableHeight) : 0.0F;
      this.animatedHeight = this.animatedHeight + (targetHeight - this.animatedHeight) * ClickGuiModule.getInstance().getExpandSpeed();
      if (Math.abs(this.animatedHeight - targetHeight) < 0.5F) {
         this.animatedHeight = targetHeight;
      }

      Color cat = this.accentAt(headerTop);
      boolean flat = GuiTheme.isFlat();
      boolean bodyVisible = this.animatedHeight > 0.5F;
      boolean headerHover = this.isHovering(mouseX, mouseY) || this.drag;
      RenderUtil.rect(
         context,
         (float)this.x,
         (float)headerTop,
         (float)(this.x + this.width),
         (float)headerBottom,
         headerHover ? GuiTheme.PANEL_HEADER_HOVER : (flat ? GuiTheme.BODY_BG_SOFT : GuiTheme.PANEL_HEADER_BG)
      );
      String title = this.truncateText(this.getName(), this.width - 14);
      int textY = headerTop + 4 + 1;
      this.drawString(title, this.x + 6.0F, textY, -1);
      String caret = this.open ? "-" : "+";
      this.drawString(caret, this.x + this.width - 8, textY, -1);
      if (bodyVisible) {
         int scissorBottom = (int)(headerBottom + this.animatedHeight);
         int contentBottom = Math.max(headerBottom, scissorBottom - 3);
         float visibleContentHeight = Math.max(0, contentBottom - headerBottom);
         float maxScroll = Math.max(0.0F, contentHeight - visibleContentHeight);
         this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
         this.scrollSelectionIntoView(visibleContentHeight, maxScroll);
         RenderUtil.rect(
            context, (float)this.x, (float)headerBottom, (float)(this.x + this.width), (float)scissorBottom, flat ? GuiTheme.BODY_BG_SOFT : GuiTheme.BODY_BG
         );
         if (!flat) {
            RenderUtil.rect(context, (float)this.x, headerBottom - 1.0F, (float)(this.x + this.width), (float)headerBottom, GuiTheme.OUTLINE);
         }

         ScissorUtil.enable(context, this.x, headerBottom, this.x + this.width, contentBottom);
         currentCategory = this.category;
         currentClipTop = headerBottom;
         currentClipBottom = contentBottom;
         float yCursor = headerBottom - this.scrollOffset;

         for (Item item : this.getItems()) {
            if (!item.isHidden()) {
               item.setLocation(this.x, yCursor);
               item.setWidth(this.width);
               if (item.getY() + item.getHeight() > bodyTop && item.getY() < contentBottom) {
                  currentAccent = GuiTheme.moduleColor(this.category, yCursor);
                  boolean allowOverflow = item.isHovering(mouseX, mouseY) && item.getY() >= bodyTop && item.getY() + item.getHeight() <= contentBottom;
                  if (allowOverflow) {
                     ScissorUtil.disable(context);
                  }

                  item.drawScreen(context, mouseX, mouseY, partialTicks);
                  if (allowOverflow) {
                     ScissorUtil.enable(context);
                  }

                  if (GuiNavigator.isSelected(item) && !(item instanceof ModuleButton)) {
                     Button.drawSelectionRing(context, this.x, item.getY(), this.x + this.width, item.getY() + item.getRowHeight() - 1.0F, currentAccent);
                  }
               }

               yCursor += item.getHeight();
            }
         }

         currentAccent = null;
         currentCategory = null;
         currentClipTop = Float.NEGATIVE_INFINITY;
         currentClipBottom = Float.POSITIVE_INFINITY;
         ScissorUtil.disable(context);
         if (!flat) {
            RenderUtil.rect(context, (float)this.x, (float)headerTop, (float)(this.x + this.width), (float)scissorBottom, GuiTheme.OUTLINE, 1.0F);
         }

         boolean anyAnimating = false;

         for (Item itemx : this.getItems()) {
            if (itemx instanceof ModuleButton mb && mb.isAnimating()) {
               anyAnimating = true;
               break;
            }
         }

         float trackTop = bodyTop + 2.0F;
         float trackBottom = contentBottom - 2.0F;
         float trackH = trackBottom - trackTop;
         if (maxScroll > 0.5F && !anyAnimating && trackH >= 8.0F) {
            float thumbH = Math.max(8.0F, trackH * (visibleContentHeight / contentHeight));
            float thumbY = trackTop + (trackH - thumbH) * (this.scrollOffset / maxScroll);
            RenderUtil.rect(context, this.x + this.width - 3.0F, trackTop, this.x + this.width - 1.0F, trackBottom, GuiTheme.CONTROL_BG);
            int thumbColor = GuiTheme.withAlpha(cat, 210).getRGB();
            RenderUtil.rect(context, this.x + this.width - 3.0F, thumbY, this.x + this.width - 1.0F, thumbY + thumbH, thumbColor);
         }
      } else if (!flat) {
         RenderUtil.rect(context, (float)this.x, (float)headerTop, (float)(this.x + this.width), (float)headerBottom, GuiTheme.OUTLINE, 1.0F);
      }

      RenderUtil.rect(
         context,
         flat ? this.x : this.x + 1.0F,
         headerTop + 1.0F,
         flat ? this.x + this.width : this.x + this.width - 1.0F,
         headerTop + 2.0F,
         GuiTheme.withAlpha(cat, 220).getRGB()
      );
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         this.x2 = this.x - mouseX;
         this.y2 = this.y - mouseY;
         KetamineClubGui.getClickGui().getComponents().forEach(c -> {
            if (c.drag) {
               c.drag = false;
            }
         });
         this.drag = true;
      } else if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
         this.open = !this.open;
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      } else if (this.open) {
         int bodyTop = this.y + this.height;
         int contentBottom = Math.max(bodyTop, (int)(bodyTop + this.animatedHeight) - 3);
         this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < contentBottom) {
               item.mouseClicked(mouseX, mouseY, mouseButton);
            }
         });
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
      if (releaseButton == 0) {
         this.drag = false;
      }

      if (this.open) {
         int bodyTop = this.y + this.height;
         int contentBottom = Math.max(bodyTop, (int)(bodyTop + this.animatedHeight) - 3);
         this.getItems().forEach(item -> {
            if (item.getY() + item.getHeight() > bodyTop && item.getY() < contentBottom) {
               item.mouseReleased(mouseX, mouseY, releaseButton);
            }
         });
      }
   }

   public void onKeyTyped(String typedChar, int keyCode) {
      if (this.open) {
         this.getItems().forEach(item -> item.onKeyTyped(typedChar, keyCode));
      }
   }

   public void onKeyPressed(int key) {
      if (this.open) {
         this.getItems().forEach(item -> item.onKeyPressed(key));
      }
   }

   public void addButton(Button button) {
      this.items.add(button);
   }

   public int getX() {
      return this.x;
   }

   public void setX(int x) {
      this.x = x;
   }

   public int getY() {
      return this.y;
   }

   public void setY(int y) {
      this.y = y;
   }

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public int getHeight() {
      return this.height;
   }

   public void setHeight(int height) {
      this.height = height;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   public final List<Item> getItems() {
      return this.items;
   }

   public boolean isHoveringBody(int mouseX, int mouseY) {
      if (this.open && !(this.animatedHeight <= 0.0F)) {
         int bodyTop = this.y + this.height;
         int scissorBottom = (int)(bodyTop + this.animatedHeight);
         return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= bodyTop && mouseY <= scissorBottom;
      } else {
         return false;
      }
   }

   public void scroll(double amount) {
      float visibleContentHeight = Math.max(0.0F, this.animatedHeight - 3.0F);
      float maxScroll = Math.max(0.0F, this.getTotalItemHeight() - visibleContentHeight);
      this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset - (float)amount * 12.0F, maxScroll));
   }

   private void scrollSelectionIntoView(float visibleHeight, float maxScroll) {
      if (GuiNavigator.isFocusedPanel(this)) {
         Item sel = GuiNavigator.getSelected();
         if (sel != null) {
            Float selTop = null;
            int selHeight = 0;
            float contentTop = 0.0F;

            for (Item item : this.getItems()) {
               if (!item.isHidden()) {
                  if (item == sel) {
                     selTop = contentTop;
                     selHeight = item.getRowHeight();
                  }

                  if (item instanceof ModuleButton mb && mb.isSubOpen()) {
                     float settingTop = contentTop + 14.0F + (mb.hasMultiplePages() ? 10 : 0);

                     for (Item setting : mb.currentPageVisibleSettings()) {
                        if (setting == sel) {
                           selTop = settingTop;
                           selHeight = setting.getRowHeight();
                        }

                        settingTop += setting.getHeight();
                     }
                  }

                  contentTop += item.getHeight();
               }
            }

            if (selTop != null) {
               if (selTop < this.scrollOffset) {
                  this.scrollOffset = selTop;
               } else if (selTop + selHeight > this.scrollOffset + visibleHeight) {
                  this.scrollOffset = selTop + selHeight - visibleHeight;
               }

               this.scrollOffset = Math.max(0.0F, Math.min(this.scrollOffset, maxScroll));
            }
         }
      }
   }

   public boolean isHovering(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
   }

   private float getTotalItemHeight() {
      float h = 0.0F;

      for (Item item : this.getItems()) {
         if (!item.isHidden()) {
            h += item.getHeight();
         }
      }

      return h;
   }

   private String truncateText(String text, int maxWidth) {
      if (Fonts.width(text) <= maxWidth) {
         return text;
      } else {
         String ellipsis = "...";
         int ellipsisWidth = Fonts.width(ellipsis);
         if (maxWidth <= ellipsisWidth) {
            return ellipsis;
         } else {
            int targetWidth = maxWidth - ellipsisWidth;
            StringBuilder sb = new StringBuilder();

            for (char c : text.toCharArray()) {
               if (Fonts.width(sb.toString() + c) > targetWidth) {
                  break;
               }

               sb.append(c);
            }

            return sb + ellipsis;
         }
      }
   }

   protected void drawString(String text, double x, double y, int color) {
      Fonts.drawString(this.context, text, (float)x, (float)y, GuiFade.apply(color));
   }
}
