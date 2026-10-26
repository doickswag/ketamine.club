package we.ketamine.club.features.modules.client;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class TargetsModule extends Module {
   public final Setting<Boolean> players = this.bool("Players", true);
   public final Setting<Boolean> hostiles = this.bool("Hostiles", false);
   public final Setting<Boolean> neutrals = this.bool("Neutrals", false);
   public final Setting<Boolean> passives = this.bool("Passives", false);

   public TargetsModule() {
      super("Targets", "Configure which entities combat modules target.", Module.Category.CLIENT);
   }

   @Override
   public void onLoad() {
      if (!this.isEnabled()) {
         this.enable();
      }
   }

   @Override
   public void onDisable() {
      this.enable();
   }

   public boolean isValidPlayerTarget(Entity entity) {
      return !(entity instanceof Player) ? false : this.isValidTarget(entity);
   }

   public boolean isValidTarget(Entity entity) {
      if (entity == mc.player) {
         return false;
      } else if (!(entity instanceof LivingEntity living)) {
         return false;
      } else if (living.isDeadOrDying()) {
         return false;
      } else if (!(entity instanceof Player player)) {
         if (this.hostiles.getValue() && isHostile(entity)) {
            return true;
         } else {
            return this.neutrals.getValue() && isNeutral(entity) ? true : this.passives.getValue() && isPassive(entity);
         }
      } else {
         return this.players.getValue() && !KetamineClub.friendManager.isFriend(player);
      }
   }

   public static boolean isHostile(Entity e) {
      return !(e instanceof Creeper)
            && !(e instanceof Skeleton)
            && !(e instanceof Stray)
            && !(e instanceof WitherSkeleton)
            && (!(e instanceof Zombie) || e instanceof ZombifiedPiglin)
            && !(e instanceof Husk)
            && !(e instanceof Drowned)
            && !(e instanceof Vindicator)
            && !(e instanceof Bogged)
            && !(e instanceof Evoker)
            && !(e instanceof Pillager)
            && !(e instanceof Ravager)
            && !(e instanceof Vex)
            && !(e instanceof Breeze)
            && !(e instanceof Blaze)
            && !(e instanceof WitherBoss)
            && !(e instanceof EnderDragon)
            && !(e instanceof Shulker)
            && !(e instanceof Guardian)
            && !(e instanceof ElderGuardian)
            && !(e instanceof Ghast)
            && !(e instanceof Hoglin)
            && !(e instanceof ZombieVillager)
            && !(e instanceof MagmaCube)
            && !(e instanceof Silverfish)
            && !(e instanceof Endermite)
            && !(e instanceof Slime)
            && !(e instanceof Phantom)
            && !(e instanceof Illusioner)
            && !(e instanceof Witch)
         ? isNeutralEntityType(e) && isAggressiveNow(e)
         : true;
   }

   public static boolean isNeutral(Entity e) {
      return isNeutralEntityType(e) && !isAggressiveNow(e);
   }

   public static boolean isPassive(Entity e) {
      return e instanceof AgeableMob || e instanceof IronGolem golem && golem.isPlayerCreated();
   }

   private static boolean isNeutralEntityType(Entity e) {
      return e instanceof EnderMan
         || e instanceof Piglin
         || e instanceof ZombifiedPiglin
         || e instanceof Spider
         || e instanceof CaveSpider
         || e instanceof PolarBear
         || e instanceof Wolf w && !w.isTame()
         || e instanceof Bee
         || e instanceof Goat
         || e instanceof IronGolem g && !g.isPlayerCreated();
   }

   private static boolean isAggressiveNow(Entity e) {
      if (e instanceof EnderMan enderman) {
         return enderman.isCreepy();
      } else if (e instanceof ZombifiedPiglin piglin) {
         return piglin.isAggressive();
      } else if (e instanceof Piglin piglin) {
         return piglin.isAggressive();
      } else if (e instanceof Spider spider) {
         return spider.isAggressive();
      } else if (e instanceof CaveSpider) {
         return true;
      } else if (e instanceof PolarBear bear) {
         return bear.isAggressive();
      } else if (e instanceof Wolf wolf) {
         return wolf.isAggressive();
      } else {
         return e instanceof Bee bee ? bee.isAngry() : false;
      }
   }
}
