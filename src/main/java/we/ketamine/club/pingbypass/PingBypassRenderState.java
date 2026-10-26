package we.ketamine.club.pingbypass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.combat.AutoCrystalModule;
import we.ketamine.club.features.modules.combat.SurroundModule;
import we.ketamine.club.features.modules.world.SpeedMine2Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;
import we.ketamine.club.util.traits.Util;

public final class PingBypassRenderState {
   private static final long STALE_MS = 750L;
   private static long sequence;
   private static long lastApplied;
   private static long lastUpdate;
   private static long worldEpoch;
   private static long snapshotEpoch;
   private static boolean initialized;
   private static final PingBypassRenderState.WorldListener WORLD_LISTENER = new PingBypassRenderState.WorldListener();
   private static final Map<String, PingBypassRenderState.MineEntry> mines = new HashMap<>();
   private static final List<PingBypassRenderState.SurroundEntry> surround = new ArrayList<>();
   private static final List<PingBypassRenderState.CrystalEntry> crystals = new ArrayList<>();

   private PingBypassRenderState() {
   }

   static synchronized void init() {
      if (!initialized) {
         initialized = true;
         Util.EVENT_BUS.register(WORLD_LISTENER);
      }
   }

   public static void reset() {
      clearVisuals();
      sequence = 0L;
      lastApplied = 0L;
      worldEpoch = 0L;
      snapshotEpoch = 0L;
   }

   private static void clearVisuals() {
      mines.clear();
      surround.clear();
      crystals.clear();
      lastUpdate = 0L;
   }

   static void clearModule(String name) {
      switch (name) {
         case "Surround":
            surround.clear();
            break;
         case "AutoCrystal":
            crystals.clear();
            break;
         case "SpeedMine2":
            mines.clear();
      }
   }

   static void worldChanged() {
      worldEpoch++;
      if (snapshotEpoch != worldEpoch) {
         clearVisuals();
      }
   }

