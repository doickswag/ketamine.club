package we.ketamine.club.features.modules.render;

import java.awt.Color;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class SeeThroughModule extends Module {
   public Setting<Boolean> players = this.bool("Players", true).setPage("Entities");
   public Setting<Float> playersRange = this.num("Players Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.players.getValue());
   public Setting<Boolean> monsters = this.bool("Monsters", false).setPage("Entities");
   public Setting<Float> monstersRange = this.num("Monsters Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.monsters.getValue());
   public Setting<Boolean> animals = this.bool("Animals", false).setPage("Entities");
   public Setting<Float> animalsRange = this.num("Animals Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.animals.getValue());
   public Setting<Boolean> items = this.bool("Items", true).setPage("Entities");
   public Setting<Float> itemsRange = this.num("Items Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.items.getValue());
   public Setting<Boolean> crystals = this.bool("Crystals", false).setPage("Entities");
   public Setting<Float> crystalsRange = this.num("Crystals Range", 64.0F, 4.0F, 256.0F).setPage("Entities").setVisibility(v -> this.crystals.getValue());
   public Setting<Boolean> projectiles = this.bool("Projectiles", false).setPage("Entities");
   public Setting<Float> projectilesRange = this.num("Projectiles Range", 64.0F, 4.0F, 256.0F)
      .setPage("Entities")
      .setVisibility(v -> this.projectiles.getValue());
   public Setting<Boolean> shine = this.bool("Shine", false).setPage("Render");
   public Setting<Color> shineColor = this.color("Shine Color", 130, 80, 255, 255).setPage("Render").setVisibility(v -> this.shine.getValue());
   public Setting<Integer> shineOpacity = this.num("Shine Opacity", 100, 0, 500).setPage("Render").setVisibility(v -> this.shine.getValue());

   public SeeThroughModule() {
      super("SeeThrough", "Render selected entities through walls", Module.Category.RENDER);
   }

   public boolean shouldSeeThrough(EntityRenderState state) {
      if (state == null) {
         return false;
      } else {
         EntityType<?> type = state.entityType;
         if (type == null) {
            return false;
         } else if (type == EntityTypes.PLAYER) {
            return this.inRange(state, this.players, this.playersRange);
         } else if (type == EntityTypes.ITEM) {
            return this.inRange(state, this.items, this.itemsRange);
         } else if (type == EntityTypes.END_CRYSTAL) {
            return this.inRange(state, this.crystals, this.crystalsRange);
         } else if (Projectile.class.isAssignableFrom(type.getBaseClass())) {
            return this.inRange(state, this.projectiles, this.projectilesRange);
         } else if (type.getCategory() == MobCategory.MONSTER) {
            return this.inRange(state, this.monsters, this.monstersRange);
         } else {
            return type.getCategory() != MobCategory.MISC ? this.inRange(state, this.animals, this.animalsRange) : false;
         }
      }
   }

   public int shineRgb() {
      return this.shineColor.getValue().getRGB() & 16777215;
   }

   private boolean inRange(EntityRenderState state, Setting<Boolean> enabled, Setting<Float> range) {
      if (!enabled.getValue()) {
         return false;
      } else {
         float r = range.getValue();
         return state.distanceToCameraSq <= (double)r * r;
      }
   }
}
