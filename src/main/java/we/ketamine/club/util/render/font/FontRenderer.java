package we.ketamine.club.util.render.font;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import we.ketamine.club.util.render.state.GlyphBatchRenderState;

public class FontRenderer implements Closeable {
   private static final Minecraft mc = Minecraft.getInstance();
   private static final float REF_TEXT_HEIGHT = 8.0F;
   private static final float TARGET_ASCENT = 7.0F;
   private final Map<Identifier, ObjectList<FontRenderer.DrawEntry>> glyphPages = new HashMap<>();
   private final ObjectList<GlyphMap> maps = new ObjectArrayList();
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final int charsPerPage;
   private final int padding;
   private Font[] baseFonts;
   private Font[] fittedFonts;
   private int multiplier;
   private int previousGameScale = -1;
   private int blockPx;
   private boolean initialized;

   public FontRenderer(Font[] fonts) {
      this(fonts, 256, 4);
   }

   public FontRenderer(Font[] fonts, int charactersPerPage, int paddingBetweenCharacters) {
      if (fonts.length == 0) {
         throw new IllegalArgumentException("fonts.length == 0");
      } else if (charactersPerPage <= 4) {
         throw new IllegalArgumentException("charactersPerPage too small");
      } else if (paddingBetweenCharacters < 0) {
         throw new IllegalArgumentException("padding < 0");
      } else {
         this.baseFonts = fonts;
         this.charsPerPage = charactersPerPage;
         this.padding = paddingBetweenCharacters;
         this.init();
      }
   }

   public void drawString(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean dropShadow) {
      this.drawString(graphics, text, x, y, color, dropShadow, 1.0F);
   }

   public void drawString(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean dropShadow, float drawScale) {
      if (text.indexOf(167) < 0) {
         this.drawPlainString(graphics, text, x, y, color, dropShadow, drawScale);
      } else {
         this.drawText(graphics, parse(text, color).getVisualOrderText(), x, y, color, dropShadow, drawScale);
      }
   }

   public void drawText(GuiGraphicsExtractor graphics, FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
      this.drawText(graphics, text, x, y, color, dropShadow, 1.0F);
   }

   public void drawText(GuiGraphicsExtractor graphics, FormattedCharSequence text, float x, float y, int color, boolean dropShadow, float drawScale) {
      int alpha = color >>> 24 & 0xFF;
      if (alpha != 0) {
         this.rebuildIfScaleChanged();
         int baseRgb = dropShadow ? shadow(color) : color & 16777215;
         float[] pen = new float[]{0.0F};
         synchronized (this.glyphPages) {
            try {
               text.accept((index, style, codePoint) -> {
                  int rgb;
                  if (style.getColor() != null) {
                     int c = style.getColor().getValue();
                     rgb = dropShadow ? shadow(c) : c & 16777215;
                  } else {
                     rgb = baseRgb;
                  }

                  int argb = alpha << 24 | rgb;

                  for (char ch : Character.toChars(codePoint)) {
                     GlyphMap.Glyph glyph = this.locateGlyph(ch);
                     if (glyph != null) {
                        if (glyph.width() > 0 && glyph.height() > 0 && ch != ' ') {
                           Identifier pageId = glyph.parent().getTextureId();
                           this.glyphPages.computeIfAbsent(pageId, k -> new ObjectArrayList()).add(new FontRenderer.DrawEntry(pen[0], 0.0F, argb, glyph));
                        }

                        pen[0] += glyph.advance();
                     }
                  }

                  return true;
               });
               this.renderGlyphPages(graphics, x, y, drawScale);
            } finally {
               this.glyphPages.clear();
            }
         }
      }
   }

   private void drawPlainString(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean dropShadow, float drawScale) {
      int alpha = color >>> 24 & 0xFF;
      if (alpha != 0 && !text.isEmpty()) {
         this.rebuildIfScaleChanged();
         int rgb = dropShadow ? shadow(color) : color & 16777215;
         int argb = alpha << 24 | rgb;
         float pen = 0.0F;
         synchronized (this.glyphPages) {
            try {
               for (int i = 0; i < text.length(); i++) {
                  GlyphMap.Glyph glyph = this.locateGlyph(text.charAt(i));
                  if (glyph != null) {
                     if (glyph.width() > 0 && glyph.height() > 0 && glyph.value() != ' ') {
                        this.glyphPages
                           .computeIfAbsent(glyph.parent().getTextureId(), key -> new ObjectArrayList())
                           .add(new FontRenderer.DrawEntry(pen, 0.0F, argb, glyph));
                     }

                     pen += glyph.advance();
                  }
               }

               this.renderGlyphPages(graphics, x, y, drawScale);
            } finally {
               this.glyphPages.clear();
            }
         }
      }
   }

