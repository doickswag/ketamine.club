package we.ketamine.club.features.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;

public class BreadcrumbsModule extends Module {
   private static final int PEARL_DELAY = 2;
   private static final double Y_OFFSET = 0.1;
   private static final double MIN_SEGMENT_SQ = 1.0E-4;
   public Setting<Boolean> self = this.bool("Self", true);
   public Setting<Boolean> pearls = this.bool("Pearls", true);
   public Setting<Integer> length = this.num("Length", 40, 5, 200);
   public Setting<Float> lineWidth = this.num("LineWidth", 2.0F, 0.5F, 5.0F);
   public Setting<Color> color = this.color("Color", 255, 255, 255, 200);
   public Setting<Boolean> fade = this.bool("Fade", true);
   public Setting<Float> fadeStart = this.num("FadeStart", 0.5F, 0.0F, 1.0F);
   private final List<BreadcrumbsModule.Breadcrumb> selfCrumbs = new ArrayList<>();
   private final List<BreadcrumbsModule.Breadcrumb> pearlCrumbs = new ArrayList<>();
   private final Map<Integer, Vec3> lastPearlSample = new HashMap<>();
   private Vec3 lastPlayerSample;

   public BreadcrumbsModule() {
      super("Breadcrumbs", "Shows movement trails for self and pearls.", Module.Category.RENDER);
      this.fadeStart.setVisibility(v -> this.fade.getValue());
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void reset() {
      this.selfCrumbs.clear();
      this.pearlCrumbs.clear();
      this.lastPearlSample.clear();
      this.lastPlayerSample = null;
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         this.fade(this.selfCrumbs);
         this.fade(this.pearlCrumbs);
         if (this.self.getValue()) {
            Vec3 pos = new Vec3(mc.player.xOld, mc.player.yOld + 0.1, mc.player.zOld);
            if (this.lastPlayerSample != null && this.lastPlayerSample.distanceToSqr(pos) > 1.0E-4) {
               this.selfCrumbs.add(new BreadcrumbsModule.Breadcrumb(this.lastPlayerSample, pos, this.length.getValue()));
            }

            this.lastPlayerSample = pos;
         } else {
            this.lastPlayerSample = null;
         }

         if (this.pearls.getValue()) {
            Set<Integer> alive = new HashSet<>();

            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof ThrownEnderpearl pearl) {
                  int id = pearl.getId();
                  alive.add(id);
                  Vec3 pos = new Vec3(pearl.xOld, pearl.yOld, pearl.zOld);
                  Vec3 last = this.lastPearlSample.get(id);
                  if (last != null && pearl.tickCount > 2 && last.distanceToSqr(pos) > 1.0E-4) {
                     this.pearlCrumbs.add(new BreadcrumbsModule.Breadcrumb(last, pos, this.length.getValue()));
                  }

                  this.lastPearlSample.put(id, pos);
               }
            }

            this.lastPearlSample.keySet().retainAll(alive);
         } else {
            this.lastPearlSample.clear();
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         Color base = this.color.getValue();
         float partial = event.getDelta();
         float width = this.lineWidth.getValue();

         for (BreadcrumbsModule.Breadcrumb crumb : this.selfCrumbs) {
            RenderUtil.drawLine(crumb.from, crumb.to, this.fade(base, crumb, partial), width);
         }

         if (this.self.getValue() && this.lastPlayerSample != null) {
            Vec3 head = interpolate(mc.player, partial).add(0.0, 0.1, 0.0);
            RenderUtil.drawLine(this.lastPlayerSample, head, base, width);
         }

         for (BreadcrumbsModule.Breadcrumb crumb : this.pearlCrumbs) {
            RenderUtil.drawLine(crumb.from, crumb.to, this.fade(base, crumb, partial), width);
         }

         if (this.pearls.getValue()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof ThrownEnderpearl pearl && pearl.tickCount > 2) {
                  Vec3 last = this.lastPearlSample.get(pearl.getId());
                  if (last != null) {
                     RenderUtil.drawLine(last, interpolate(pearl, partial), base, width);
                  }
               }
            }
         }
      }
   }

   private static Vec3 interpolate(Entity e, float partial) {
      return new Vec3(Mth.lerp(partial, e.xOld, e.getX()), Mth.lerp(partial, e.yOld, e.getY()), Mth.lerp(partial, e.zOld, e.getZ()));
   }

   private Color fade(Color base, BreadcrumbsModule.Breadcrumb b, float partial) {
      if (!this.fade.getValue()) {
         return base;
      } else {
         float life = (b.age + partial) / b.lifeTime;
         float start = this.fadeStart.getValue();
         float progress = (life - start) / (1.0F - start);
         if (progress < 0.0F) {
            progress = 0.0F;
         }

         if (progress > 1.0F) {
            progress = 1.0F;
         }

         int a = Math.round(base.getAlpha() * (1.0F - progress));
         return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
      }
   }

   private void fade(List<BreadcrumbsModule.Breadcrumb> list) {
      Iterator<BreadcrumbsModule.Breadcrumb> it = list.iterator();

      while (it.hasNext()) {
         BreadcrumbsModule.Breadcrumb b = it.next();
         if (++b.age >= b.lifeTime) {
            it.remove();
         }
      }
   }

   private static class Breadcrumb {
      final Vec3 from;
      final Vec3 to;
      final int lifeTime;
      int age;

      Breadcrumb(Vec3 from, Vec3 to, int lifeTime) {
         this.from = from;
         this.to = to;
         this.lifeTime = lifeTime;
      }
   }
}
