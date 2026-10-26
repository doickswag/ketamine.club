package we.ketamine.club.mixin.client;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({OptionInstance.class})
public interface OptionInstanceAccessor<T> {
   @Accessor("value")
   void ketamineclub$setValue(T var1);
}
