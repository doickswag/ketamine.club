package we.ketamine.club.event.system;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.Event;

public class EventBus {
   private final Map<Class<?>, CopyOnWriteArrayList<Listener>> listeners = new ConcurrentHashMap<>();
   private final Set<Object> registeredHosts = Collections.newSetFromMap(new IdentityHashMap<>());

   public EventBus() {
   }

   public synchronized boolean isRegistered(Object host) {
      return this.registeredHosts.contains(host);
   }

   public synchronized void register(Object host) {
      if (this.registeredHosts.add(host)) {
         try {
            this.register(host, host.getClass());
         } catch (RuntimeException var3) {
            this.unregister(host);
            throw var3;
         }
      }
   }

   public synchronized void unregister(Object host) {
      this.registeredHosts.remove(host);

      for (CopyOnWriteArrayList<Listener> list : this.listeners.values()) {
         list.removeIf(listener -> listener.getHost() == host);
      }
   }

   public boolean post(Event event) {
      List<Listener> list = this.listeners.get(event.getClass());
      if (list == null) {
         return false;
      } else {
         for (Listener listener : list) {
            if (event.isCancelled()) {
               return true;
            }

            try {
               listener.invoke(event);
            } catch (RuntimeException var6) {
               KetamineClub.LOGGER.error("Event listener on {} failed for {}", listener.getHost().getClass().getName(), event.getClass().getName(), var6);
            }
         }

         return event.isCancelled();
      }
   }

   private void register(Object host, Class<?> klass) {
      for (Method method : klass.getDeclaredMethods()) {
         Subscribe subscribe = method.getAnnotation(Subscribe.class);
         if (subscribe != null) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1) {
               Class<?> eventType = params[0];
               Listener listener = Listener.of(host, subscribe.priority(), method);
               List<Listener> registry = this.listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
               this.register(registry, listener);
            }
         }
      }

      if (klass.getSuperclass() != null) {
         this.register(host, klass.getSuperclass());
      }
   }

   private void register(List<Listener> registry, Listener target) {
      int i = 0;

      for (Listener listener : registry) {
         if (target.priority() > listener.priority()) {
            break;
         }

         i++;
      }

      registry.add(i, target);
   }
}
