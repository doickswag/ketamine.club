package we.ketamine.club.mixin.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.combat.AutoCrystalModule;
import we.ketamine.club.features.modules.combat.PistonCrystalModule;
import we.ketamine.club.features.modules.combat.SurroundModule;

@Mixin({ClientLevel.class})
public class MixinClientLevelEntity {
   public MixinClientLevelEntity() {
   }

   @Inject(
      method = {"addEntity"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$onEntityAdded(Entity entity, CallbackInfo ci) {
      if (entity instanceof EndCrystal crystal && KetamineClub.moduleManager != null) {
         if (((ClientLevel)(Object)this).getEntity(entity.getId()) == entity) {
            AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (autoCrystal != null && autoCrystal.isEnabled()) {
               autoCrystal.trackCrystalSpawn(crystal);
            }

            PistonCrystalModule pistonCrystal = KetamineClub.moduleManager.getModuleByClass(PistonCrystalModule.class);
            if (pistonCrystal != null && pistonCrystal.isEnabled()) {
               pistonCrystal.onCrystalAdded(crystal);
            }

            SurroundModule surround = KetamineClub.moduleManager.getModuleByClass(SurroundModule.class);
            if (surround == null || !surround.onCrystalAdded(crystal)) {
               if (autoCrystal != null && autoCrystal.isEnabled()) {
                  autoCrystal.onCrystalAdded(crystal);
               }
            }
         }
      }
   }
}
