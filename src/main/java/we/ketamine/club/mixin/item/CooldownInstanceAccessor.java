package we.ketamine.club.mixin.item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   targets = {"net.minecraft.world.item.ItemCooldowns$CooldownInstance"}
)
public interface CooldownInstanceAccessor {
   @Accessor("endTime")
   int ketamineclub$getEndTime();
}
