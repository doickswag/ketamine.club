package we.ketamine.club.features.modules.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.font.Fonts;
import we.ketamine.club.util.traits.Jsonable;

public class NametagsModule extends Module {
   public Setting<Boolean> showPearls = this.bool("ShowPearls", true).setPage("Info");
   public Setting<Boolean> showItems = this.bool("ShowItems", true).setPage("Info");
   public Setting<Boolean> showArmor = this.bool("ShowArmor", true).setPage("Info");
   public Setting<Boolean> showDist = this.bool("ShowDist", true).setPage("Info");
   public Setting<Boolean> showPops = this.bool("ShowPops", true).setPage("Info");
   public Setting<Boolean> showTurtle = this.bool("ShowTurtle", true).setPage("Info");
   public Setting<Boolean> turtleTimer = this.bool("TurtleTimer", true).setPage("Info");
   public Setting<Color> nameColor = this.color("NameColor", 255, 255, 255, 255).setPage("Colors");
   public Setting<Color> friendColor = this.color("FriendColor", 0, 255, 100, 255).setPage("Colors");
   public Setting<Color> enemyColor = this.color("EnemyColor", 255, 60, 60, 255).setPage("Colors");
   public Setting<Color> bgColor = this.color("BgColor", 0, 0, 0, 128).setPage("Colors");
   public Setting<Color> distColor = this.color("DistColor", 170, 170, 170, 255).setPage("Colors");
   public Setting<Color> popColor = this.color("PopColor", 255, 80, 80, 255).setPage("Colors");
   public Setting<Color> turtleColor = this.color("TurtleColor", 90, 210, 130, 255).setPage("Colors");
   public Setting<Float> gap = this.num("Gap", 1.0F, 0.1F, 15.0F).setPage("Render");
   public Setting<Float> armorGap = this.num("ArmorGap", 2.0F, 0.0F, 20.0F).setPage("Render");
   public Setting<Float> scale = this.num("Scale", 1.0F, 0.1F, 3.0F).setPage("Render");
   public Setting<Float> minScale = this.num("MinScale", 0.3F, 0.1F, 1.0F).setPage("Render");
   public Setting<Boolean> itemsOnArmor = this.bool("ItemsOnArmor", false).setPage("Render");
   private static final long FIVE_DAYS_MS = 432000000L;
   private final Map<UUID, NametagsModule.PearlEntry> pearlOwnerCache = new HashMap<>();
   private final Jsonable pearlCacheJson = new Jsonable() {
      {
         Objects.requireNonNull(NametagsModule.this);
      }

      @Override
      public JsonElement toJson() {
         JsonObject root = new JsonObject();
         long now = System.currentTimeMillis();

         for (Entry<UUID, NametagsModule.PearlEntry> e : NametagsModule.this.pearlOwnerCache.entrySet()) {
            if (now - e.getValue().lastSeenMs() <= 432000000L) {
               JsonObject obj = new JsonObject();
               obj.addProperty("owner", e.getValue().ownerName());
               obj.addProperty("lastSeen", e.getValue().lastSeenMs());
               root.add(e.getKey().toString(), obj);
            }
         }

         return root;
      }

      @Override
      public void fromJson(JsonElement element) {
         if (element != null && !element.isJsonNull()) {
            long now = System.currentTimeMillis();

            for (Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
               try {
                  JsonObject obj = e.getValue().getAsJsonObject();
                  String owner = obj.get("owner").getAsString();
                  long lastSeen = obj.get("lastSeen").getAsLong();
                  if (now - lastSeen <= 432000000L) {
                     NametagsModule.this.pearlOwnerCache.put(UUID.fromString(e.getKey()), new NametagsModule.PearlEntry(owner, lastSeen));
                  }
               } catch (Exception var10) {
               }
            }
         }
      }

      @Override
      public String getFileName() {
         return "pearl_owners.json";
      }
   };
   static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

