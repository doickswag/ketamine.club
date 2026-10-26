package we.ketamine.club.event.system;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public record Listener(Object host, int priority, Consumer<Object> consumer) {
   public static Listener of(Object host, int priority, Method method) {
      return new Listener(host, priority, buildLambdaMetafactory(host, method));
   }

   public void invoke(Object event) {
      this.consumer.accept(event);
   }

   public Object getHost() {
      return this.host;
   }

   private static Consumer<Object> buildLambdaMetafactory(Object host, Method method) {
      try {
         Lookup lookup = MethodHandles.privateLookupIn(host.getClass(), MethodHandles.lookup());
         CallSite site = LambdaMetafactory.metafactory(
            lookup,
            "accept",
            MethodType.methodType(Consumer.class, host.getClass()),
            MethodType.methodType(void.class, Object.class),
            lookup.unreflect(method),
            MethodType.methodType(void.class, method.getParameterTypes()[0])
         );
         MethodHandle target = site.getTarget();
         return (Consumer)target.invoke((Object)host);
      } catch (Throwable var5) {
         throw new RuntimeException("Failed to build lambda from %s method".formatted(method.getName()), var5);
      }
   }
}
