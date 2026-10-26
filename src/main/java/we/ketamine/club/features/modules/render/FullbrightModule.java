package we.ketamine.club.features.modules.render;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.mixin.client.OptionInstanceAccessor;

public class FullbrightModule extends Module {
   private static final double MAX_GAMMA = 1.0;
   private static final int NIGHT_VISION_AMPLIFIER = 69;
   private final Setting<FullbrightModule.Mode> mode = this.mode("Mode", FullbrightModule.Mode.GAMMA);
   private Double previousGamma;
   private MobEffectInstance previousNightVision;
   private LocalPlayer potionOwner;
   private boolean potionApplied;
   private FullbrightModule.Mode activeMode;

   public FullbrightModule() {
      super("Fullbright", "Provides gamma or Night Vision full brightness.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      if (mc.options != null) {
         this.previousGamma = (Double)mc.options.gamma().get();
      }

      this.activeMode = null;
      this.applyMode();
   }

   @Override
   public void onDisable() {
      if (mc.options != null && this.previousGamma != null) {
         this.setGamma(this.previousGamma);
      }

      this.restoreNightVision();
      this.previousGamma = null;
      this.activeMode = null;
   }

   @Override
   public void onTick() {
      if (mc.options != null && this.previousGamma == null) {
         this.previousGamma = (Double)mc.options.gamma().get();
      }

      this.applyMode();
   }

   private void applyMode() {
      FullbrightModule.Mode selected = this.mode.getValue();
      if (selected != this.activeMode) {
         if (selected == FullbrightModule.Mode.GAMMA) {
            this.restoreNightVision();
         } else if (mc.options != null && this.previousGamma != null) {
            this.setGamma(this.previousGamma);
         }

         this.activeMode = selected;
      }

      if (selected == FullbrightModule.Mode.GAMMA) {
         if (mc.options != null) {
            this.setGamma(1.0);
         }
      } else {
         this.applyNightVision();
      }
   }

   private void applyNightVision() {
      if (mc.player != null) {
         if (this.potionOwner != mc.player) {
            this.previousNightVision = null;
            this.potionApplied = false;
            this.potionOwner = mc.player;
         }

         if (!this.potionApplied) {
            MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
            this.previousNightVision = current == null ? null : new MobEffectInstance(current);
            this.potionApplied = true;
         }

         MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
         if (current == null || current.getAmplifier() != 69 || !current.isInfiniteDuration()) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 69, false, false, false));
         }
      }
   }

   private void restoreNightVision() {
      if (this.potionApplied && this.potionOwner != null && this.potionOwner == mc.player) {
         this.potionOwner.removeEffect(MobEffects.NIGHT_VISION);
         if (this.previousNightVision != null) {
            this.potionOwner.addEffect(this.previousNightVision);
         }
      }

      this.previousNightVision = null;
      this.potionOwner = null;
      this.potionApplied = false;
   }

   private void setGamma(double value) {
      ((OptionInstanceAccessor)(Object)mc.options.gamma()).ketamineclub$setValue(value);
   }

   private static enum Mode {
      GAMMA,
      POTION;

      private Mode() {
      }
   }
}
