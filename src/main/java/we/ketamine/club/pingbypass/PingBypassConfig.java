package we.ketamine.club.pingbypass;

public record PingBypassConfig(String vpsHost, int sshPort, String sshUser, String identityFile, String password, String serverAddress) {
   public PingBypassConfig(String vpsHost, int sshPort, String sshUser, String identityFile, String serverAddress) {
      this(vpsHost, sshPort, sshUser, identityFile, "", serverAddress);
   }

   public PingBypassConfig(String vpsHost, int sshPort, String sshUser, String identityFile, String password, String serverAddress) {
      vpsHost = vpsHost.strip();
      sshUser = sshUser.strip();
      identityFile = identityFile.strip();
      password = password == null ? "" : password;
      serverAddress = serverAddress.strip();
      if (vpsHost.isEmpty() || sshUser.isEmpty() || serverAddress.isEmpty()) {
         throw new IllegalArgumentException("Enter the VPS host, SSH user, and Minecraft server.");
      } else if (sshPort < 1 || sshPort > 65535) {
         throw new IllegalArgumentException("SSH port must be 1\u201365535.");
      } else if (password.indexOf(0) < 0 && password.indexOf(10) < 0 && password.indexOf(13) < 0) {
         if (serverAddress.length() <= 255 && !serverAddress.chars().anyMatch(Character::isISOControl)) {
            this.vpsHost = vpsHost;
            this.sshPort = sshPort;
            this.sshUser = sshUser;
            this.identityFile = identityFile;
            this.password = password;
            this.serverAddress = serverAddress;
         } else {
            throw new IllegalArgumentException("Invalid Minecraft server address.");
         }
      } else {
         throw new IllegalArgumentException("The SSH password must not contain control characters.");
      }
   }
}
