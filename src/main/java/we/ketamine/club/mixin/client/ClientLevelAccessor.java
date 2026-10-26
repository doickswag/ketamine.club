package we.ketamine.club.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientLevel.class})
public interface ClientLevelAccessor {
   @Accessor("blockStatePredictionHandler")
   BlockStatePredictionHandler ketamineclub$getBlockStatePredictionHandler();
}
