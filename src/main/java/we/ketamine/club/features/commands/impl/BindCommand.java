package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.commands.argument.ModuleArgumentType;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.manager.CommandManager;
import we.ketamine.club.util.KeyboardUtil;

public class BindCommand extends Command {
   private Module module;

   public BindCommand() {
      super("bind", "setbind");
      this.setDescription("Sets a key bind for a module");
      EVENT_BUS.register(this);
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(argument("module", ModuleArgumentType.module(true)).executes(ctx -> {
         this.module = ModuleArgumentType.getModule(ctx, "module");
         return this.success("Press any key...", new Object[0]);
      }));
   }

   @Subscribe
   public void onKey(KeyInputEvent event) {
      if (!nullCheck() && this.module != null && event.getKey() != -1 && event.getAction() == 1) {
         if (event.getKey() == 256) {
            this.module = null;
            sendMessage("Operation canceled.", new Object[0]);
         } else {
            sendMessage("Bind for {green} %s {} set to {green} %s", new Object[]{this.module.getName(), KeyboardUtil.getKeyName(event.getKey())});
            this.module.bind.setValue(new Bind(event.getKey()));
            this.module = null;
         }
      }
   }

   @Subscribe
   public void onMouse(MouseInputEvent event) {
      if (!nullCheck() && this.module != null && event.getAction() == 1) {
         Bind bind = Bind.fromMouseButton(event.getButton());
         sendMessage("Bind for {green} %s {} set to {green} %s", new Object[]{this.module.getName(), bind.toString()});
         this.module.bind.setValue(bind);
         this.module = null;
      }
   }
}
