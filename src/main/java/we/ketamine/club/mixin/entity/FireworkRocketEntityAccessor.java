package we.ketamine.club.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({FireworkRocketEntity.class})
public interface FireworkRocketEntityAccessor {
   @Accessor("life")
   int ketamineclub$getLife();

   @Accessor("lifetime")
   int ketamineclub$getLifetime();

   @Accessor("attachedToEntity")
   LivingEntity ketamineclub$getAttachedToEntity();

   @Accessor("DATA_ID_FIREWORKS_ITEM")
   static EntityDataAccessor<ItemStack> ketamineclub$getFireworksItemData() {
      throw new AssertionError();
   }
}
