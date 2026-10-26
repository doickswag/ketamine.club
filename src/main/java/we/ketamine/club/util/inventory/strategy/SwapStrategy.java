package we.ketamine.club.util.inventory.strategy;

import we.ketamine.club.util.inventory.Result;

public interface SwapStrategy {
   boolean swap(Result var1);

   boolean swapBack(int var1, Result var2);
}