   public static JsonObject workerSnapshot(long now) {
      JsonObject result = envelope();
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.level != null && mc.player != null && !mc.player.isDeadOrDying()) {
         JsonArray values = new JsonArray();
         SurroundModule surroundModule = module(SurroundModule.class);
         if (surroundModule != null) {
            for (SurroundModule.PingBypassRenderEntry entry : surroundModule.pingBypassRenderEntries(now)) {
               JsonObject value = position(entry.pos());
               value.addProperty("rocket", entry.rocket());
               value.addProperty("remaining", entry.remainingMs());
               values.add(value);
            }
         }

         result.add("surround", values);
         values = new JsonArray();
         AutoCrystalModule crystalModule = module(AutoCrystalModule.class);
         if (crystalModule != null) {
            for (AutoCrystalModule.PingBypassRenderEntry entry : crystalModule.pingBypassRenderEntries(now)) {
               JsonObject value = position(entry.pos());
               value.addProperty("damage", entry.damage());
               value.addProperty("remaining", entry.remainingMs());
               values.add(value);
            }
         }

         result.add("crystals", values);
         values = new JsonArray();
         SpeedMine2Module mine = module(SpeedMine2Module.class);
         if (mine != null) {
            for (SpeedMine2Module.PingBypassRenderEntry entry : mine.pingBypassRenderEntries()) {
               JsonObject value = position(entry.pos());
               value.addProperty("lane", entry.lane());
               value.addProperty("cycle", entry.cycle());
               value.addProperty("progress", entry.progress());
               value.addProperty("visible", entry.visible());
               AABB box = entry.box();
               JsonArray bounds = new JsonArray();

               for (double coordinate : new double[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ}) {
                  bounds.add(coordinate);
               }

               value.add("box", bounds);
               values.add(value);
            }
         }

         result.add("mines", values);
         return result;
      } else {
         return result;
      }
   }

   private static JsonObject envelope() {
      JsonObject result = new JsonObject();
      result.addProperty("sequence", ++sequence);
      result.addProperty("epoch", worldEpoch);
      JsonObject revisions = new JsonObject();

      for (String name : List.of("Surround", "AutoCrystal", "SpeedMine2")) {
         revisions.addProperty(name, PingBypassModules.revision(name));
      }

      result.add("revisions", revisions);
      result.add("surround", new JsonArray());
      result.add("crystals", new JsonArray());
      result.add("mines", new JsonArray());
      return result;
   }

   public static void apply(JsonObject snapshot) {
      if (PingBypassModules.isViewer()) {
         apply(snapshot, now());
      }
   }

   static void apply(JsonObject snapshot, long received) {
      long incoming = requiredLong(snapshot, "sequence");
      long epoch = requiredLong(snapshot, "epoch");
      if (incoming >= 1L && epoch >= 0L) {
         if (incoming > lastApplied && epoch >= worldEpoch && epoch >= snapshotEpoch) {
            JsonObject versions = object(snapshot.get("revisions"));
            boolean acceptSurround = accepts(versions, "Surround");
            boolean acceptCrystals = accepts(versions, "AutoCrystal");
            boolean acceptMines = accepts(versions, "SpeedMine2");
            JsonArray surroundValues = array(snapshot, "surround", 256);
            JsonArray crystalValues = array(snapshot, "crystals", 128);
            JsonArray mineValues = array(snapshot, "mines", 8);
            List<PingBypassRenderState.SurroundEntry> nextSurround = new ArrayList<>(surroundValues.size());

            for (JsonElement value : surroundValues) {
               JsonObject object = object(value);
               nextSurround.add(new PingBypassRenderState.SurroundEntry(readPos(object), requiredBoolean(object, "rocket"), lifetime(object, "remaining")));
            }

            List<PingBypassRenderState.CrystalEntry> nextCrystals = new ArrayList<>(crystalValues.size());

            for (JsonElement value : crystalValues) {
               JsonObject object = object(value);
               float damage = requiredFloat(object, "damage");
               if (damage < 0.0F) {
                  throw new IllegalArgumentException("Invalid crystal render value");
               }

               nextCrystals.add(new PingBypassRenderState.CrystalEntry(readPos(object), damage, lifetime(object, "remaining")));
            }

            Map<String, PingBypassRenderState.MineEntry> nextMines = new HashMap<>();
            boolean continuous = epoch == snapshotEpoch && fresh(received);
            Module mineModule = KetamineClub.moduleManager.getModuleByName("SpeedMine2");
            float fadeMs = mineModule == null ? 250.0F : number(mineModule, "FadeTime", 250.0F);

            for (JsonElement value : mineValues) {
               JsonObject object = object(value);
               int lane = requiredInt(object, "lane");
               long cycle = requiredLong(object, "cycle");
               if (lane >= 0 && lane <= 1 && cycle >= 0L) {
                  BlockPos pos = readPos(object);
                  float progress = requiredFloat(object, "progress");
                  if (!(progress < 0.0F) && !(progress > 1.0F)) {
                     boolean visible = requiredBoolean(object, "visible");
                     AABB box = readBox(object);
                     String key = pos.asLong() + ":" + cycle;
                     PingBypassRenderState.MineEntry previous = continuous ? mines.get(key) : null;
                     float from = previous != null && !(progress + 1.0E-4F < previous.current) ? previous.progress(received) : 0.0F;
                     float alpha = previous == null ? 0.0F : previous.opacity(received);
                     if (!visible && previous != null) {
                        box = previous.box;
                     }

                     if (nextMines.put(key, new PingBypassRenderState.MineEntry(pos, box, from, progress, received, true, visible, alpha, fadeMs)) != null) {
                        throw new IllegalArgumentException("Duplicate mining render target");
                     }
                     continue;
                  }

                  throw new IllegalArgumentException("Invalid mining render progress");
               }

               throw new IllegalArgumentException("Invalid mining render identity");
            }

            if (continuous && acceptMines) {
               for (Entry<String, PingBypassRenderState.MineEntry> entry : mines.entrySet()) {
                  if (!nextMines.containsKey(entry.getKey())) {
                     PingBypassRenderState.MineEntry fading = entry.getValue().stop(received);
                     if (fading.opacity(received) > 0.004F && nextMines.size() < 64) {
                        nextMines.put(entry.getKey(), fading);
                     }
                  }
               }
            }

            if (!continuous) {
               clearVisuals();
            }

            surround.clear();
            if (acceptSurround) {
               surround.addAll(nextSurround);
            }

            crystals.clear();
            if (acceptCrystals) {
               crystals.addAll(nextCrystals);
            }

            mines.clear();
            if (acceptMines) {
               mines.putAll(nextMines);
            }

            lastApplied = incoming;
            snapshotEpoch = epoch;
            lastUpdate = received;
         }
      } else {
         throw new IllegalArgumentException("Invalid render sequence/epoch");
      }
   }

   private static boolean accepts(JsonObject versions, String name) {
      long revision = requiredLong(versions, name);
      if (revision < 0L) {
         throw new IllegalArgumentException("Invalid render revision");
      } else {
         Module module = KetamineClub.moduleManager.getModuleByName(name);
         return module != null && module.isToggled() && revision == PingBypassModules.revision(name);
      }
   }

   private static long now() {
      return System.nanoTime() / 1000000L;
   }

   static boolean fresh(long now) {
      return lastApplied > 0L && snapshotEpoch == worldEpoch && now - lastUpdate <= 750L;
   }

   private static boolean renderReady(long now) {
      if (!PingBypassModules.isViewer()) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.level != null && mc.player != null && !mc.player.isDeadOrDying() ? fresh(now) : false;
      }
   }

   public static void render3D(Render3DEvent event) {
      long now = now();
      if (renderReady(now)) {
         SurroundModule surroundModule = module(SurroundModule.class);
         if (surroundModule != null) {
            renderSurround(event, surroundModule, now - lastUpdate);
         }

         AutoCrystalModule crystalModule = module(AutoCrystalModule.class);
         if (crystalModule != null) {
            renderCrystals(event, crystalModule, now - lastUpdate);
         }

         SpeedMine2Module mine = module(SpeedMine2Module.class);
         if (mine != null) {
            for (PingBypassRenderState.MineEntry entry : mines.values()) {
               renderMine(event, mine, entry, now);
            }
         }
      }
   }

   public static void render2D(Render2DEvent event) {
      long now = now();
      if (renderReady(now)) {
         AutoCrystalModule module = module(AutoCrystalModule.class);
         if (module != null && module.isToggled() && bool(module, "DamageText")) {
            float life = Math.max(1.0F, number(module, "RenderTime", 10.0F) * 50.0F);
            GuiGraphicsExtractor graphics = event.getContext();
            Minecraft minecraft = Minecraft.getInstance();
            boolean customFont = ((Enum)module.getSettingByName("DamageTextFont").getValue()).name().equals("CLICKGUI");
            int height = customFont ? Fonts.lineHeight() : 9;

            for (PingBypassRenderState.CrystalEntry entry : crystals) {
               if (!(entry.damage <= 0.0F) && entry.remaining > now - lastUpdate) {
                  float[] screen = MatrixCapture.worldToScreen(entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5);
                  if (screen != null) {
                     String text = String.format(Locale.ROOT, "%.1f", entry.damage);
                     int width = customFont ? Fonts.width(text) : minecraft.font.width(text);
                     Color color = withAlpha(color(module, "DamageTextColor"), (float)(entry.remaining - (now - lastUpdate)) / life);
                     graphics.pose().pushMatrix();
                     graphics.pose().translate(screen[0], screen[1]);
                     float scale = number(module, "DamageTextScale", 1.0F);
                     graphics.pose().scale(scale, scale);
                     if (customFont) {
                        if (bool(module, "DamageTextShadow")) {
                           Fonts.drawString(graphics, text, -width / 2.0F + 0.7F, -height / 2.0F + 0.7F, new Color(0, 0, 0, color.getAlpha()).getRGB());
                        }

                        Fonts.drawString(graphics, text, -width / 2.0F, -height / 2.0F, color.getRGB());
                     } else {
                        graphics.text(minecraft.font, text, -width / 2, -height / 2, color.getRGB(), bool(module, "DamageTextShadow"));
                     }

                     graphics.pose().popMatrix();
                  }
               }
            }
         }
      }
   }

   private static void renderSurround(Render3DEvent event, SurroundModule module, long age) {
      if (module.isToggled() && bool(module, "Render")) {
         float life = number(module, "FadeTime", 0.2F) * 1000.0F;

         for (PingBypassRenderState.SurroundEntry entry : surround) {
            box(
               event,
               new AABB(entry.pos),
               color(module, entry.rocket ? "RocketFillColor" : "FillColor"),
               color(module, entry.rocket ? "RocketOutlineColor" : "OutlineColor"),
               (float)(entry.remaining - age) / Math.max(1.0F, life),
               1.0F
            );
         }
      }
   }

   private static void renderCrystals(Render3DEvent event, AutoCrystalModule module, long age) {
      if (module.isToggled() && bool(module, "RenderBox")) {
         float life = number(module, "RenderTime", 10.0F) * 50.0F;

         for (PingBypassRenderState.CrystalEntry entry : crystals) {
            box(
               event,
               new AABB(entry.pos),
               color(module, "FillColor"),
               color(module, "OutlineColor"),
               (float)(entry.remaining - age) / Math.max(1.0F, life),
               number(module, "OutlineWidth", 1.5F)
            );
         }
      }
   }

   private static void renderMine(Render3DEvent event, SpeedMine2Module module, PingBypassRenderState.MineEntry entry, long now) {
      if (module.isToggled() && bool(module, "Render")) {
         float alpha = entry.opacity(now);
         if (!(alpha <= 0.004F)) {
            alpha = alpha * alpha * (3.0F - 2.0F * alpha);
            float progress = entry.progress(now);
            AABB bounds = animate(entry.box, progress, module);
            Color fillStart = color(module, "FillStart");
            Color outlineStart = color(module, "OutlineStart");
            Color fill = bool(module, "DynamicColor") ? mix(fillStart, color(module, "FillEnd"), progress) : fillStart;
            Color outline = bool(module, "DynamicColor") ? mix(outlineStart, color(module, "OutlineEnd"), progress) : outlineStart;
            box(event, bounds, bool(module, "Fill") ? fill : null, bool(module, "Outline") ? outline : null, alpha, number(module, "LineWidth", 1.5F));
         }
      }
   }

   private static AABB animate(AABB box, float progress, Module module) {
      String mode = String.valueOf(module.getSettingByName("Animation").getValue());

      return switch (mode) {
         case "None" -> box;
         case "In" -> scaleBox(box, 1.0F - progress);
         case "InOut" -> progress >= 0.5F ? scaleBox(box, (progress - 0.5F) * 2.0F) : scaleBox(box, 1.0F - progress * 2.0F);
         case "OutIn" -> progress >= 0.5F ? scaleBox(box, 1.0F - (progress - 0.5F) * 2.0F) : scaleBox(box, progress * 2.0F);
         default -> scaleBox(box, progress);
      };
   }

   private static AABB scaleBox(AABB box, float value) {
      value = value * value * (3.0F - 2.0F * value);
      Vec3 center = box.getCenter();
      return new AABB(
         lerp(center.x, box.minX, value),
         lerp(center.y, box.minY, value),
         lerp(center.z, box.minZ, value),
         lerp(center.x, box.maxX, value),
         lerp(center.y, box.maxY, value),
         lerp(center.z, box.maxZ, value)
      );
   }

   private static double lerp(double from, double to, float amount) {
      return from + (to - from) * amount;
   }

   private static void box(Render3DEvent event, AABB bounds, Color fill, Color outline, float alpha, float width) {
      if (!(alpha <= 0.0F)) {
         if (fill != null) {
            RenderUtil.drawBoxFilled(event.getMatrix(), bounds, withAlpha(fill, alpha));
         }

         if (outline != null) {
            RenderUtil.drawBox(event.getMatrix(), bounds, withAlpha(outline, alpha), width);
         }
      }
   }

   private static Color mix(Color from, Color to, float progress) {
      return new Color(
         (int)Math.round(lerp(from.getRed(), to.getRed(), progress)),
         (int)Math.round(lerp(from.getGreen(), to.getGreen(), progress)),
         (int)Math.round(lerp(from.getBlue(), to.getBlue(), progress)),
         (int)Math.round(lerp(from.getAlpha(), to.getAlpha(), progress))
      );
   }

   private static Color withAlpha(Color color, float alpha) {
      alpha = Math.clamp(alpha, 0.0F, 1.0F);
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp((long)Math.round(color.getAlpha() * alpha), 0, 255));
   }

   static float interpolate(float previous, float current, long elapsedMs) {
      return previous + (current - previous) * Math.clamp((float)elapsedMs / 50.0F, 0.0F, 1.0F);
   }

   private static JsonObject position(BlockPos pos) {
      JsonObject value = new JsonObject();
      value.addProperty("x", pos.getX());
      value.addProperty("y", pos.getY());
      value.addProperty("z", pos.getZ());
      return value;
   }

   private static BlockPos readPos(JsonObject value) {
      return new BlockPos(requiredInt(value, "x"), requiredInt(value, "y"), requiredInt(value, "z"));
   }

   private static AABB readBox(JsonObject value) {
      JsonElement element = value.get("box");
      if (element != null && element.isJsonArray() && element.getAsJsonArray().size() == 6) {
         double[] c = new double[6];

         for (int i = 0; i < c.length; i++) {
            c[i] = requiredDouble(element.getAsJsonArray().get(i), "box");
         }

         return new AABB(c[0], c[1], c[2], c[3], c[4], c[5]);
      } else {
         throw new IllegalArgumentException("Invalid mining render box");
      }
   }

   private static double requiredDouble(JsonElement value, String key) {
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
         double result = value.getAsDouble();
         if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Invalid render " + key);
         } else {
            return result;
         }
      } else {
         throw new IllegalArgumentException("Invalid render " + key);
      }
   }

   private static JsonArray array(JsonObject object, String key, int maximum) {
      JsonElement value = object.get(key);
      if (value != null && value.isJsonArray() && value.getAsJsonArray().size() <= maximum) {
         return value.getAsJsonArray();
      } else {
         throw new IllegalArgumentException("Invalid render array");
      }
   }

   private static JsonObject object(JsonElement value) {
      if (value != null && value.isJsonObject()) {
         return value.getAsJsonObject();
      } else {
         throw new IllegalArgumentException("Invalid render entry");
      }
   }

   private static long lifetime(JsonObject object, String key) {
      long value = requiredLong(object, key);
      if (value >= 0L && value <= 10000L) {
         return value;
      } else {
         throw new IllegalArgumentException("Invalid render lifetime");
      }
   }

   private static float requiredFloat(JsonObject object, String key) {
      float result = (float)requiredDouble(object.get(key), key);
      if (!Float.isFinite(result)) {
         throw new IllegalArgumentException("Invalid render " + key);
      } else {
         return result;
      }
   }

   private static int requiredInt(JsonObject object, String key) {
      return Math.toIntExact(requiredLong(object, key));
   }

   private static long requiredLong(JsonObject object, String key) {
      JsonElement value = object.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
         try {
            return value.getAsBigDecimal().longValueExact();
         } catch (NumberFormatException | ArithmeticException var4) {
            throw new IllegalArgumentException("Invalid render " + key, var4);
         }
      } else {
         throw new IllegalArgumentException("Invalid render " + key);
      }
   }

   private static boolean requiredBoolean(JsonObject object, String key) {
      JsonElement value = object.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
         return value.getAsBoolean();
      } else {
         throw new IllegalArgumentException("Invalid render " + key);
      }
   }

   private static boolean bool(Module module, String key) {
      Setting<?> setting = module.getSettingByName(key);
      return setting != null && Boolean.TRUE.equals(setting.getValue());
   }

   private static float number(Module module, String key, float fallback) {
      Setting<?> setting = module.getSettingByName(key);
      return setting != null && setting.getValue() instanceof Number value ? value.floatValue() : fallback;
   }

   private static Color color(Module module, String key) {
      Setting<?> setting = module.getSettingByName(key);
      return setting != null && setting.getValue() instanceof Color value ? value : Color.WHITE;
   }

   private static <T extends Module> T module(Class<T> type) {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(type);
   }

   private record CrystalEntry(BlockPos pos, float damage, long remaining) {
   }

   private record MineEntry(BlockPos pos, AABB box, float previous, float current, long received, boolean active, boolean visible, float alpha, float fadeMs) {
      float progress(long now) {
         return PingBypassRenderState.interpolate(this.previous, this.current, now - this.received);
      }

      float opacity(long now) {
         float step = this.fadeMs <= 0.0F ? 1.0F : (float)Math.max(0L, now - this.received) / this.fadeMs;
         return Math.clamp(this.alpha + (this.visible ? step : -step), 0.0F, 1.0F);
      }

      PingBypassRenderState.MineEntry stop(long now) {
         return this.active
            ? new PingBypassRenderState.MineEntry(this.pos, this.box, this.progress(now), this.current, now, false, false, this.opacity(now), this.fadeMs)
            : this;
      }
   }

   private record SurroundEntry(BlockPos pos, boolean rocket, long remaining) {
   }

   private static final class WorldListener {
      private WorldListener() {
      }

      @Subscribe
      private void packet(PacketEvent.ReceivePost event) {
         if (PingBypass.isWorker() || PingBypassModules.isViewer()) {
            if (event.getPacket() instanceof ClientboundLoginPacket
               || event.getPacket() instanceof ClientboundRespawnPacket
               || event.getPacket() instanceof ClientboundStartConfigurationPacket) {
               PingBypassRenderState.worldChanged();
            }
         }
      }
   }
}
