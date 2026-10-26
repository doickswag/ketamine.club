package we.ketamine.club.mixin.client;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.render.TablistModule;

@Mixin({PlayerTabOverlay.class})
public abstract class MixinPlayerTabOverlay {
   public MixinPlayerTabOverlay() {
   }

   @Inject(
      method = {"getPlayerInfos"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$filterPlayers(CallbackInfoReturnable<List<PlayerInfo>> cir) {
      TablistModule module = KetamineClub.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         ClientPacketListener conn = Minecraft.getInstance().getConnection();
         if (conn != null) {
            List<PlayerInfo> all = conn.getListedOnlinePlayers().stream().toList();
            List<PlayerInfo> filtered = all.stream()
               .filter(module::shouldShow)
               .sorted(Comparator.comparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
               .limit(80L)
               .toList();
            cir.setReturnValue(filtered);
         }
      }
   }

   @Inject(
      method = {"getNameForDisplay"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void ketamineclub$colorName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
      TablistModule module = KetamineClub.moduleManager.getModuleByClass(TablistModule.class);
      if (module != null && module.isEnabled()) {
         String name = info.getProfile().name();
         if (name != null) {
            Color color;
            if (KetamineClub.enemyManager.isEnemy(name)) {
               color = module.enemyColor.getValue();
            } else {
               if (!KetamineClub.friendManager.isFriend(name)) {
                  return;
               }

               color = module.friendColor.getValue();
            }

            Component original = (Component)cir.getReturnValue();
            cir.setReturnValue(original.copy().withStyle(Style.EMPTY.withColor(color.getRGB() & 16777215)));
         }
      }
   }
}
