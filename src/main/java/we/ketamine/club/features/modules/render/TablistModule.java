package we.ketamine.club.features.modules.render;

import java.awt.Color;
import net.minecraft.client.multiplayer.PlayerInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class TablistModule extends Module {
   public Setting<Boolean> friends = this.bool("Friends", true);
   public Setting<Boolean> enemies = this.bool("Enemies", true);
   public final Setting<Color> friendColor = this.color("Friend Color", 0, 255, 100, 255);
   public final Setting<Color> enemyColor = this.color("Enemy Color", 255, 0, 0, 255);

   public TablistModule() {
      super("Tablist", "Hides players in the tablist who aren't you, a friend, or an enemy", Module.Category.RENDER);
   }

   public boolean shouldShow(PlayerInfo info) {
      String name = info.getProfile().name();
      if (name == null) {
         return false;
      } else if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) {
         return true;
      } else {
         return this.friends.getValue() && KetamineClub.friendManager.isFriend(name)
            ? true
            : this.enemies.getValue() && KetamineClub.enemyManager.isEnemy(name);
      }
   }
}