   public NametagsModule() {
      super("Nametags", "Renders custom nametags above players", Module.Category.RENDER);
      KetamineClub.configManager.addConfig(this.pearlCacheJson);
      this.turtleTimer.setVisibility(v -> this.showTurtle.getValue());
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (!nullCheck()) {
         if (MatrixCapture.projection != null) {
            GuiGraphicsExtractor graphics = event.getContext();
            float delta = event.getDelta();

            record RenderJob(double distSq, Runnable draw) {
            }

            List<RenderJob> jobs = new ArrayList<>();

            for (Player player : mc.level.players()) {
               if (player != mc.player && player.isAlive()) {
                  double px = player.xo + (player.getX() - player.xo) * delta;
                  double py = player.yo + (player.getY() - player.yo) * delta + player.getBbHeight() + this.gap.getValue().floatValue() * 0.5;
                  double pz = player.zo + (player.getZ() - player.zo) * delta;
                  double dist = mc.player.position().distanceTo(player.position());
                  boolean isEnemy = KetamineClub.enemyManager.isEnemy(player);
                  boolean isFriend = !isEnemy && KetamineClub.friendManager.isFriend(player);
                  int nameArgb = (isEnemy ? this.enemyColor.getValue() : (isFriend ? this.friendColor.getValue() : this.nameColor.getValue())).getRGB();
                  String secondaryStr = this.showDist.getValue() ? " " + (int)dist + "m" : "";
                  int pops = KetamineClub.playerInfoManager.getTotemPops(player.getUUID());
                  Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);

                  for (EquipmentSlot slot : ARMOR_SLOTS) {
                     armor.put(slot, KetamineClub.playerInfoManager.getEquipment(player, slot));
                  }

                  ItemStack mainHand = KetamineClub.playerInfoManager.getMainHandItem(player);
                  ItemStack offHand = KetamineClub.playerInfoManager.getOffHandItem(player);
                  String name = player.getGameProfile().name();
                  int turtleSeconds = -1;
                  if (this.showTurtle.getValue() && KetamineClub.turtleMasterManager.hasTurtleMaster(player)) {
                     turtleSeconds = KetamineClub.turtleMasterManager.getRemainingSeconds(player.getUUID());
                  }

                  int turtle = turtleSeconds;
                  jobs.add(
                     new RenderJob(
                        dist * dist, () -> this.renderNametag(graphics, px, py, pz, dist, name, nameArgb, secondaryStr, pops, turtle, armor, mainHand, offHand)
                     )
                  );
               }
            }

            if (this.showPearls.getValue()) {
               long now = System.currentTimeMillis();
               this.pearlOwnerCache.entrySet().removeIf(ex -> now - ((NametagsModule.PearlEntry)ex.getValue()).lastSeenMs() > 432000000L);

               for (Entity e : mc.level.entitiesForRendering()) {
                  if (e instanceof ThrownEnderpearl pearl && pearl.tickCount > 2) {
                     UUID uuid = pearl.getUUID();
                     if (pearl.getOwner() instanceof Player thrower) {
                        this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(thrower.getGameProfile().name(), now));
                     } else {
                        NametagsModule.PearlEntry existing = this.pearlOwnerCache.get(uuid);
                        if (existing != null) {
                           this.pearlOwnerCache.put(uuid, new NametagsModule.PearlEntry(existing.ownerName(), now));
                        }
                     }

                     NametagsModule.PearlEntry entry = this.pearlOwnerCache.get(uuid);
                     if (entry != null) {
                        double px = pearl.xo + (pearl.getX() - pearl.xo) * delta;
                        double py = pearl.yo + (pearl.getY() - pearl.yo) * delta + pearl.getBbHeight() + this.gap.getValue().floatValue() * 0.5;
                        double pz = pearl.zo + (pearl.getZ() - pearl.zo) * delta;
                        double dist = mc.player.position().distanceTo(pearl.position());
                        boolean isEnemy = KetamineClub.enemyManager.isEnemy(entry.ownerName());
                        boolean isFriend = !isEnemy && KetamineClub.friendManager.isFriend(entry.ownerName());
                        int nameArgb = (isEnemy ? this.enemyColor.getValue() : (isFriend ? this.friendColor.getValue() : this.nameColor.getValue())).getRGB();
                        String ownerName = entry.ownerName();
                        jobs.add(new RenderJob(dist * dist, () -> this.renderPearlTag(graphics, px, py, pz, dist, ownerName, nameArgb)));
                     }
                  }
               }
            }

            jobs.sort(Comparator.comparingDouble(RenderJob::distSq).reversed());

            for (RenderJob job : jobs) {
               job.draw.run();
            }
         }
      }
   }

   private void renderPearlTag(GuiGraphicsExtractor graphics, double wx, double wy, double wz, double dist, String name, int nameArgb) {
      Fonts.beginHudPass();
      Fonts.preserveHudCase(true);

      try {
         float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
         if (screen != null) {
            float anchorX = screen[0];
            float anchorY = screen[1];
            float s = (float)Math.max((double)this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
            int nameW = mc.font.width(name);
            int halfW = nameW / 2;
            int textH = 9;
            int textTopY = -textH;
            graphics.pose().pushMatrix();
            graphics.pose().translate(anchorX, anchorY);
            graphics.pose().scale(s, s);
            graphics.fill(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
            graphics.text(mc.font, name, -halfW, textTopY, nameArgb);
            graphics.pose().popMatrix();
            return;
         }
      } finally {
         Fonts.endHudPass();
      }
   }

   public void renderNametag(
      GuiGraphicsExtractor graphics,
      double wx,
      double wy,
      double wz,
      double dist,
      String name,
      int nameArgb,
      String secondaryStr,
      int totemPops,
      Map<EquipmentSlot, ItemStack> armor,
      ItemStack mainHand,
      ItemStack offHand
   ) {
      this.renderNametag(graphics, wx, wy, wz, dist, name, nameArgb, secondaryStr, totemPops, -1, armor, mainHand, offHand);
   }

   public void renderNametag(
      GuiGraphicsExtractor graphics,
      double wx,
      double wy,
      double wz,
      double dist,
      String name,
      int nameArgb,
      String secondaryStr,
      int totemPops,
      int turtleSeconds,
      Map<EquipmentSlot, ItemStack> armor,
      ItemStack mainHand,
      ItemStack offHand
   ) {
      Fonts.beginHudPass();
      Fonts.preserveHudCase(true);

      try {
         float[] screen = MatrixCapture.worldToScreen(wx, wy, wz);
         if (screen != null) {
            float anchorX = screen[0];
            float anchorY = screen[1];
            float s = (float)Math.max((double)this.minScale.getValue().floatValue(), this.scale.getValue().floatValue() * 8.0 / (dist + 8.0));
            int nameW = mc.font.width(name);
            int secondaryW = mc.font.width(secondaryStr);
            String popsStr = this.showPops.getValue() && totemPops > 0 ? " -" + totemPops : "";
            int popsW = mc.font.width(popsStr);
            int textH = 9;
            boolean turtle = turtleSeconds >= 0;
            int turtleIconW = turtle ? textH + 2 : 0;
            String turtleStr = turtle && this.turtleTimer.getValue() && turtleSeconds < Integer.MAX_VALUE ? " " + turtleSeconds + "s" : "";
            int turtleStrW = mc.font.width(turtleStr);
            int totalW = turtleIconW + nameW + secondaryW + popsW + turtleStrW;
            int halfW = totalW / 2;
            int textTopY = -textH;
            graphics.pose().pushMatrix();
            graphics.pose().translate(anchorX, anchorY);
            graphics.pose().scale(s, s);
            graphics.fill(-halfW - 2, textTopY - 1, halfW + 2, 1, this.bgColor.getValue().getRGB());
            int cursorX = -halfW;
            if (turtle) {
               graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(MobEffects.RESISTANCE), cursorX, textTopY, textH, textH);
               cursorX += turtleIconW;
            }

            graphics.text(mc.font, name, cursorX, textTopY, nameArgb);
            cursorX += nameW;
            if (!secondaryStr.isEmpty()) {
               graphics.text(mc.font, secondaryStr, cursorX, textTopY, this.distColor.getValue().getRGB());
            }

            cursorX += secondaryW;
            if (!popsStr.isEmpty()) {
               graphics.text(mc.font, popsStr, cursorX, textTopY, this.popColor.getValue().getRGB());
            }

            cursorX += popsW;
            if (!turtleStr.isEmpty()) {
               graphics.text(mc.font, turtleStr, cursorX, textTopY, this.turtleColor.getValue().getRGB());
            }

            if (this.showArmor.getValue()) {
               boolean hasArmor = false;

               for (EquipmentSlot slot : ARMOR_SLOTS) {
                  if (!armor.get(slot).isEmpty()) {
                     hasArmor = true;
                     break;
                  }
               }

               if (hasArmor) {
                  int slotSize = 16;
                  int armorTopY = textTopY - 1 - this.armorGap.getValue().intValue() - slotSize;

                  for (int i = 0; i < 4; i++) {
                     ItemStack armorStack = armor.get(ARMOR_SLOTS[i]);
                     int slotX = -(slotSize * 4) / 2 + i * slotSize;
                     graphics.pose().pushMatrix();
                     graphics.pose().translate(slotX, armorTopY);
                     graphics.item(armorStack, 0, 0);
                     graphics.itemDecorations(mc.font, armorStack, 0, 0);
                     graphics.pose().popMatrix();
                  }
               }
            }

            if (this.showItems.getValue()) {
               int slotSize = 16;
               int itemY;
               int offX;
               int mainX;
               if (this.itemsOnArmor.getValue()) {
                  itemY = textTopY - 1 - this.armorGap.getValue().intValue() - slotSize;
                  offX = -(slotSize * 4) / 2 - slotSize;
                  mainX = slotSize * 4 / 2;
               } else {
                  itemY = textTopY + textH / 2 - slotSize / 2;
                  offX = -halfW - 2 - slotSize;
                  mainX = halfW + 2;
               }

               if (!offHand.isEmpty()) {
                  graphics.item(offHand, offX, itemY);
                  graphics.itemDecorations(mc.font, offHand, offX, itemY);
               }

               if (!mainHand.isEmpty()) {
                  graphics.item(mainHand, mainX, itemY);
                  graphics.itemDecorations(mc.font, mainHand, mainX, itemY);
               }
            }

            graphics.pose().popMatrix();
            return;
         }
      } finally {
         Fonts.endHudPass();
      }
   }

   private record PearlEntry(String ownerName, long lastSeenMs) {
   }
}
