package we.ketamine.club.features.modules.render;

import java.awt.Color;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.HandShaderChain;
import we.ketamine.club.util.render.HandShaderRender;
import we.ketamine.club.util.render.WorldChamsChain;

public class ShadersModule extends Module {
   public Setting<ShadersModule.Mode> mode = this.mode("Mode", ShadersModule.Mode.Default).setPage("General");
   public Setting<ShadersModule.StylePreset> stylePreset = this.mode("StylePreset", ShadersModule.StylePreset.Custom).setPage("Style");
   public Setting<Boolean> worldOutline = this.bool("Outline", true).setPage("Style");
   public Setting<Float> outlineWidth = this.num("OutlineWidth", 2.0F, 0.0F, 10.0F).setPage("Style");
   public Setting<Float> outlineAlpha = this.num("OutlineAlpha", 1.0F, 0.0F, 1.0F).setPage("Style");
   public Setting<Boolean> glow = this.bool("Glow", true).setPage("Style");
   public Setting<Float> glowRadius = this.num("GlowRadius", 3.0F, 0.0F, 16.0F).setPage("Style");
   public Setting<Float> glowIntensity = this.num("GlowIntensity", 1.55F, 0.0F, 3.0F).setPage("Style");
   public Setting<Boolean> innerGlow = this.bool("InnerGlow", true).setPage("Style");
   public Setting<Float> innerGlowRadius = this.num("InnerGlowRadius", 4.0F, 0.0F, 16.0F).setPage("Style");
   public Setting<Float> innerGlowIntensity = this.num("InnerGlowIntensity", 1.55F, 0.0F, 3.0F).setPage("Style");
   public Setting<Boolean> fill = this.bool("Fill", true).setPage("Style");
   public Setting<Float> fillTint = this.num("FillTint", 1.0F, 0.0F, 2.0F).setPage("Style");
   public Setting<Float> fillAlpha = this.num("FillAlpha", 0.4F, 0.0F, 1.0F).setPage("Style");
   public Setting<Boolean> players = this.bool("Players", true).setPage("Targets");
   public Setting<Float> playerRange = this.num("PlayerRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> friends = this.bool("Friends", true).setPage("Targets");
   public Setting<Float> friendRange = this.num("FriendRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> monsters = this.bool("Monsters", true).setPage("Targets");
   public Setting<Float> monsterRange = this.num("MonsterRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> animals = this.bool("Animals", false).setPage("Targets");
   public Setting<Float> animalRange = this.num("AnimalRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> items = this.bool("Items", false).setPage("Targets");
   public Setting<Float> itemRange = this.num("ItemRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> crystals = this.bool("Crystals", true).setPage("Targets");
   public Setting<Float> crystalRange = this.num("CrystalRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Boolean> projectiles = this.bool("Projectiles", false).setPage("Targets");
   public Setting<Float> projectileRange = this.num("ProjectileRange", 64.0F, 4.0F, 256.0F).setPage("Targets");
   public Setting<Color> playerColor = this.color("PlayerOutline", 255, 0, 0, 255).setPage("Colors");
   public Setting<Color> friendColor = this.color("FriendOutline", 0, 255, 100, 255).setPage("Colors");
   public Setting<Color> enemyColor = this.color("EnemyOutline", 255, 60, 60, 255).setPage("Colors");
   public Setting<Color> monsterColor = this.color("MonsterOutline", 200, 60, 60, 255).setPage("Colors");
   public Setting<Color> animalColor = this.color("AnimalOutline", 255, 200, 60, 255).setPage("Colors");
   public Setting<Color> itemColor = this.color("ItemOutline", 255, 255, 255, 255).setPage("Colors");
   public Setting<Color> crystalColor = this.color("CrystalOutline", 0, 200, 255, 255).setPage("Colors");
   public Setting<Color> projectileColor = this.color("ProjectileOutline", 200, 200, 0, 255).setPage("Colors");
   public Setting<Boolean> hand = this.bool("HandShaders", false).setPage("Hand");
   public Setting<ShadersModule.HandPreset> handPreset = this.mode("HandPreset", ShadersModule.HandPreset.Custom).setPage("Hand");
   public Setting<Boolean> handOutline = this.bool("HandOutline", true).setPage("Hand");
   public Setting<Float> handThickness = this.num("HandThickness", 2.0F, 1.0F, 10.0F).setPage("Hand");
   public Setting<Float> handOutlineAlpha = this.num("HandOutlineAlpha", 1.0F, 0.0F, 1.0F).setPage("Hand");
   public Setting<Boolean> handFill = this.bool("HandFill", false).setPage("Hand");
   public Setting<Float> handFillAlpha = this.num("HandFillAlpha", 0.35F, 0.0F, 1.0F).setPage("Hand");
   public Setting<Boolean> handGlow = this.bool("HandGlow", false).setPage("Hand");
   public Setting<Float> handGlowRadius = this.num("HandGlowRadius", 4.0F, 1.0F, 16.0F).setPage("Hand");
   public Setting<Float> handGlowIntensity = this.num("HandGlowIntensity", 1.0F, 0.0F, 3.0F).setPage("Hand");
   public Setting<Boolean> handInnerGlow = this.bool("HandInnerGlow", false).setPage("Hand");
   public Setting<Float> handInnerGlowRadius = this.num("HandInnerGlowRadius", 4.0F, 1.0F, 16.0F).setPage("Hand");
   public Setting<Float> handInnerGlowIntensity = this.num("HandInnerGlowIntensity", 1.0F, 0.0F, 3.0F).setPage("Hand");
   public Setting<Color> handColor = this.color("HandColor", 255, 0, 0, 255).setPage("Hand");

   public ShadersModule() {
      super("Shaders", "Stylised post-effect shader on selected entities", Module.Category.RENDER);
      this.stylePreset.setVisibility(v -> this.mode.getValue() == ShadersModule.Mode.Default);
      this.worldOutline.setVisibility(v -> this.customWorldStyle());
      this.outlineWidth.setVisibility(v -> this.customWorldStyle() && this.worldOutline.getValue());
      this.outlineAlpha.setVisibility(v -> this.customWorldStyle() && this.worldOutline.getValue());
      this.glow.setVisibility(v -> this.customWorldStyle());
      this.glowRadius.setVisibility(v -> this.customWorldStyle() && this.glow.getValue());
      this.glowIntensity.setVisibility(v -> this.customWorldStyle() && this.glow.getValue());
      this.innerGlow.setVisibility(v -> this.customWorldStyle());
      this.innerGlowRadius.setVisibility(v -> this.customWorldStyle() && this.innerGlow.getValue());
      this.innerGlowIntensity.setVisibility(v -> this.customWorldStyle() && this.innerGlow.getValue());
      this.fill.setVisibility(v -> this.customWorldStyle());
      this.fillTint.setVisibility(v -> this.customWorldStyle() && this.fill.getValue());
      this.fillAlpha.setVisibility(v -> this.customWorldStyle() && this.fill.getValue());
      this.playerRange.setVisibility(v -> this.players.getValue());
      this.friendRange.setVisibility(v -> this.friends.getValue());
      this.monsterRange.setVisibility(v -> this.monsters.getValue());
      this.animalRange.setVisibility(v -> this.animals.getValue());
      this.itemRange.setVisibility(v -> this.items.getValue());
      this.crystalRange.setVisibility(v -> this.crystals.getValue());
      this.projectileRange.setVisibility(v -> this.projectiles.getValue());
      this.handPreset.setVisibility(v -> this.hand.getValue());
      this.handThickness.setVisibility(v -> this.customHandStyle() && this.handOutline.getValue());
      this.handOutline.setVisibility(v -> this.customHandStyle());
      this.handOutlineAlpha.setVisibility(v -> this.customHandStyle() && this.handOutline.getValue());
      this.handFill.setVisibility(v -> this.customHandStyle());
      this.handFillAlpha.setVisibility(v -> this.customHandStyle() && this.handFill.getValue());
      this.handGlow.setVisibility(v -> this.customHandStyle());
      this.handGlowRadius.setVisibility(v -> this.customHandStyle() && this.handGlow.getValue());
      this.handGlowIntensity.setVisibility(v -> this.customHandStyle() && this.handGlow.getValue());
      this.handInnerGlow.setVisibility(v -> this.customHandStyle());
      this.handInnerGlowRadius.setVisibility(v -> this.customHandStyle() && this.handInnerGlow.getValue());
      this.handInnerGlowIntensity.setVisibility(v -> this.customHandStyle() && this.handInnerGlow.getValue());
      this.handColor.setVisibility(v -> this.hand.getValue());
   }

   @Override
   public void onDisable() {
      HandShaderRender.cleanup();
      HandShaderChain.cleanup();
      WorldChamsChain.cleanup();
   }

   public boolean wantsHandShader() {
      if (this.isEnabled() && this.hand.getValue()) {
         ShadersModule.HandStyle style = this.handStyle();
         return style.outlineWidth() > 0 && style.outlineAlpha() > 0.0F || style.fillAlpha() > 0.0F || style.glowRadius() > 0 || style.innerGlowRadius() > 0;
      } else {
         return false;
      }
   }

   public int getHandThickness() {
      return this.handStyle().outlineWidth();
   }

   public float getHandOutlineAlpha() {
      return this.handStyle().outlineAlpha();
   }

   public float getHandFillAlpha() {
      return this.handStyle().fillAlpha();
   }

   public int getHandGlowRadius() {
      return this.handStyle().glowRadius();
   }

   public float getHandGlowIntensity() {
      return this.handStyle().glowIntensity();
   }

   public int getHandInnerGlowRadius() {
      return this.handStyle().innerGlowRadius();
   }

   public float getHandInnerGlowIntensity() {
      return this.handStyle().innerGlowIntensity();
   }

   public int getHandRgb() {
      Color c = this.handColor.getValue();
      return c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
   }

   public int getGlowRadius() {
      return this.worldStyle().glowRadius();
   }

   public float getGlowIntensity() {
      return this.worldStyle().glowIntensity();
   }

   public int getInnerGlowRadius() {
      return this.worldStyle().innerGlowRadius();
   }

   public float getInnerGlowIntensity() {
      return this.worldStyle().innerGlowIntensity();
   }

   public int getOutlineWidth() {
      return this.worldStyle().outlineWidth();
   }

   public float getOutlineAlpha() {
      return this.worldStyle().outlineAlpha();
   }

   public float getFillTint() {
      return this.worldStyle().fillTint();
   }

   public float getFillAlpha() {
      return this.worldStyle().fillAlpha();
   }

   public boolean wantsOutlines() {
      return this.hasWorldShaderEffect();
   }

   public boolean hasWorldShaderEffect() {
      if (!this.isEnabled()) {
         return false;
      } else {
         ShadersModule.Mode m = this.mode.getValue();
         if (m != null && m != ShadersModule.Mode.Off) {
            ShadersModule.WorldStyle style = this.worldStyle();
            return style.outlineWidth() > 0 && style.outlineAlpha() > 0.0F || style.fillAlpha() > 0.0F || style.glowRadius() > 0 || style.innerGlowRadius() > 0;
         } else {
            return false;
         }
      }
   }

   public boolean shouldShader(Entity entity) {
      if (!this.hasWorldShaderEffect()) {
         return false;
      } else if (entity == null) {
         return false;
      } else if (mc.player != null && entity == mc.player) {
         return false;
      } else if (entity instanceof AbstractClientPlayer p && !(entity instanceof LocalPlayer)) {
         boolean friend = KetamineClub.friendManager.isFriend(p);
         return friend
            ? this.friends.getValue() && this.inRange(entity, this.friendRange.getValue())
            : this.players.getValue() && this.inRange(entity, this.playerRange.getValue());
      } else if (entity instanceof Monster) {
         return this.monsters.getValue() && this.inRange(entity, this.monsterRange.getValue());
      } else if (entity instanceof Animal) {
         return this.animals.getValue() && this.inRange(entity, this.animalRange.getValue());
      } else if (entity instanceof ItemEntity) {
         return this.items.getValue() && this.inRange(entity, this.itemRange.getValue());
      } else if (entity instanceof EndCrystal) {
         return this.crystals.getValue() && this.inRange(entity, this.crystalRange.getValue());
      } else {
         return !(entity instanceof Projectile) ? false : this.projectiles.getValue() && this.inRange(entity, this.projectileRange.getValue());
      }
   }

   private boolean inRange(Entity entity, float range) {
      return mc.player == null || entity.distanceTo(mc.player) <= range;
   }

   public int getRgbFor(Entity entity) {
      Color c = this.colorFor(entity);
      return c.getRed() << 16 | c.getGreen() << 8 | c.getBlue();
   }

   private Color colorFor(Entity entity) {
      if (entity instanceof AbstractClientPlayer p && !(entity instanceof LocalPlayer)) {
         if (KetamineClub.enemyManager.isEnemy(p)) {
            return this.enemyColor.getValue();
         } else {
            return KetamineClub.friendManager.isFriend(p) ? this.friendColor.getValue() : this.playerColor.getValue();
         }
      } else if (entity instanceof Monster) {
         return this.monsterColor.getValue();
      } else if (entity instanceof Animal) {
         return this.animalColor.getValue();
      } else if (entity instanceof ItemEntity) {
         return this.itemColor.getValue();
      } else if (entity instanceof EndCrystal) {
         return this.crystalColor.getValue();
      } else {
         return entity instanceof Projectile ? this.projectileColor.getValue() : this.playerColor.getValue();
      }
   }

   private boolean customWorldStyle() {
      return this.mode.getValue() == ShadersModule.Mode.Default && this.stylePreset.getValue() == ShadersModule.StylePreset.Custom;
   }

   private boolean customHandStyle() {
      return this.hand.getValue() && this.handPreset.getValue() == ShadersModule.HandPreset.Custom;
   }

   private ShadersModule.WorldStyle worldStyle() {
      ShadersModule.StylePreset preset = this.stylePreset.getValue();
      if (preset == null) {
         preset = ShadersModule.StylePreset.Custom;
      }
      return switch (preset) {
         case Custom -> new ShadersModule.WorldStyle(
            this.worldOutline.getValue() ? Math.round(this.outlineWidth.getValue()) : 0,
            this.outlineAlpha.getValue(),
            this.glow.getValue() ? Math.round(this.glowRadius.getValue()) : 0,
            this.glowIntensity.getValue(),
            this.innerGlow.getValue() ? Math.round(this.innerGlowRadius.getValue()) : 0,
            this.innerGlowIntensity.getValue(),
            this.fillTint.getValue(),
            this.fill.getValue() ? this.fillAlpha.getValue() : 0.0F
         );
         case Subtle -> new ShadersModule.WorldStyle(1, 0.85F, 3, 0.85F, 0, 0.0F, 1.0F, 0.2F);
         case Crisp -> new ShadersModule.WorldStyle(2, 1.0F, 0, 0.0F, 0, 0.0F, 1.0F, 0.15F);
         case Glow -> new ShadersModule.WorldStyle(1, 0.75F, 8, 1.8F, 4, 1.15F, 1.12F, 0.28F);
         case Solid -> new ShadersModule.WorldStyle(2, 1.0F, 0, 0.0F, 0, 0.0F, 1.0F, 0.7F);
      };
   }

   private ShadersModule.HandStyle handStyle() {
      ShadersModule.HandPreset preset = this.handPreset.getValue();
      if (preset == null) {
         preset = ShadersModule.HandPreset.Custom;
      }
      return switch (preset) {
         case Custom -> new ShadersModule.HandStyle(
            this.handOutline.getValue() ? Math.round(this.handThickness.getValue()) : 0,
            this.handOutlineAlpha.getValue(),
            this.handFill.getValue() ? this.handFillAlpha.getValue() : 0.0F,
            this.handGlow.getValue() ? Math.round(this.handGlowRadius.getValue()) : 0,
            this.handGlowIntensity.getValue(),
            this.handInnerGlow.getValue() ? Math.round(this.handInnerGlowRadius.getValue()) : 0,
            this.handInnerGlowIntensity.getValue()
         );
         case Subtle -> new ShadersModule.HandStyle(1, 0.85F, 0.18F, 3, 0.8F, 0, 0.0F);
         case Crisp -> new ShadersModule.HandStyle(2, 1.0F, 0.0F, 0, 0.0F, 0, 0.0F);
         case Glow -> new ShadersModule.HandStyle(1, 0.75F, 0.18F, 8, 1.6F, 4, 1.0F);
         case Solid -> new ShadersModule.HandStyle(2, 1.0F, 0.45F, 0, 0.0F, 0, 0.0F);
      };
   }

   public static enum HandPreset {
      Custom,
      Subtle,
      Crisp,
      Glow,
      Solid;

      private HandPreset() {
      }
   }

   private record HandStyle(
      int outlineWidth, float outlineAlpha, float fillAlpha, int glowRadius, float glowIntensity, int innerGlowRadius, float innerGlowIntensity
   ) {
   }

   public static enum Mode {
      Default,
      Off;

      private Mode() {
      }
   }

   public static enum StylePreset {
      Custom,
      Subtle,
      Crisp,
      Glow,
      Solid;

      private StylePreset() {
      }
   }

   private record WorldStyle(
      int outlineWidth, float outlineAlpha, int glowRadius, float glowIntensity, int innerGlowRadius, float innerGlowIntensity, float fillTint, float fillAlpha
   ) {
   }
}
