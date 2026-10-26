package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.awt.Color;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.CommandManager;

public class SyncAllCommand extends Command {
   public SyncAllCommand() {
      super("syncall");
      this.setDescription("Syncs clipboard RGB to all module color settings while preserving each alpha");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.executes(ctx -> this.syncAll());
   }

   private int syncAll() {
      Color source = KetamineClubGui.getColorClipboard();
      if (source == null) {
         return this.fail("Copy a color in the ClickGUI color picker first.", new Object[0]);
      } else {
         int changed = 0;
         int seen = 0;

         for (Module module : KetamineClub.moduleManager.getModules()) {
            for (Setting<?> setting : module.getSettings()) {
               if (setting.getValue() instanceof Color current) {
                  seen++;
                  Color synced = new Color(source.getRed(), source.getGreen(), source.getBlue(), current.getAlpha());
                  if (!current.equals(synced)) {
                     setColor(setting, synced);
                     changed++;
                  }
               }
            }
         }

         if (changed > 0) {
            KetamineClub.configManager.save();
         }

         return this.success("Synced {green} %s {reset} of {green} %s {reset} color setting(s), preserving alpha.", new Object[]{changed, seen});
      }
   }

   private static void setColor(Setting<?> setting, Color color) {
      ((Setting<Color>)setting).setValue(color);
   }
}
