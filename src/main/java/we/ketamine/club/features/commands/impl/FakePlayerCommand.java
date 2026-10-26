package we.ketamine.club.features.commands.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.manager.CommandManager;

public class FakePlayerCommand extends Command {
   private static final byte TOTEM_POP_EVENT_ID = 35;
   private static final EquipmentSlot[] COPIED_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final List<RemotePlayer> spawned = new ArrayList<>();
   private int nextId = -13337;

   public FakePlayerCommand() {
      super("fakeplayer", "fp");
      this.setDescription("Spawns a client-side fake player to test on");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.executes(ctx -> this.spawn("FakePlayer")))
                  .then(literal("remove").executes(ctx -> this.removeAll())))
               .then(literal("pop").executes(ctx -> this.popAll())))
            .then(literal("swim").executes(ctx -> this.toggleSwim())))
         .then(argument("name", StringArgumentType.word()).executes(ctx -> this.spawn(StringArgumentType.getString(ctx, "name"))));
   }

   private int popAll() {
      if (!nullCheck() && mc.getConnection() != null) {
         int popped = 0;

         for (RemotePlayer fake : this.spawned) {
            if (fake.level() == mc.level) {
               mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(fake, (byte)35));
               popped++;
            }
         }

         return popped == 0
            ? this.fail("No fake players to pop. Spawn one first.", new Object[0])
            : this.success("Popped %s fake player(s)", new Object[]{popped});
      } else {
         return this.fail("You need to be in a world to do that.", new Object[0]);
      }
   }

   private int toggleSwim() {
      if (nullCheck()) {
         return this.fail("You need to be in a world to do that.", new Object[0]);
      } else {
         boolean anyStanding = false;
         int found = 0;

         for (RemotePlayer fake : this.spawned) {
            if (fake.level() == mc.level) {
               found++;
               if (fake.getPose() != Pose.SWIMMING) {
                  anyStanding = true;
               }
            }
         }

         if (found == 0) {
            return this.fail("No fake players. Spawn one first.", new Object[0]);
         } else {
            Pose pose = anyStanding ? Pose.SWIMMING : Pose.STANDING;

            for (RemotePlayer fakex : this.spawned) {
               if (fakex.level() == mc.level) {
                  fakex.setPose(pose);
                  fakex.refreshDimensions();
               }
            }

            return this.success(pose == Pose.SWIMMING ? "Fake player(s) now in swim pose." : "Fake player(s) now standing.", new Object[0]);
         }
      }
   }

   private int spawn(String name) {
      if (nullCheck()) {
         return this.fail("You need to be in a world to do that.", new Object[0]);
      } else {
         int despawned = this.despawnAll();
         GameProfile profile = new GameProfile(UUID.randomUUID(), name);
         RemotePlayer fake = new RemotePlayer(mc.level, profile);
         fake.setId(this.nextId--);
         fake.copyPosition(mc.player);
         fake.setYHeadRot(mc.player.getYHeadRot());
         fake.setHealth(20.0F);

         for (EquipmentSlot slot : COPIED_SLOTS) {
            fake.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
         }

         mc.level.addEntity(fake);
         this.spawned.add(fake);
         return despawned > 0
            ? this.success("Replaced fake player with {green} %s", new Object[]{name})
            : this.success("Spawned fake player {green} %s", new Object[]{name});
      }
   }

   private int removeAll() {
      if (nullCheck()) {
         this.spawned.clear();
         return this.fail("You need to be in a world to do that.", new Object[0]);
      } else {
         int removed = this.despawnAll();
         return removed == 0 ? this.success("No fake players to remove.", new Object[0]) : this.success("Removed %s fake player(s)", new Object[]{removed});
      }
   }

   private int despawnAll() {
      int removed = 0;
      Iterator<RemotePlayer> it = this.spawned.iterator();

      while (it.hasNext()) {
         RemotePlayer fake = it.next();
         it.remove();
         if (fake.level() == mc.level) {
            mc.level.removeEntity(fake.getId(), RemovalReason.DISCARDED);
            removed++;
         }
      }

      return removed;
   }
}
