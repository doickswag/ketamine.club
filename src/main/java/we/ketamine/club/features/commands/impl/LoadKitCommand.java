package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.modules.player.InstantRekitModule;
import we.ketamine.club.manager.CommandManager;

public class LoadKitCommand extends Command {
   public LoadKitCommand() {
      super("loadkit");
      this.setDescription("Selects which saved kit InstantRekit will apply");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.load("default"));
      builder.then(argument("name", StringArgumentType.word()).executes(ctx -> this.load(StringArgumentType.getString(ctx, "name"))));
   }

   private int load(String name) {
      InstantRekitModule module = KetamineClub.moduleManager.getModuleByClass(InstantRekitModule.class);
      if (module == null) {
         return this.fail("InstantRekit module is not registered.", new Object[0]);
      } else if (!module.loadKit(name)) {
         return module.listKits().isEmpty()
            ? this.fail("No kit named %s. No kits saved yet \u2014 use .savekit <name>.", new Object[]{name})
            : this.fail("No kit named %s. Saved kits: %s", new Object[]{name, String.join(", ", module.listKits())});
      } else {
         return this.success("Loaded kit {green} %s {reset}. Open a container to apply it.", new Object[]{name});
      }
   }
}
