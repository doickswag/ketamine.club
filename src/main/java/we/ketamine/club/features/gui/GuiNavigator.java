package we.ketamine.club.features.gui;

import java.util.ArrayList;
import java.util.List;
import we.ketamine.club.features.gui.items.Item;
import we.ketamine.club.features.gui.items.buttons.BindButton;
import we.ketamine.club.features.gui.items.buttons.ModuleButton;

public final class GuiNavigator {
   private static boolean active;
   private static int panelIndex;
   private static Item selected;
   private static BindButton pendingBind;

   private GuiNavigator() {
   }

   public static void reset() {
      active = false;
      panelIndex = 0;
      selected = null;
      pendingBind = null;
   }

   public static boolean isSelected(Item item) {
      return active && item != null && item == selected;
   }

   public static boolean isFocusedPanel(Widget panel) {
      return active && panel != null && panel == focusedPanel();
   }

   public static Item getSelected() {
      return active ? selected : null;
   }

   public static boolean handleKey(int key) {
      if (pendingBind != null) {
         if (pendingBind.isListening) {
            pendingBind.onKeyPressed(key);
            if (!pendingBind.isListening) {
               pendingBind = null;
            }

            return true;
         }

         pendingBind = null;
      }

      boolean arrow = key == 263 || key == 262 || key == 265 || key == 264;
      if (!active) {
         if (!arrow) {
            return false;
         } else {
            active = true;
            selectFirst();
            return true;
         }
      } else {
         switch (key) {
            case 32:
               expandSelected();
               return true;
            case 256:
               active = false;
               selected = null;
               return true;
            case 257:
            case 335:
               activateSelected();
               return true;
            case 262:
               horizontal(1);
               return true;
            case 263:
               horizontal(-1);
               return true;
            case 264:
               move(1);
               return true;
            case 265:
               move(-1);
               return true;
            default:
               return false;
         }
      }
   }

   private static void selectFirst() {
      List<Item> rows = flatList();
      selected = rows.isEmpty() ? null : rows.get(0);
   }

   private static void move(int dir) {
      List<Item> rows = flatList();
      if (rows.isEmpty()) {
         selected = null;
      } else {
         int idx = rows.indexOf(selected);
         if (idx < 0) {
            selected = rows.get(0);
         } else {
            idx = Math.max(0, Math.min(rows.size() - 1, idx + dir));
            selected = rows.get(idx);
         }
      }
   }

   private static void horizontal(int dir) {
      ModuleButton owner = owningMultiPageModule();
      if (owner != null) {
         owner.cyclePage(dir);
         List<Item> settings = owner.currentPageVisibleSettings();
         selected = (Item)(settings.isEmpty() ? owner : settings.get(0));
      } else {
         List<Widget> panels = visiblePanels();
         if (!panels.isEmpty()) {
            float targetY = selected != null ? selected.getY() : Float.NEGATIVE_INFINITY;
            panelIndex = Math.max(0, Math.min(panels.size() - 1, clampPanelIndex() + dir));
            selectNearestY(targetY);
         }
      }
   }

   private static void selectNearestY(float targetY) {
      List<Item> rows = flatList();
      if (rows.isEmpty()) {
         selected = null;
      } else if (targetY == Float.NEGATIVE_INFINITY) {
         selected = rows.get(0);
      } else {
         Item best = rows.get(0);
         float bestDist = Float.MAX_VALUE;

         for (Item row : rows) {
            float dist = Math.abs(row.getY() - targetY);
            if (dist < bestDist) {
               bestDist = dist;
               best = row;
            }
         }

         selected = best;
      }
   }

   private static void activateSelected() {
      if (selected != null) {
         selected.keyActivate();
         if (selected instanceof BindButton b && b.isListening) {
            pendingBind = b;
         }
      }
   }

   private static void expandSelected() {
      if (selected instanceof ModuleButton mb) {
         mb.toggleSubOpen();
      }
   }

   private static List<Widget> visiblePanels() {
      List<Widget> out = new ArrayList<>();

      for (Widget w : KetamineClubGui.getClickGui().getComponents()) {
         if (!w.isHidden()) {
            out.add(w);
         }
      }

      return out;
   }

   private static int clampPanelIndex() {
      int size = visiblePanels().size();
      return size == 0 ? 0 : Math.max(0, Math.min(size - 1, panelIndex));
   }

   private static Widget focusedPanel() {
      List<Widget> panels = visiblePanels();
      if (panels.isEmpty()) {
         return null;
      } else {
         panelIndex = clampPanelIndex();
         return panels.get(panelIndex);
      }
   }

   private static List<Item> flatList() {
      List<Item> rows = new ArrayList<>();
      Widget panel = focusedPanel();
      if (panel == null) {
         return rows;
      } else {
         for (Item item : panel.getItems()) {
            if (!item.isHidden()) {
               rows.add(item);
               if (item instanceof ModuleButton mb && mb.isSubOpen()) {
                  rows.addAll(mb.currentPageVisibleSettings());
               }
            }
         }

         return rows;
      }
   }

   private static ModuleButton owningMultiPageModule() {
      Widget panel = focusedPanel();
      if (panel != null && selected != null) {
         for (Item item : panel.getItems()) {
            if (item instanceof ModuleButton mb && mb.isSubOpen() && mb.hasMultiplePages() && mb.currentPageVisibleSettings().contains(selected)) {
               return mb;
            }
         }

         return null;
      } else {
         return null;
      }
   }
}
