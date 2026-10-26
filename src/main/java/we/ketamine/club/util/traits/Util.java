package we.ketamine.club.util.traits;

import net.minecraft.client.Minecraft;
import we.ketamine.club.event.system.EventBus;

public interface Util {
   Minecraft mc = Minecraft.getInstance();
   EventBus EVENT_BUS = new EventBus();

   @SuppressWarnings("unchecked")
   static <E extends Throwable> void throwUnchecked(Throwable t) throws E {
      throw (E)t;
   }
}
