package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.modules.player.InstantRekitModule;
import we.ketamine.club.manager.CommandManager;

public class SaveKitCommand extends Command {
   public SaveKitCommand() {
      super("savekit");
      this.setDescription("Snapshots your current inventory as a named InstantRekit kit");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.save("default"));
      builder.then(argument("name", StringArgumentType.word()).executes(ctx -> this.save(StringArgumentType.getString(ctx, "name"))));
   }

   private int save(String name) {
      InstantRekitModule module = KetamineClub.moduleManager.getModuleByClass(InstantRekitModule.class);
      if (module == null) {
         return this.fail("InstantRekit module is not registered.", new Object[0]);
      } else {
         int saved = module.saveKit(name);
         if (saved == 0) {
            return this.fail("Inventory is empty \u2014 nothing to save.", new Object[0]);
         } else {
            KetamineClub.configManager.save();
            return this.success("Saved kit {green} %s {reset} with {green} %s {reset} item slot(s).", new Object[]{name, saved});
         }
      }
   }
}
