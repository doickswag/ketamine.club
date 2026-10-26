package we.ketamine.club.event.impl.entity;

import net.minecraft.world.entity.LivingEntity;
import we.ketamine.club.event.Event;

public class TotemPopEvent extends Event {
   private final LivingEntity entity;

   public TotemPopEvent(LivingEntity entity) {
      this.entity = entity;
   }

   public LivingEntity getEntity() {
      return this.entity;
   }
}
