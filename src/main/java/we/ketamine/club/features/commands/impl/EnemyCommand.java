package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.StringJoiner;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.commands.argument.EnemyArgumentType;
import we.ketamine.club.features.commands.argument.OnlinePlayerArgumentType;
import we.ketamine.club.manager.CommandManager;

public class EnemyCommand extends Command {
   public EnemyCommand() {
      super("enemy", "enemies", "e");
      this.setDescription("Manages your enemies list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(literal("list").executes(ctx -> {
         List<String> enemies = KetamineClub.enemyManager.getEnemies();
         if (enemies.isEmpty()) {
            return this.success("You have no enemies :)", new Object[0]);
         } else {
            StringJoiner joiner = new StringJoiner(",");
            enemies.forEach(joiner::add);
            return this.success("Enemies (%s): %s", new Object[]{enemies.size(), joiner});
         }
      }))).then(literal("clear").executes(ctx -> {
         KetamineClub.enemyManager.clearEnemies();
         return this.success("Cleared enemies list", new Object[0]);
      }))).then(literal("add").then(argument("username", OnlinePlayerArgumentType.onlinePlayer()).executes(ctx -> {
         String username = OnlinePlayerArgumentType.getOnlinePlayer(ctx, "username");
         if (KetamineClub.enemyManager.isEnemy(username)) {
            return this.success("{red} %s {reset} is already on your enemies list.", new Object[]{username});
         } else {
            KetamineClub.enemyManager.addEnemy(username);
            return this.success("Added {red} %s {reset} to your enemies list", new Object[]{username});
         }
      })))).then(literal("remove").then(argument("username", EnemyArgumentType.enemy()).executes(ctx -> {
         String username = EnemyArgumentType.getEnemy(ctx, "username");
         if (!KetamineClub.enemyManager.isEnemy(username)) {
            return this.success("{red} %s {reset} is not on your enemies list.", new Object[]{username});
         } else {
            KetamineClub.enemyManager.removeEnemy(username);
            return this.success("Removed {red} %s {reset} from your enemies list", new Object[]{username});
         }
      })));
   }
}
