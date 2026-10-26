package we.ketamine.club.mixin.entity;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LocalPlayer.class})
public interface EntityRotationAccessor {
   @Accessor("xBob")
   float ketamineclub$getXBob();

   @Accessor("xBob")
   void ketamineclub$setXBob(float var1);

   @Accessor("xBobO")
   float ketamineclub$getXBobO();

   @Accessor("xBobO")
   void ketamineclub$setXBobO(float var1);

   @Accessor("yBob")
   float ketamineclub$getYBob();

   @Accessor("yBob")
   void ketamineclub$setYBob(float var1);

   @Accessor("yBobO")
   float ketamineclub$getYBobO();

   @Accessor("yBobO")
   void ketamineclub$setYBobO(float var1);

   @Accessor("lastOnGround")
   boolean ketamineclub$getLastOnGround();
}
