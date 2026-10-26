package we.ketamine.club.pingbypass.ssh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SshDeployment {
   public static final int RELAY_PORT_BASE = 20000;
   public static final String READY_MARKER = "KETAMINE_PINGBYPASS_READY";
   private static final String ROOT = ".local/share/ketamine-pingbypass";
   private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5L);
   private static final Duration START_TIMEOUT = Duration.ofMinutes(15L);
   private static Path passwordHelperExecutable;
   private static Path passwordHelperScript;

   private SshDeployment() {
   }

   public static SshDeployment.Tunnel start(
      SshDeployment.Config config,
      String username,
      UUID profileId,
      String minecraftVersion,
      String loaderVersion,
      Path modJar,
      Path fabricApiJar,
      String secret,
      Consumer<String> progress
   ) throws IOException, InterruptedException {
      return start(config, username, profileId, minecraftVersion, loaderVersion, modJar, fabricApiJar, null, secret, progress);
   }

   public static SshDeployment.Tunnel start(
      SshDeployment.Config config,
      String username,
      UUID profileId,
      String minecraftVersion,
      String loaderVersion,
      Path modJar,
      Path fabricApiJar,
      Path viaFabricPlusJar,
      String secret,
      Consumer<String> progress
   ) throws IOException, InterruptedException {
      Objects.requireNonNull(config, "config");
      Objects.requireNonNull(profileId, "profileId");
      Objects.requireNonNull(progress, "progress");
      requireMatch(username, "[A-Za-z0-9_]{1,16}", "Minecraft username");
      requireMatch(minecraftVersion, "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", "Minecraft version");
      requireMatch(loaderVersion, "[0-9][A-Za-z0-9.+_-]{0,63}", "Fabric Loader version");
      requireMatch(secret, "[0-9a-f]{64}", "session secret");
      validateJar(modJar, "ketamine.club");
      validateJar(fabricApiJar, "Fabric API");
      if (viaFabricPlusJar != null) {
         validateJar(viaFabricPlusJar, "ViaFabricPlus");
      }

      if (config.password().isEmpty() && !config.identityFile().isEmpty() && !Files.isReadable(Path.of(config.identityFile()))) {
         throw new IOException("Cannot read the SSH key at " + config.identityFile() + ". Choose a private key or leave blank to use ssh-agent.");
      } else {
         String session = ".local/share/ketamine-pingbypass/sessions/" + UUID.randomUUID();
         SshDeployment.Tunnel tunnel = null;
         boolean directoryCreated = false;

         try {
            report(progress, "Connecting to the VPS...");
            int relayPort = remoteRelayPort(config, progress);
            execute(
               config,
               "cd \"$HOME\" && umask 077 && mkdir -p "
                  + shellQuote(session + "/game/mods")
                  + " && chmod 700 "
                  + shellQuote(".local/share/ketamine-pingbypass")
                  + " "
                  + shellQuote(session),
               null,
               progress
            );
            directoryCreated = true;
            report(progress, "Uploading the client mod and Fabric API...");
            upload(config, session + "/game/mods/ketamineclub.jar", modJar, progress);
            upload(config, session + "/game/mods/fabric-api.jar", fabricApiJar, progress);
            if (viaFabricPlusJar != null) {
               report(progress, "Uploading ViaFabricPlus for the headless worker...");
               upload(config, session + "/game/mods/viafabricplus.jar", viaFabricPlusJar, progress);
            }

            try (InputStream script = SshDeployment.class.getResourceAsStream("/pingbypass/bootstrap.sh")) {
               if (script == null) {
                  throw new IOException("This mod is missing the PingBypass bootstrap. Rebuild the packaged mod with ./gradlew build.");
               }

               execute(config, "cd \"$HOME\" && umask 077 && cat > " + shellQuote(session + "/bootstrap.sh"), script, progress);
            }

            int socksPort;
            int localPort;
            try (
               ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
               ServerSocket socksReservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            ) {
               localPort = reservation.getLocalPort();
               socksPort = socksReservation.getLocalPort();
            }

            List<String> command = sshCommand(config);
            command.addAll(
               List.of(
                  "-C",
                  "-L",
                  "127.0.0.1:" + localPort + ":127.0.0.1:" + relayPort,
                  "-D",
                  "127.0.0.1:" + socksPort,
                  config.host(),
                  "cd \"$HOME\" && exec bash "
                     + shellQuote(session + "/bootstrap.sh")
                     + " "
                     + shellQuote(session)
                     + " "
                     + shellQuote(username)
                     + " "
                     + shellQuote(profileId.toString())
                     + " "
                     + shellQuote(minecraftVersion)
                     + " "
                     + shellQuote(loaderVersion)
                     + " "
                     + Integer.toString(relayPort)
               )
            );
            Process var29 = startSsh(command, config);
            tunnel = new SshDeployment.Tunnel(var29, localPort, socksPort, secret, progress);
            tunnel.awaitReady();
            report(progress, "VPS worker ready. Connecting through the SSH tunnel...");
            return tunnel;
         } catch (InterruptedException | RuntimeException | IOException var26) {
            if (tunnel != null) {
               tunnel.close();
            }

            if (directoryCreated && tunnel == null) {
               removeFailedUpload(config, session);
            }

            throw var26;
         }
      }
   }

   private static void requireMatch(String value, String pattern, String label) {
      if (value == null || !value.matches(pattern)) {
         throw new IllegalArgumentException("Invalid " + label + ".");
      }
   }

   private static void validateJar(Path path, String label) throws IOException {
      if (path != null && Files.isRegularFile(path) && Files.isReadable(path) && path.getFileName().toString().endsWith(".jar")) {
         long size = Files.size(path);
         if (size == 0L || size > 134217728L) {
            throw new IOException(label + " JAR is empty or exceeds the 128 MiB deployment limit.");
         }
      } else {
         throw new IOException("Cannot find the installed " + label + " JAR. PingBypass deployment requires a packaged mod and Fabric API.");
      }
   }

   private static int remoteRelayPort(SshDeployment.Config config, Consumer<String> progress) throws IOException, InterruptedException {
      String output = executeCapture(config, "cd \"$HOME\" && id -u", null, ignored -> {});
      int uid = -1;

      for (String line : output.lines().map(String::strip).toList()) {
         if (line.matches("[0-9]+")) {
            try {
               uid = Math.toIntExact(Long.parseLong(line));
            } catch (ArithmeticException | NumberFormatException var7) {
            }
         }
      }

      if (uid >= 0 && uid <= 45535) {
         return 20000 + uid;
      } else {
         throw new IOException("Could not select a private PingBypass relay port for this SSH account.");
      }
   }

   static String shellQuote(String value) {
      if (value.indexOf(0) >= 0) {
         throw new IllegalArgumentException("NUL is not permitted in shell arguments.");
      } else {
         return "'" + value.replace("'", "'\"'\"'") + "'";
      }
   }

   static List<String> sshCommand(SshDeployment.Config config) {
      boolean password = !config.password().isEmpty();
      List<String> command = new ArrayList<>(
         List.of(
            "ssh",
            "-T",
            "-o",
            password ? "BatchMode=no" : "BatchMode=yes",
            "-o",
            "StrictHostKeyChecking=accept-new",
            "-o",
            password ? "PasswordAuthentication=yes" : "PasswordAuthentication=no",
            "-o",
            "KbdInteractiveAuthentication=no",
            "-o",
            "ForwardAgent=no",
            "-o",
            "ControlMaster=no",
            "-o",
            "ControlPath=none",
            "-o",
            "ExitOnForwardFailure=yes",
            "-o",
            "ConnectTimeout=15",
            "-o",
            "ServerAliveInterval=15",
            "-o",
            "ServerAliveCountMax=3",
            "-p",
            Integer.toString(config.port()),
            "-l",
            config.user()
         )
      );
      if (password) {
         command.addAll(List.of("-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no", "-o", "NumberOfPasswordPrompts=1"));
      } else if (!config.identityFile().isEmpty()) {
         command.addAll(List.of("-o", "IdentitiesOnly=yes", "-i", config.identityFile()));
      }

      return command;
   }

   private static Process startSsh(List<String> command, SshDeployment.Config config) throws IOException {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      if (!config.password().isEmpty()) {
         if (isWindows()) {
            builder.environment().put("SSH_ASKPASS", windowsAskpassExecutable().toString());
         } else {
            builder.environment().put("SSH_ASKPASS", askpassCommand());
         }

         builder.environment().put("SSH_ASKPASS_REQUIRE", "force");
         builder.environment().putIfAbsent("DISPLAY", ":0");
         builder.environment().put("KETAMINE_SSH_PASSWORD", config.password());
      }

      try {
         return builder.start();
      } catch (IOException var4) {
         throw new IOException("Cannot start OpenSSH. Install the OpenSSH client and make sure ssh is on PATH.", var4);
      }
   }

   static synchronized String askpassCommand() throws IOException {
      if (passwordHelperScript == null || !Files.isRegularFile(passwordHelperScript)) {
         Path script = Files.createTempFile("ketamine-pingbypass-askpass-", ".sh");
         script.toFile().deleteOnExit();
         Files.writeString(
            script,
            "#!/bin/sh\ncase \"$SSH_ASKPASS_PROMPT\" in confirm|none) exit 1;; esac\nprintf '%s\\n' \"$KETAMINE_SSH_PASSWORD\"\n",
            StandardCharsets.UTF_8
         );
         Files.setPosixFilePermissions(script, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
         passwordHelperScript = script;
      }

      return passwordHelperScript.toString();
   }

   private static boolean isWindows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
   }

   static synchronized Path windowsAskpassExecutable() throws IOException {
      if (passwordHelperExecutable != null && Files.isRegularFile(passwordHelperExecutable)) {
         return passwordHelperExecutable;
      } else {
         String resource = windowsAskpassResource(System.getProperty("os.arch", ""));
         Path helper = Files.createTempFile("ketamine-pingbypass-askpass-", ".exe");
         helper.toFile().deleteOnExit();

         try (InputStream input = SshDeployment.class.getResourceAsStream(resource)) {
            if (input == null) {
               throw new IOException("The mod is missing its native Windows SSH password helper. Reinstall the packaged mod.");
            }

            Files.copy(input, helper, StandardCopyOption.REPLACE_EXISTING);
         }

         if (Files.size(helper) < 1024L) {
            throw new IOException("The Windows SSH password helper is damaged. Reinstall the packaged mod.");
         } else {
            passwordHelperExecutable = helper;
            return helper;
         }
      }
   }

   static String windowsAskpassResource(String architecture) throws IOException {
      String var1 = architecture.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "amd64", "x86_64" -> "/pingbypass/askpass-windows-x86_64.exe";
         case "aarch64", "arm64" -> "/pingbypass/askpass-windows-aarch64.exe";
         default -> throw new IOException("PingBypass password login does not support this Windows CPU architecture: " + architecture);
      };
   }

   private static void upload(SshDeployment.Config config, String destination, Path file, Consumer<String> progress) throws IOException, InterruptedException {
      try (InputStream input = Files.newInputStream(file)) {
         execute(config, "cd \"$HOME\" && umask 077 && cat > " + shellQuote(destination), input, progress);
      }
   }

   private static void execute(SshDeployment.Config config, String remoteCommand, InputStream input, Consumer<String> progress) throws IOException, InterruptedException {
      executeCapture(config, remoteCommand, input, progress);
   }

   private static String executeCapture(SshDeployment.Config config, String remoteCommand, InputStream input, Consumer<String> progress) throws IOException, InterruptedException {
      List<String> command = sshCommand(config);
      command.add(config.host());
      command.add(remoteCommand);
      Process process = startSsh(command, config);
      SshDeployment.OutputTail tail = new SshDeployment.OutputTail();
      Thread reader = Thread.ofVirtual().name("pingbypass-ssh-output").start(() -> readOutput(process, tail, progress, null, null));
      CompletableFuture<Void> upload = new CompletableFuture<>();
      Thread writer = Thread.ofVirtual().name("pingbypass-ssh-upload").start(() -> {
         try (OutputStream output = process.getOutputStream()) {
            if (input != null) {
               input.transferTo(output);
            }

            upload.complete(null);
         } catch (IOException var8x) {
            upload.completeExceptionally(var8x);
         }
      });

      String failure;
      try {
         if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IOException("SSH upload timed out. Check VPS connectivity and available disk space.");
         }

         reader.join(1000L);
         if (process.exitValue() != 0) {
            throw sshFailure(tail.text());
         }

         try {
            upload.get(1L, TimeUnit.SECONDS);
         } catch (ExecutionException var15) {
            throw new IOException("Could not upload PingBypass files over SSH.", var15.getCause());
         } catch (TimeoutException var16) {
            throw new IOException("SSH closed before the upload completed.", var16);
         }

         failure = tail.text();
      } finally {
         process.destroy();
         if (process.isAlive()) {
            process.destroyForcibly();
         }

         reader.interrupt();
         writer.interrupt();
      }

      return failure;
   }

   private static void removeFailedUpload(SshDeployment.Config config, String session) {
      boolean interrupted = Thread.interrupted();

      try {
         List<String> command = sshCommand(config);
         command.add(config.host());
         command.add("cd \"$HOME\" && rm -rf -- " + shellQuote(session));
         Process cleanup = startSsh(command, config);
         cleanup.getOutputStream().close();
         if (!cleanup.waitFor(5L, TimeUnit.SECONDS)) {
            cleanup.destroyForcibly();
         }
      } catch (IOException var9) {
      } catch (InterruptedException var10) {
         interrupted = true;
      } finally {
         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private static IOException sshFailure(String output) {
      String hint = "VPS connection failed.";
      if (output.contains("CreateProcessW failed error:193")
         || output.contains("CreateProcessW failed error 193")
         || output.contains("CreateProcessW failed error:2")
         || output.contains("CreateProcessW failed error 2")) {
         hint = "Windows could not run the SSH password helper. Install the latest PingBypass JAR with the native Windows helper.";
      } else if (output.contains("REMOTE HOST IDENTIFICATION HAS CHANGED")) {
         hint = "The VPS identity has changed since your last connection. Ask the VPS owner to confirm the change before updating the saved SSH host key.";
      } else if (output.contains("Host key verification failed")) {
         hint = "OpenSSH could not accept the VPS identity. Check the SSH host-key error below; new hosts are saved automatically.";
      } else if (output.contains("Permission denied")) {
         hint = "SSH authentication failed. Check the VPS username and password, or the private key if you use key authentication.";
      } else if (output.contains("Address already in use") || output.contains("cannot listen to port")) {
         hint = "The PingBypass port is already in use. Close the other session and connect again.";
      } else if (output.contains("administratively prohibited")) {
         hint = "The VPS SSH server must allow TCP forwarding (AllowTcpForwarding local or yes).";
      }

      return new IOException(hint + (output.isBlank() ? "" : "\n" + output));
   }

   private static void report(Consumer<String> progress, String message) {
      try {
         progress.accept(message);
      } catch (RuntimeException var3) {
      }
   }

   private static void readOutput(Process process, SshDeployment.OutputTail tail, Consumer<String> progress, CompletableFuture<Void> ready, String secret) {
      try {
         String line;
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while ((line = reader.readLine()) != null) {
               if (secret != null) {
                  line = line.replace(secret, "[redacted]");
               }

               line = line.replaceAll("\\x1B\\[[0-9;]*[A-Za-z]", "").replaceAll("[\\p{Cntrl}&&[^\\t]]", "");
               if (line.length() > 2048) {
                  line = line.substring(0, 2048);
               }

               tail.add(line);
               if (ready != null && line.contains("KETAMINE_PINGBYPASS_READY")) {
                  ready.complete(null);
               } else if (!line.isBlank()) {
                  report(progress, line);
               }
            }
         } catch (IOException var15) {
            tail.add("SSH output closed: " + var15.getMessage());
         }
      } finally {
         if (ready != null) {
            ready.completeExceptionally(sshFailure(tail.text()));
         }
      }
   }

   public record Config(String host, int port, String user, String identityFile, String password) {
      public Config(String host, int port, String user, String identityFile) {
         this(host, port, user, identityFile, "");
      }

      public Config(String host, int port, String user, String identityFile, String password) {
         host = Objects.requireNonNull(host, "VPS host").trim();
         user = Objects.requireNonNull(user, "SSH user").trim();
         if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
         }

         if (!host.matches("[A-Za-z0-9:][A-Za-z0-9._:%-]{0,252}")) {
            throw new IllegalArgumentException("Enter a VPS hostname, SSH host alias, or IP address without a port.");
         } else if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("SSH port must be between 1 and 65535.");
         } else if (!user.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Enter the VPS SSH username, for example ubuntu.");
         } else {
            identityFile = identityFile == null ? "" : identityFile.trim();
            if (identityFile.indexOf(0) < 0 && identityFile.indexOf(10) < 0 && identityFile.indexOf(13) < 0) {
               if (identityFile.startsWith("~/") || identityFile.startsWith("~\\")) {
                  identityFile = Path.of(System.getProperty("user.home"), identityFile.substring(2)).toString();
               }

               password = password == null ? "" : password;
               if (password.indexOf(0) < 0 && password.indexOf(10) < 0 && password.indexOf(13) < 0) {
                  this.host = host;
                  this.port = port;
                  this.user = user;
                  this.identityFile = identityFile;
                  this.password = password;
               } else {
                  throw new IllegalArgumentException("The SSH password must not contain control characters.");
               }
            } else {
               throw new IllegalArgumentException("The SSH key path must not contain control characters.");
            }
         }
      }
   }

   private static final class OutputTail {
      private final ArrayDeque<String> lines = new ArrayDeque<>();

      private OutputTail() {
      }

      synchronized void add(String line) {
         if (this.lines.size() == 12) {
            this.lines.removeFirst();
         }

         this.lines.addLast(line);
      }

      synchronized String text() {
         return String.join("\n", this.lines);
      }
   }

   public static final class Tunnel implements AutoCloseable {
      private final Process process;
      private final int localPort;
      private final int socksPort;
      private final AtomicBoolean closed = new AtomicBoolean();
      private final CompletableFuture<Void> ready = new CompletableFuture<>();
      private final SshDeployment.OutputTail tail = new SshDeployment.OutputTail();
      private final Thread heartbeat;

      private Tunnel(Process process, int localPort, int socksPort, String secret, Consumer<String> progress) throws IOException {
         this.process = process;
         this.localPort = localPort;
         this.socksPort = socksPort;

         try {
            process.getOutputStream().write((secret + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
         } catch (IOException var7) {
            process.destroyForcibly();
            throw var7;
         }

         Thread.ofVirtual().name("pingbypass-worker-output").start(() -> SshDeployment.readOutput(process, this.tail, progress, this.ready, secret));
         this.heartbeat = Thread.ofVirtual().name("pingbypass-ssh-heartbeat").start(() -> {
            try {
               while (!this.closed.get() && process.isAlive()) {
                  Thread.sleep(10000L);
                  process.getOutputStream().write(10);
                  process.getOutputStream().flush();
               }
            } catch (IOException var3x) {
               this.ready.completeExceptionally(SshDeployment.sshFailure(this.tail.text()));
               this.close();
            } catch (InterruptedException var4x) {
               Thread.currentThread().interrupt();
            }
         });
      }

      public String host() {
         return "127.0.0.1";
      }

      public int localPort() {
         return this.localPort;
      }

      public int socksPort() {
         return this.socksPort;
      }

      public boolean isAlive() {
         return !this.closed.get() && this.process.isAlive();
      }

      public String lastOutput() {
         return this.tail.text();
      }

      private void awaitReady() throws IOException, InterruptedException {
         try {
            this.ready.get(SshDeployment.START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
         } catch (ExecutionException var4) {
            if (var4.getCause() instanceof IOException io) {
               throw io;
            }

            throw new IOException("Could not start the VPS worker.", var4.getCause());
         } catch (TimeoutException var5) {
            throw new IOException("VPS worker did not start within 15 minutes. Check its output for download or memory errors.\n" + this.tail.text(), var5);
         }

         if (!this.isAlive()) {
            throw SshDeployment.sshFailure(this.tail.text());
         }
      }

      @Override
      public void close() {
         if (this.closed.compareAndSet(false, true)) {
            this.ready.completeExceptionally(new IOException("PingBypass connection cancelled."));
            this.heartbeat.interrupt();

            try {
               this.process.getOutputStream().close();
            } catch (IOException var2) {
            }

            Thread.ofVirtual().name("pingbypass-ssh-stop").start(() -> {
               try {
                  if (!this.process.waitFor(5L, TimeUnit.SECONDS)) {
                     this.process.destroy();
                     if (!this.process.waitFor(2L, TimeUnit.SECONDS)) {
                        this.process.destroyForcibly();
                     }
                  }
               } catch (InterruptedException var2x) {
                  this.process.destroyForcibly();
                  Thread.currentThread().interrupt();
               }
            });
         }
      }
   }
}
