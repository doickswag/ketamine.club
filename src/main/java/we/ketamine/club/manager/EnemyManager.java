package we.ketamine.club.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.util.traits.Jsonable;

public class EnemyManager implements Jsonable {
   private final List<String> enemies = new ArrayList<>();

   public EnemyManager() {
   }

   public void init() {
      KetamineClub.configManager.addConfig(this);
   }

   public boolean isEnemy(String name) {
      return this.enemies.stream().anyMatch(enemy -> enemy.equalsIgnoreCase(name));
   }

   public boolean isEnemy(Player player) {
      return this.isEnemy(player.getGameProfile().name());
   }

   public void addEnemy(String name) {
      this.enemies.add(name);
      KetamineClub.friendManager.removeFriend(name);
   }

   public void removeEnemy(String name) {
      this.enemies.removeIf(s -> s.equalsIgnoreCase(name));
   }

   public void clearEnemies() {
      this.enemies.clear();
   }

   public List<String> getEnemies() {
      return this.enemies;
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      JsonArray array = new JsonArray();

      for (String enemy : this.enemies) {
         array.add(enemy);
      }

      object.add("enemies", array);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      for (JsonElement e : element.getAsJsonObject().get("enemies").getAsJsonArray()) {
         this.enemies.add(e.getAsString());
      }
   }

   @Override
   public String getFileName() {
      return "enemies.json";
   }
}
