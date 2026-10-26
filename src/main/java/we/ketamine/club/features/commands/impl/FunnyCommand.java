package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.manager.CommandManager;

public class FunnyCommand extends Command {
   public FunnyCommand() {
      super("funny");
      this.setDescription("Toggles visibility of the Funny category in the ClickGui");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> {
         CommandManager cm = (CommandManager)ctx.getSource();
         boolean nowVisible = !cm.isFunnyVisible();
         cm.setFunnyVisible(nowVisible);
         if (!nowVisible) {
            KetamineClub.moduleManager.stream().filter(m -> m.getCategory() == Module.Category.FUNNY).filter(Feature::isEnabled).forEach(Module::disable);
         }

         KetamineClubGui.getInstance().reload();
         return this.success("Funny category is now %s", new Object[]{nowVisible ? "{green} visible" : "{red} hidden"});
      });
   }
}
