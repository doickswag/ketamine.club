package we.ketamine.club.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.util.traits.Jsonable;

public class FriendManager implements Jsonable {
   public static final long PERMANENT = -1L;
   private final Map<String, Long> friends = new LinkedHashMap<>();

   public FriendManager() {
   }

   public void init() {
      KetamineClub.configManager.addConfig(this);
   }

   public boolean isFriend(String name) {
      this.purgeExpired();
      return this.friends.keySet().stream().anyMatch(friend -> friend.equalsIgnoreCase(name));
   }

   public boolean isFriend(Player player) {
      return this.isFriend(player.getGameProfile().name());
   }

   public void addFriend(String name) {
      this.addFriend(name, -1L);
   }

   public void addFriend(String name, long expiry) {
      boolean alreadyFriend = this.friends.keySet().stream().anyMatch(friend -> friend.equalsIgnoreCase(name));
      this.friends.keySet().removeIf(friend -> friend.equalsIgnoreCase(name));
      this.friends.put(name, expiry);
      KetamineClub.enemyManager.removeEnemy(name);
      if (!alreadyFriend) {
         this.whisperFriendAdded(name);
      }
   }

   private void whisperFriendAdded(String name) {
      Minecraft mc = Minecraft.getInstance();
      ClientPacketListener connection = mc.getConnection();
      if (connection != null) {
         connection.sendCommand("w " + name + " Added as a friend on client");
      }
   }

   public void removeFriend(String name) {
      this.friends.keySet().removeIf(s -> s.equalsIgnoreCase(name));
   }

   public void clearFriends() {
      this.friends.clear();
   }

   public long getExpiry(String name) {
      for (Entry<String, Long> entry : this.friends.entrySet()) {
         if (entry.getKey().equalsIgnoreCase(name)) {
            return entry.getValue();
         }
      }

      return -1L;
   }

   public List<String> getFriends() {
      this.purgeExpired();
      return new ArrayList<>(this.friends.keySet());
   }

   private void purgeExpired() {
      long now = System.currentTimeMillis();
      this.friends.values().removeIf(expiry -> expiry != -1L && expiry <= now);
   }

   @Override
   public JsonElement toJson() {
      this.purgeExpired();
      JsonObject object = new JsonObject();
      JsonArray array = new JsonArray();

      for (Entry<String, Long> entry : this.friends.entrySet()) {
         JsonObject element = new JsonObject();
         element.addProperty("name", entry.getKey());
         element.addProperty("expiry", entry.getValue());
         array.add(element);
      }

      object.add("friends", array);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      for (JsonElement e : element.getAsJsonObject().get("friends").getAsJsonArray()) {
         if (e.isJsonObject()) {
            JsonObject obj = e.getAsJsonObject();
            long expiry = obj.has("expiry") ? obj.get("expiry").getAsLong() : -1L;
            this.friends.put(obj.get("name").getAsString(), expiry);
         } else {
            this.friends.put(e.getAsString(), -1L);
         }
      }

      this.purgeExpired();
   }

   @Override
   public String getFileName() {
      return "friends.json";
   }
}