   private void renderGlyphPages(GuiGraphicsExtractor graphics, float x, float y, float drawScale) {
      float blockGui = (float)this.blockPx / this.multiplier * drawScale;
      float yTop = y + (8.0F - blockGui) / 2.0F;
      Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();

      try {
         pose.translate(x, yTop);
         float scale = drawScale / this.multiplier;
         pose.scale(scale, scale);

         for (Entry<Identifier, ObjectList<FontRenderer.DrawEntry>> entry : this.glyphPages.entrySet()) {
            Identifier pageId = entry.getKey();
            List<FontRenderer.DrawEntry> entries = (List<FontRenderer.DrawEntry>)entry.getValue();
            ObjectList<GlyphBatchRenderState.GlyphQuad> quads = new ObjectArrayList(entries.size());

            for (FontRenderer.DrawEntry de : entries) {
               GlyphMap.Glyph glyph = de.glyph;
               GlyphMap parent = glyph.parent();
               int x0 = Math.round(de.dx);
               int y0 = Math.round(de.dy);
               quads.add(
                  new GlyphBatchRenderState.GlyphQuad(
                     x0,
                     y0,
                     x0 + glyph.width(),
                     y0 + glyph.height(),
                     (float)glyph.u() / parent.getWidth(),
                     (float)glyph.v() / parent.getHeight(),
                     (float)(glyph.u() + glyph.width()) / parent.getWidth(),
                     (float)(glyph.v() + glyph.height()) / parent.getHeight(),
                     de.argb
                  )
               );
            }

            AbstractTexture texture = mc.getTextureManager().getTexture(pageId);
            graphics.guiRenderState
               .addGuiElement(
                  new GlyphBatchRenderState(
                     RenderPipelines.GUI_TEXTURED,
                     TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()),
                     new Matrix3x2f(pose),
                     quads,
                     graphics.scissorStack.peek()
                  )
               );
         }
      } finally {
         pose.popMatrix();
      }
   }

   public static String stripControlCodes(String text) {
      if (text.indexOf(167) < 0) {
         return text;
      } else {
         StringBuilder sb = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
               i++;
            } else {
               sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   public float getTextWidth(String text) {
      this.rebuildIfScaleChanged();
      String stripped = stripControlCodes(text);
      float adv = 0.0F;

      for (int i = 0; i < stripped.length(); i++) {
         GlyphMap.Glyph g = this.locateGlyph(stripped.charAt(i));
         if (g != null) {
            adv += g.advance();
         }
      }

      return adv / this.multiplier;
   }

   public float getTextWidth(FormattedCharSequence text) {
      this.rebuildIfScaleChanged();
      float[] adv = new float[]{0.0F};
      text.accept((index, style, codePoint) -> {
         for (char ch : Character.toChars(codePoint)) {
            GlyphMap.Glyph g = this.locateGlyph(ch);
            if (g != null) {
               adv[0] += g.advance();
            }
         }

         return true;
      });
      return adv[0] / this.multiplier;
   }

   public float getHeight() {
      return 9.0F;
   }

   private void rebuildIfScaleChanged() {
      int current = Math.round((float)mc.getWindow().getGuiScale());
      if (current != this.previousGameScale) {
         this.close();
         this.init();
      }
   }

   private void init() {
      if (this.initialized) {
         throw new IllegalStateException("Double call to init()");
      } else {
         this.lock.writeLock().lock();

         try {
            int scale = Math.round((float)mc.getWindow().getGuiScale());
            if (scale < 1) {
               scale = 1;
            }

            this.previousGameScale = scale;
            this.multiplier = scale;
            BufferedImage probe = new BufferedImage(1, 1, 2);
            Graphics2D pg = probe.createGraphics();

            try {
               float probeSize = 16.0F * this.multiplier;
               pg.setFont(this.baseFonts[0].deriveFont(probeSize));
               FontMetrics probeFm = pg.getFontMetrics();
               float ascentRatio = probeFm.getAscent() / probeSize;
               float fitSize = 7.0F * this.multiplier / ascentRatio;
               Font[] fitted = new Font[this.baseFonts.length];

               for (int i = 0; i < this.baseFonts.length; i++) {
                  fitted[i] = this.baseFonts[i].deriveFont(fitSize);
               }

               pg.setFont(fitted[0]);
               FontMetrics fm = pg.getFontMetrics();
               this.blockPx = fm.getAscent() + fm.getDescent();
               this.fittedFonts = fitted;
               this.initialized = true;
            } finally {
               pg.dispose();
            }
         } finally {
            this.lock.writeLock().unlock();
         }
      }
   }

   private GlyphMap.Glyph locateGlyph(char ch) {
      this.lock.readLock().lock();

      try {
         ObjectListIterator base = this.maps.iterator();

         while (base.hasNext()) {
            GlyphMap m = (GlyphMap)base.next();
            if (m.contains(ch)) {
               return m.getGlyph(ch);
            }
         }
      } finally {
         this.lock.readLock().unlock();
      }

      int base = this.charsPerPage * (ch / this.charsPerPage);
      int var21 = Math.min(base + this.charsPerPage, 65536);
      GlyphMap var22 = new GlyphMap(this.fittedFonts, base, var21, this.padding);
      this.lock.writeLock().lock();

      try {
         var22.generate();
         this.maps.add(var22);
      } catch (RuntimeException var17) {
         try {
            var22.destroy();
         } catch (RuntimeException var16) {
            var17.addSuppressed(var16);
         }

         throw var17;
      } finally {
         this.lock.writeLock().unlock();
      }

      return var22.getGlyph(ch);
   }

   @Override
   public void close() {
      this.lock.writeLock().lock();

      try {
         ObjectListIterator var1 = this.maps.iterator();

         while (var1.hasNext()) {
            GlyphMap m = (GlyphMap)var1.next();
            m.destroyDeferred();
         }

         this.maps.clear();
         this.initialized = false;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private static int shadow(int rgb) {
      return (rgb & 16579836) >> 2;
   }

   private static Component parse(String text, int defaultColor) {
      if (text.indexOf(167) < 0) {
         return Component.literal(text).withStyle(Style.EMPTY.withColor(defaultColor));
      } else {
         MutableComponent root = Component.literal("").withStyle(Style.EMPTY.withColor(defaultColor));
         Style style = Style.EMPTY.withColor(defaultColor);
         StringBuilder run = new StringBuilder();

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167 && i + 1 < text.length()) {
               if (run.length() > 0) {
                  root.append(Component.literal(run.toString()).withStyle(style));
                  run.setLength(0);
               }

               char code = Character.toLowerCase(text.charAt(++i));
               style = applyCode(style, code, defaultColor);
            } else {
               run.append(c);
            }
         }

         if (run.length() > 0) {
            root.append(Component.literal(run.toString()).withStyle(style));
         }

         return root;
      }
   }

   private static Style applyCode(Style s, char code, int defaultColor) {
      return switch (code) {
         case '0' -> s.withColor(-16777216);
         case '1' -> s.withColor(-16777046);
         case '2' -> s.withColor(-16733696);
         case '3' -> s.withColor(-16733526);
         case '4' -> s.withColor(-5636096);
         case '5' -> s.withColor(-5635926);
         case '6' -> s.withColor(-22016);
         case '7' -> s.withColor(-5592406);
         case '8' -> s.withColor(-11184811);
         case '9' -> s.withColor(-11184641);
         default -> s;
         case 'a' -> s.withColor(-11141291);
         case 'b' -> s.withColor(-11141121);
         case 'c' -> s.withColor(-43691);
         case 'd' -> s.withColor(-43521);
         case 'e' -> s.withColor(-171);
         case 'f' -> s.withColor(-1);
         case 'r' -> Style.EMPTY.withColor(defaultColor);
      };
   }

   private record DrawEntry(float dx, float dy, int argb, GlyphMap.Glyph glyph) {
   }
}
