package we.ketamine.club.features.modules.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudClientModule;
import we.ketamine.club.features.modules.client.HudModule;
import we.ketamine.club.features.modules.client.HudPosition;
import we.ketamine.club.features.modules.render.NametagsModule;

public class RadarHudModule extends HudModule {
   private static final int DEFAULT_POP_COLOR = -44976;
   private static final Identifier LOGO = Identifier.fromNamespaceAndPath("ketamineclub", "textures/font/logo_white.png");
   private static final int LOGO_TEX_W = 16;
   private static final int LOGO_TEX_H = 16;
   private static final int LOGO_ALPHA_WHITE = -1;
   private static final int LOGO_GAP = 3;

   public RadarHudModule() {
      super("Radar");
   }

   public int renderedLineCount() {
      if (mc.player != null && mc.level != null) {
         int count = 1;
         UUID selfId = mc.player.getUUID();

         for (Player player : mc.level.players()) {
            if (!player.getUUID().equals(selfId) && player.isAlive()) {
               count++;
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   @Override
   public void render(Render2DEvent event) {
      if (mc.player != null && mc.level != null) {
         List<RadarHudModule.Entry> entries = new ArrayList<>();
         UUID selfId = mc.player.getUUID();

         for (Player player : mc.level.players()) {
            if (!player.getUUID().equals(selfId) && player.isAlive()) {
               double distanceSquared = mc.player.distanceToSqr(player);
               entries.add(new RadarHudModule.Entry(player, distanceSquared));
            }
         }

         entries.sort(Comparator.<RadarHudModule.Entry>comparingDouble(entryx -> entryx.distanceSquared).reversed());
         entries.add(new RadarHudModule.Entry(mc.player, 0.0));
         if (!entries.isEmpty()) {
            GuiGraphicsExtractor ctx = event.getContext();
            HudClientModule hudClient = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
            int popColor = this.resolvePopColor();
            int lineHeight = 9;
            int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
            int baseY = this.blockTop(HudPosition.BOTTOM_RIGHT, 1, linesBelow, 0);
            int i = entries.size() - 1;

            for (int row = 0; i >= 0; row++) {
               RadarHudModule.Entry entry = entries.get(i);
               String name = entry.player.getGameProfile().name();
               int pops = KetamineClub.playerInfoManager.getTotemPops(entry.player.getUUID());
               String popsStr = pops > 0 ? " -" + pops : "";
               int nameW = mc.font.width(name);
               int popsW = mc.font.width(popsStr);
               int totalW = nameW + popsW;
               int y = baseY - row * lineHeight;
               int x = this.lineX(HudPosition.BOTTOM_RIGHT, totalW);
               int nameColor = this.resolveNameColor(hudClient, entry.player);
               if (entry.player == mc.player) {
                  int logoX = x - lineHeight - 3;
                  int logoY = y + (lineHeight - lineHeight) / 2;
                  ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F, lineHeight, lineHeight, 16, 16, 16, 16, -1);
               }

               ctx.text(mc.font, name, x, y, nameColor);
               if (!popsStr.isEmpty()) {
                  ctx.text(mc.font, popsStr, x + nameW, y, popColor);
               }

               i--;
            }
         }
      }
   }

   private int resolveNameColor(HudClientModule hudClient, Player player) {
      if (hudClient == null) {
         return -1;
      } else if (player == mc.player) {
         return hudClient.radarSelfColor.getValue().getRGB();
      } else {
         return KetamineClub.friendManager.isFriend(player) ? hudClient.radarFriendColor.getValue().getRGB() : hudClient.radarEnemyColor.getValue().getRGB();
      }
   }

   private int resolvePopColor() {
      NametagsModule nametags = KetamineClub.moduleManager.getModuleByClass(NametagsModule.class);
      return nametags != null && nametags.popColor.getValue() != null ? nametags.popColor.getValue().getRGB() : -44976;
   }

   private record Entry(Player player, double distanceSquared) {
   }
}
