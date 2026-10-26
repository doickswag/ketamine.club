package we.ketamine.club.pingbypass.ui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.PingBypassConfig;
import we.ketamine.club.pingbypass.PingBypassProtocol;

public final class PingBypassScreen extends Screen {
   private static final int TEXT = -1644048;
   private static final int MUTED = -5919041;
   private static String lastHost = "";
   private static String lastPort = "22";
   private static String lastUser = "";
   private static String lastKey = "";
   private static String lastPassword = "";
   private static String lastServer = "";
   private final Screen parent;
   private EditBox host;
   private EditBox port;
   private EditBox user;
   private EditBox key;
   private EditBox password;
   private EditBox server;
   private Button connect;
   private Button disconnect;
   private Button protocol;
   private String validationError;
   private int left;
   private int top;
   private int formWidth;

   public PingBypassScreen(Screen parent) {
      super(Component.literal("PingBypass"));
      this.parent = parent;
   }

   protected void init() {
      this.formWidth = Math.min(480, this.width - 24);
      this.left = (this.width - this.formWidth) / 2;
      this.top = Math.max(8, (this.height - 264) / 2);
      this.protocol = null;
      if (PingBypassProtocol.hasViaFabricPlus()) {
         this.protocol = (Button)this.addRenderableWidget(Button.builder(Component.literal("Server version: " + this.selectedProtocolLabel()), button -> {
            this.rememberFields();

            try {
               PingBypassProtocol.openSelector(this);
            } catch (IllegalStateException var3x) {
               this.validationError = var3x.getMessage();
            }
         }).bounds(this.left, this.top + 16, this.formWidth, 18).build());
         this.protocol
            .setTooltip(
               Tooltip.create(
                  Component.literal("ViaFabricPlus translates on the remote worker. Select a Java release version from 1.7.2 onward, or Auto Detect.")
               )
            );
      }

      int half = (this.formWidth - 8) / 2;
      int hostWidth = this.formWidth - 76;
      this.host = this.field(this.left, this.top + 48, hostWidth, "VPS host", lastHost, "vps.example.com");
      this.port = this.field(this.left + hostWidth + 8, this.top + 48, 68, "SSH port", lastPort, "22");
      this.port.setMaxLength(5);
      this.user = this.field(this.left, this.top + 84, half, "SSH username", lastUser, "pingbypass");
      this.password = this.field(this.left + half + 8, this.top + 84, half, "SSH password (optional)", lastPassword, "Leave empty for SSH key");
      this.password.addFormatter((value, cursor) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
      this.password.setTooltip(Tooltip.create(Component.literal("Use the password assigned to this VPS account. It is held in memory only while connecting.")));
      this.key = this.field(this.left, this.top + 120, this.formWidth, "SSH private key file (optional)", lastKey, "Leave empty when using a password");
      this.key
         .setTooltip(
            Tooltip.create(Component.literal("Local private key path, or leave empty to use your SSH agent and default keys. The key stays on this computer."))
         );
      this.server = this.field(this.left, this.top + 156, this.formWidth, "Minecraft server", lastServer, "play.example.com:25565");
      int buttonY = Math.min(this.height - 28, this.top + 240);
      int buttonWidth = (this.formWidth - 16) / 3;
      this.connect = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Connect"), button -> this.connect()).bounds(this.left, buttonY, buttonWidth, 20).build()
      );
      this.disconnect = (Button)this.addRenderableWidget(Button.builder(Component.literal("Disconnect"), button -> {
         this.validationError = null;
         PingBypass.get().disconnect();
      }).bounds(this.left + buttonWidth + 8, buttonY, buttonWidth, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Back"), button -> this.onClose()).bounds(this.left + (buttonWidth + 8) * 2, buttonY, buttonWidth, 20).build()
      );
      this.setInitialFocus(this.host);
      this.updateButtons();
   }

   private EditBox field(int x, int y, int fieldWidth, String name, String value, String hint) {
      EditBox box = new EditBox(this.font, x, y, fieldWidth, 20, Component.literal(name));
      box.setMaxLength(1024);
      box.setValue(value);
      box.setHint(Component.literal(hint));
      box.setResponder(ignored -> {
         this.validationError = null;
         this.rememberFields();
      });
      return (EditBox)this.addRenderableWidget(box);
   }

   private void connect() {
      this.rememberFields();

      try {
         if (this.host.getValue().isBlank()) {
            throw new IllegalArgumentException("Enter the VPS hostname or IP address.");
         }

         if (this.user.getValue().isBlank()) {
            throw new IllegalArgumentException("Enter the SSH username on your VPS.");
         }

         if (this.server.getValue().isBlank()) {
            throw new IllegalArgumentException("Enter the Minecraft server address.");
         }

         int sshPort;
         try {
            sshPort = Integer.parseInt(this.port.getValue().trim());
         } catch (NumberFormatException var3) {
            throw new IllegalArgumentException("SSH port must be a number from 1 to 65535.");
         }

         if (sshPort < 1 || sshPort > 65535) {
            throw new IllegalArgumentException("SSH port must be a number from 1 to 65535.");
         }

         this.validationError = null;
         PingBypass.get()
            .connect(
               new PingBypassConfig(
                  this.host.getValue().trim(),
                  sshPort,
                  this.user.getValue().trim(),
                  this.key.getValue().trim(),
                  this.password.getValue(),
                  this.server.getValue().trim()
               )
            );
      } catch (IllegalStateException | IllegalArgumentException var4) {
         this.validationError = var4.getMessage();
      }

      this.updateButtons();
   }

   public void tick() {
      this.updateButtons();
   }

   private void updateButtons() {
      boolean busy = PingBypass.get().isBusy();
      this.connect.active = !busy;
      this.disconnect.active = busy;
      this.disconnect.setMessage(Component.literal(PingBypass.get().isConnected() ? "Disconnect" : "Cancel"));
      if (this.protocol != null) {
         this.protocol.active = !busy;
         this.protocol.setMessage(Component.literal("Server version: " + this.selectedProtocolLabel()));
      }

      for (EditBox field : new EditBox[]{this.host, this.port, this.user, this.password, this.key, this.server}) {
         field.setEditable(!busy);
      }
   }

   private void rememberFields() {
      if (this.host != null) {
         lastHost = this.host.getValue();
      }

      if (this.port != null) {
         lastPort = this.port.getValue();
      }

      if (this.user != null) {
         lastUser = this.user.getValue();
      }

      if (this.key != null) {
         lastKey = this.key.getValue();
      }

      if (this.password != null) {
         lastPassword = this.password.getValue();
      }

      if (this.server != null) {
         lastServer = this.server.getValue();
      }
   }

   private String selectedProtocolLabel() {
      try {
         return PingBypass.get().serverVersionLabel();
      } catch (IllegalStateException | IllegalArgumentException var2) {
         this.validationError = var2.getMessage();
         return "Select a supported version";
      }
   }

   public void removed() {
      this.rememberFields();
   }

   public void onClose() {
      this.rememberFields();
      this.minecraft.gui.setScreen(this.parent);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      graphics.centeredText(this.font, this.title, this.width / 2, this.top, -1644048);
      if (this.protocol == null) {
         graphics.centeredText(this.font, "VPS gameplay. Local movement, visuals and controls.", this.width / 2, this.top + 18, -5919041);
      }

      this.label(graphics, this.host, "VPS host");
      this.label(graphics, this.port, "SSH port");
      this.label(graphics, this.user, "SSH username");
      this.label(graphics, this.password, "SSH password (optional)");
      this.label(graphics, this.key, "Private key file (optional)");
      this.label(graphics, this.server, "Minecraft server");
      graphics.textWithWordWrap(
         this.font,
         Component.literal("Use a VPS password or SSH key. Your Minecraft account stays signed in here."),
         this.left,
         this.top + 183,
         this.formWidth,
         -5919041
      );
      String status = this.validationError != null ? this.validationError : PingBypass.get().status();
      int color = this.validationError != null ? -29299 : (PingBypass.get().isConnected() ? -8331602 : -5919041);
      List<FormattedCharSequence> lines = this.font.split(Component.literal(status), this.formWidth);
      int availableLines = Math.max(1, (this.connect.getY() - (this.top + 208) - 4) / 9);

      for (int line = 0; line < Math.min(lines.size(), availableLines); line++) {
         FormattedCharSequence text = lines.get(line);
         graphics.text(this.font, text, this.left, this.top + 208 + line * 9, color);
      }

      if (lines.size() > availableLines
         && mouseX >= this.left
         && mouseX <= this.left + this.formWidth
         && mouseY >= this.top + 208
         && mouseY < this.connect.getY()) {
         graphics.setTooltipForNextFrame(this.font, Component.literal(status), mouseX, mouseY);
      }

      super.extractRenderState(graphics, mouseX, mouseY, delta);
   }

   private void label(GuiGraphicsExtractor graphics, EditBox field, String label) {
      graphics.text(this.font, label, field.getX(), field.getY() - 11, -5919041);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
