package we.ketamine.club.pingbypass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.pingbypass.ssh.SshDeployment;
import we.ketamine.club.pingbypass.ui.PingBypassScreen;
import we.ketamine.club.util.traits.Util;

public final class PingBypass {
   private static final PingBypass INSTANCE = new PingBypass();
   private static final boolean WORKER = Boolean.getBoolean("ketamine.pingbypass.worker");
   private static final int MAX_PENDING_WORKER_PACKETS = 65536;
   private volatile PingBypass.Viewer viewer;
   private volatile PingBypass.Worker worker;
   private volatile ServerSocket workerListener;
   private volatile String status = "Disconnected";
   private boolean shuttingDown;
   private String lastRelations = "";
   private int ticks;

   private PingBypass() {
   }

   public static PingBypass get() {
      return INSTANCE;
   }

   public static boolean isWorker() {
      return WORKER;
   }

   public boolean isViewer() {
      return PingBypassModules.isViewer();
   }

   public boolean isBusy() {
      return this.viewer != null;
   }

   public boolean isConnected() {
      PingBypass.Viewer session = this.viewer;
      Minecraft mc = Minecraft.getInstance();
      return session != null
         && session.started
         && session.wire != null
         && session.wire.isOpen()
         && mc.player != null
         && mc.getConnection() != null
         && mc.getConnection().getConnection() == session.connection;
   }

   public String status() {
      return this.status;
   }

   public String serverVersionLabel() {
      PingBypass.Viewer session = this.viewer;
      return session != null ? session.targetProtocol.label() : PingBypassProtocol.selectionLabel();
   }

   public boolean isTranslatedViewer() {
      PingBypass.Viewer session = this.viewer;
      return session != null && !session.closed.get() && session.targetProtocol.requiresTranslation();
   }

   public boolean suppressViewerMovement() {
      PingBypass.Viewer session = this.viewer;
      return session != null && !session.closed.get() && session.authoritativeMotion;
   }

   public boolean isWorkerActive() {
      return WORKER && this.worker != null && this.worker.started && !this.worker.closed.get();
   }

   public void init() {
      PingBypassRenderState.init();
      PayloadTypeRegistry.clientboundPlay().register(PingBypassMotionPayload.TYPE, PingBypassMotionPayload.CODEC);
      ClientPlayNetworking.registerGlobalReceiver(
         PingBypassMotionPayload.TYPE, (payload, context) -> this.applyMotion(JsonParser.parseString(payload.state()).getAsJsonObject())
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> this.tick());
      if (WORKER) {
         KetamineClub.moduleManager.getModules().forEach(module -> {
            if (module.isToggled()) {
               module.disable();
            }
         });
         Minecraft.getInstance().options.pauseOnLostFocus = false;
         Thread.ofVirtual().name("PingBypass-worker-listener").start(this::listen);
      }
   }

   public void connect(PingBypassConfig config) {
      Minecraft mc = Minecraft.getInstance();
      if (!WORKER && this.viewer == null) {
         if (!ServerAddress.isValidAddress(config.serverAddress())) {
            this.status = "Invalid Minecraft server address.";
         } else {
            PingBypass.Viewer session = new PingBypass.Viewer(config, mc.getUser().getName(), mc.getUser().getProfileId());
            this.viewer = session;
            this.status = "Preparing VPS connection\u2026";
            session.deployment = Thread.ofVirtual()
               .name("PingBypass-deploy")
               .start(
                  () -> {
                     try {
                        Path mod = artifact("ketamineclub");
                        Path fabric = artifact("fabric-api");
                        Path via = PingBypassProtocol.hasViaFabricPlus() ? artifact("viafabricplus") : null;
                        String loader = ((ModContainer)FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow())
                           .getMetadata()
                           .getVersion()
                           .getFriendlyString();
                        SshDeployment.Config sshConfig = new SshDeployment.Config(
                           config.vpsHost(), config.sshPort(), config.sshUser(), config.identityFile(), config.password()
                        );
                        SshDeployment.Tunnel tunnel = SshDeployment.start(
                           sshConfig,
                           session.name,
                           session.profile,
                           SharedConstants.getCurrentVersion().name(),
                           loader,
                           mod,
                           fabric,
                           via,
                           session.secret,
                           progress -> {
                              if (this.viewer == session) {
                                 this.status = progress;
                              }
                           }
                        );
                        session.tunnel = tunnel;
                        if (session.closed.get()) {
                           tunnel.close();
                           return;
                        }

                        Socket socket = new Socket();
                        session.connectingSocket = socket;
                        socket.connect(new InetSocketAddress(tunnel.host(), tunnel.localPort()), 10000);
                        session.wire = new RelayWire(socket, frame -> this.receiveViewer(session, frame), reason -> this.stopViewer(session, reason));
                        session.bridge = new ViewerSocket(reason -> this.stopViewer(session, reason));
                        if (!session.closed.get() && this.viewer == session) {
                           JsonObject hello = message("hello");
                           hello.addProperty("version", 1);
                           hello.addProperty("game", SharedConstants.getCurrentVersion().name());
                           hello.addProperty("secret", session.secret);
                           hello.addProperty("profile", session.profile.toString());
                           hello.addProperty("name", session.name);
                           hello.addProperty("server", config.serverAddress());
                           hello.add("targetProtocol", session.targetProtocol.toJson());
                           session.wire.json(hello.toString());
                           session.wire.start();
                           return;
                        }
                     } catch (Exception var14) {
                        this.stopViewer(session, "PingBypass setup failed: " + safeMessage(var14));
                        return;
                     } finally {
                        if (session.closed.get()) {
                           this.closeViewerResources(session);
                        }
                     }
                  }
               );
         }
      }
   }

   public void disconnect() {
      PingBypass.Viewer session = this.viewer;
      if (session != null) {
         this.stopViewer(session, "Disconnected");
      }
   }

   public void close() {
      this.shuttingDown = true;
      this.disconnect();
      PingBypass.Worker session = this.worker;
      if (session != null) {
         this.stopWorker(session, "Client stopped");
      }

      if (this.workerListener != null) {
         try {
            this.workerListener.close();
         } catch (IOException var3) {
         }
      }
   }

   private void receiveViewer(PingBypass.Viewer session, RelayWire.Frame frame) {
      if (this.viewer == session && !session.closed.get()) {
         if (frame.type() == 2) {
            if (!session.started) {
               throw new IllegalStateException("Game data before session start.");
            }

            session.bridge.receive(frame.payload());
         } else {
            if (frame.type() != 1) {
               throw new IllegalArgumentException("Unexpected worker frame.");
            }

            JsonObject json = parse(frame);
            String var4 = json.get("op").getAsString();
            switch (var4) {
               case "ready":
                  this.onMain(session, frame.payload().length, () -> {
                     if (session.ready) {
                        throw new IllegalStateException("Duplicate session greeting.");
                     } else {
                        session.ready = true;
                        PingBypassModules.enterViewer(snapshot -> sendModules(session.wire, snapshot));
                        PingBypassInput.attachViewer(input -> {
                           JsonObject update = message("input");
                           update.add("value", input);
                           session.wire.json(update.toString());
                        });
                        this.sendRelations(session);
                        String local = "127.0.0.1:" + session.bridge.port();
                        this.status = "Connecting through " + session.config.vpsHost() + "\u2026";
                        ServerData viewerServer = new ServerData(session.config.serverAddress() + " \u00b7 PingBypass", local, Type.OTHER);
                        PingBypassProtocol.configureViewer(viewerServer);
                        ConnectScreen.startConnecting(new TitleScreen(), Minecraft.getInstance(), ServerAddress.parseString(local), viewerServer, false, null);
                     }
                  });
                  break;
               case "auth":
                  this.authorize(session, json);
                  break;
               case "compression":
                  session.bridge.enableCompression(encode(LoginProtocols.CLIENTBOUND, new ClientboundLoginCompressionPacket(256)));
                  break;
               case "modules":
                  this.onMain(session, frame.payload().length, () -> PingBypassModules.apply(json.getAsJsonObject("value")));
                  break;
               case "runtime":
                  this.onMain(session, frame.payload().length, () -> PingBypassModules.applyRuntime(json.getAsJsonObject("value")));
                  break;
               case "render":
                  this.queueRender(session, json.getAsJsonObject("value"));
                  break;
               case "relations":
                  this.onMain(session, frame.payload().length, () -> {
                     this.sendRelations(session);
                     if (json.get("revision").getAsLong() == session.relationRevision) {
                        applyRelations(json);
                        this.lastRelations = relations().toString();
                     }
                  });
                  break;
               case "error":
                  this.stopViewer(session, json.get("message").getAsString());
                  break;
               default:
                  throw new IllegalArgumentException("Unexpected worker message.");
            }
         }
      }
   }

   private void authorize(PingBypass.Viewer session, JsonObject request) {
      String digest = request.get("hash").getAsString();
      if (session.started && session.authRequested.compareAndSet(false, true) && digest.matches("-?[0-9a-f]{1,40}")) {
         Thread.ofVirtual()
            .name("PingBypass-account-join")
            .start(
               () -> {
                  JsonObject answer = message("auth-result");
                  boolean success = false;

                  try {
                     Minecraft mc = Minecraft.getInstance();
                     if (this.viewer != session || session.closed.get() || !mc.getUser().getProfileId().equals(session.profile)) {
                        return;
                     }

                     new YggdrasilAuthenticationService(new Proxy(java.net.Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", session.tunnel.socksPort())))
                        .createMinecraftSessionService()
                        .joinServer(session.profile, mc.getUser().getAccessToken(), digest);
                     success = true;
                  } catch (Exception var6) {
                     this.status = "Minecraft login authorization failed. Refresh your local launcher session.";
                  }

                  answer.addProperty("success", success);
                  if (this.viewer == session && !session.closed.get()) {
                     session.wire.json(answer.toString());
                  }
               }
            );
      } else {
         throw new IllegalArgumentException("Unexpected login authorization request.");
      }
   }

   private void listen() {
      try {
         String secretFile = System.getProperty("ketamine.pingbypass.secretFile");
         String secret = secretFile == null ? System.getProperty("ketamine.pingbypass.secret", "") : Files.readString(Path.of(secretFile)).strip();
         if (!secret.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A private 256-bit relay secret is required.");
         }

         ServerSocket listener = new ServerSocket();
         listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), Integer.getInteger("ketamine.pingbypass.port", 25570)), 1);
         this.workerListener = listener;
         KetamineClub.LOGGER.info("KETAMINE_PINGBYPASS_READY");

         while (!listener.isClosed()) {
            Socket socket = listener.accept();
            if (this.worker != null) {
               socket.close();
            } else {
               PingBypass.Worker session = new PingBypass.Worker(secret);
               this.worker = session;
               session.wire = new RelayWire(socket, frame -> this.receiveWorker(session, frame), reason -> this.stopWorker(session, reason));
               session.wire.start();
               CompletableFuture.delayedExecutor(10L, TimeUnit.SECONDS).execute(() -> {
                  if (!session.authenticated) {
                     this.stopWorker(session, "Relay handshake timed out.");
                  }
               });
            }
         }
      } catch (Exception var6) {
         KetamineClub.LOGGER.error("PingBypass listener failed: {}", safeMessage(var6));
      }
   }

   private void receiveWorker(PingBypass.Worker session, RelayWire.Frame frame) {
      if (!session.closed.get() && this.worker == session) {
         if (frame.type() == 1) {
            JsonObject json = parse(frame);
            String op = json.get("op").getAsString();
            if (!session.authenticated) {
               if (op.equals("hello")
                  && json.get("version").getAsInt() == 1
                  && SharedConstants.getCurrentVersion().name().equals(json.get("game").getAsString())
                  && MessageDigest.isEqual(session.secret.getBytes(StandardCharsets.UTF_8), json.get("secret").getAsString().getBytes(StandardCharsets.UTF_8))) {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.getUser().getProfileId().toString().equals(json.get("profile").getAsString())
                     && mc.getUser().getName().equals(json.get("name").getAsString())) {
                     session.server = json.get("server").getAsString();
                     session.targetProtocol = json.has("targetProtocol")
                        ? PingBypassProtocol.Selection.fromJson(json.getAsJsonObject("targetProtocol"))
                        : PingBypassProtocol.Selection.nativeVersion();
                     PingBypassProtocol.validateWorker(session.targetProtocol);
                     if (ServerAddress.isValidAddress(session.server) && session.server.length() <= 255) {
                        session.authenticated = true;
                        this.onMain(session, frame.payload().length, () -> {
                           PingBypassInput.resetWorker();
                           PingBypassModules.attachWorker(snapshot -> sendModules(session.wire, snapshot));
                           session.wire.json(message("ready").toString());
                        });
                        return;
                     }

                     throw new IllegalArgumentException("Invalid server.");
                  }

                  throw new IllegalArgumentException("Worker profile mismatch.");
               }

               throw new IllegalArgumentException("Relay authentication/version mismatch.");
            }

            switch (op) {
               case "start":
                  this.onMain(
                     session,
                     frame.payload().length,
                     () -> {
                        if (session.started) {
                           throw new IllegalArgumentException("Session already started.");
                        } else {
                           session.started = true;
                           Minecraft mcx = Minecraft.getInstance();
                           session.controller = new RemotePlayerController(
                              mcx, packet -> this.sendUpstream(session, packet), packet -> this.sendViewer(session, packet)
                           );
                           session.controller
                              .setMotionSender(
                                 state -> this.sendViewer(session, new ClientboundCustomPayloadPacket(new PingBypassMotionPayload(state.toString())))
                              );
                           session.controller.setRemotePhysicsRequired(session.targetProtocol.requiresTranslation());
                           ServerData upstream = new ServerData("PingBypass", session.server, Type.OTHER);
                           PingBypassProtocol.configureWorker(upstream, session.targetProtocol);
                           ConnectScreen.startConnecting(new TitleScreen(), mcx, ServerAddress.parseString(session.server), upstream, false, null);
                        }
                     }
                  );
                  break;
               case "auth-result":
                  if (!session.authRequested.get() || !session.auth.complete(json.get("success").getAsBoolean())) {
                     throw new IllegalArgumentException("Unexpected authorization response.");
                  }
                  break;
               case "motion-ack":
                  this.onMain(session, frame.payload().length, () -> {
                     if (session.controller != null) {
                        session.controller.acknowledgeMotion(json.get("generation").getAsInt());
                     }
                  });
                  break;
               case "input":
                  this.onMain(session, frame.payload().length, () -> PingBypassInput.apply(json.getAsJsonObject("value")));
                  break;
               case "buttons":
                  this.onMain(session, frame.payload().length, () -> {
                     if (session.controller != null) {
                        session.controller.setButtons(json.get("attack").getAsBoolean(), json.get("use").getAsBoolean());
                     }
                  });
                  break;
               case "modules":
                  this.onMain(session, frame.payload().length, () -> stageModules(session, json.getAsJsonObject("value")));
                  break;
               case "relations":
                  this.onMain(session, frame.payload().length, () -> {
                     long revision = json.get("revision").getAsLong();
                     if (revision > session.relationRevision) {
                        session.relationRevision = revision;
                        applyRelations(json);
                        session.lastRelations = relations().toString();
                     }
                  });
                  break;
               default:
                  throw new IllegalArgumentException("Unexpected viewer message.");
            }
         } else {
            if (frame.type() != 3 || !session.authenticated || !session.started) {
               throw new IllegalArgumentException("Unauthenticated or unexpected viewer frame.");
            }

            this.onMain(session, frame.payload().length, () -> {
               ProtocolInfo<?> protocol = session.outbound;
               if (protocol != null && phase(protocol) == frame.phase()) {
                  if (protocol.id() == ConnectionProtocol.PLAY || protocol.id() == ConnectionProtocol.CONFIGURATION) {
                     ByteBuf buffer = Unpooled.wrappedBuffer(frame.payload());

                     try {
                        Packet<?> packet = (Packet<?>)protocol.codec().decode(buffer);
                        if (buffer.isReadable()) {
                           throw new IllegalArgumentException("Trailing packet data.");
                        }

                        if (protocol.id() == ConnectionProtocol.PLAY) {
                           session.controller.accept(packet);
                        } else if (packet instanceof ServerboundResourcePackPacket) {
                           this.sendUpstream(session, packet);
                        }
                     } finally {
                        buffer.release();
                     }
                  }
               }
            });
         }
      }
   }

   public Component authenticateWorker(Connection connection, String hash) {
      PingBypass.Worker session = this.worker;
      if (session != null && session.connection == connection && session.authenticated && session.authRequested.compareAndSet(false, true)) {
         JsonObject request = message("auth");
         request.addProperty("hash", hash);
         session.wire.json(request.toString());

         try {
            return session.auth.get(30L, TimeUnit.SECONDS) ? null : Component.literal("Local Minecraft session authorization failed.");
         } catch (Exception var6) {
            return Component.literal("Local Minecraft session authorization timed out or disconnected.");
         }
      } else {
         return Component.literal("PingBypass login has no authorized controller.");
      }
   }

   public void protocol(Connection connection, ProtocolInfo<?> protocol, boolean inbound) {
      PingBypass.Worker remote = this.worker;
      if (WORKER && remote != null && remote.started) {
         if (remote.connection == null && inbound && protocol.id() == ConnectionProtocol.LOGIN) {
            remote.connection = connection;
         }

         if (remote.connection == connection) {
            if (inbound) {
               remote.inbound = protocol;
            } else {
               remote.outbound = protocol;
            }
         }
      }

      PingBypass.Viewer local = this.viewer;
      if (!WORKER
         && local != null
         && local.bridge != null
         && connection.getRemoteAddress() instanceof InetSocketAddress address
         && address.getAddress().isLoopbackAddress()
         && address.getPort() == local.bridge.port()) {
         local.connection = connection;
      }
   }

   public boolean mirrors(ChannelHandlerContext context) {
      PingBypass.Worker session = this.worker;
      return session != null && !session.closed.get() && !session.failing.get() && session.connection == connection(context) && session.started;
   }

   public void received(ChannelHandlerContext context, ProtocolInfo<?> protocol, Packet<?> packet, byte[] bytes) {
      PingBypass.Worker session = this.worker;
      if (session != null && session.connection == connection(context) && !session.closed.get()) {
         if (!(packet instanceof ClientboundHelloPacket) && !(packet instanceof ClientboundLoginCompressionPacket)) {
            if (packet instanceof ClientboundLoginFinishedPacket) {
               session.wire.json(message("compression").toString());
            }

            if (protocol.id() == ConnectionProtocol.PLAY || !PingBypassProtocol.isInternalPayload(packet)) {
               if (!(packet instanceof ClientboundCustomPayloadPacket payload && payload.payload().type().id().equals(PingBypassMotionPayload.TYPE.id()))) {
                  if (packet instanceof ClientboundTransferPacket) {
                     this.failWorker(session, "This server requested a transfer. Reconnect through PingBypass to the destination server.");
                  } else if (protocol.id() != ConnectionProtocol.PLAY) {
                     session.wire.send(new RelayWire.Frame(2, phase(protocol), bytes));
                  } else if (packet instanceof ClientboundBundleDelimiterPacket) {
                     session.bundleDelimiter = bytes;
                  } else {
                     synchronized (session.captured) {
                        if (bytes.length > 33554432 - session.capturedBytes) {
                           this.failWorker(session, "Worker packet processing cannot keep up with the server.");
                        } else {
                           session.capturedBytes += bytes.length;
                           session.captured.computeIfAbsent(packet, ignored -> new ArrayDeque<>()).addLast(new PingBypass.Captured(protocol, bytes));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean handleWorkerPacket(Connection connection, Packet<?> packet) {
      PingBypass.Worker session = this.worker;
      if (session == null || session.connection != connection || session.inbound == null || session.inbound.id() != ConnectionProtocol.PLAY) {
         return false;
      } else if (!session.closed.get() && !session.failing.get()) {
         PacketListener listener = connection.getPacketListener();
         boolean schedule = false;
         boolean overflow = false;
         synchronized (session.pendingPackets) {
            if (session.closed.get() || session.failing.get()) {
               return true;
            }

            if (session.pendingPackets.size() >= 65536) {
               overflow = true;
            } else {
               session.pendingPackets.addLast(new PingBypass.PendingWorkerPacket(listener, packet));
               if (!session.packetDispatch) {
                  session.packetDispatch = true;
                  schedule = true;
               }
            }
         }

         if (overflow) {
            this.failWorker(session, "Worker packet processing cannot keep up with the server.");
         } else if (schedule) {
            Minecraft.getInstance().execute(() -> this.drainWorkerPackets(session));
         }

         return true;
      } else {
         return true;
      }
   }

   private void drainWorkerPackets(PingBypass.Worker session) {
      while (!session.closed.get() && !session.failing.get()) {
         PingBypass.PendingWorkerPacket pending;
         synchronized (session.pendingPackets) {
            pending = session.pendingPackets.pollFirst();
            if (pending == null) {
               session.packetDispatch = false;
               return;
            }
         }

         try {
            Packet<?> packet = pending.packet();
            if (!(packet instanceof ClientboundBundlePacket bundle)) {
               this.applyWorkerPacket(session, pending.listener(), packet);
            } else {
               ProtocolInfo<?> protocol = session.inbound;
               byte[] delimiter = session.bundleDelimiter;
               if (delimiter == null) {
                  throw new IllegalStateException("Bundle without its protocol delimiter.");
               }

               session.wire.send(new RelayWire.Frame(2, phase(protocol), delimiter));

               for (Packet<?> child : bundle.subPackets()) {
                  this.applyWorkerPacket(session, pending.listener(), child);
               }

               session.wire.send(new RelayWire.Frame(2, phase(protocol), delimiter));
            }
         } catch (Exception var11) {
            this.failWorker(session, "PingBypass packet error: " + safeMessage(var11));
            break;
         }
      }

      synchronized (session.pendingPackets) {
         session.pendingPackets.clear();
         session.packetDispatch = false;
      }
   }

   private void applyWorkerPacket(PingBypass.Worker session, PacketListener listener, Packet<?> packet) {
      PingBypass.Captured captured;
      synchronized (session.captured) {
         ArrayDeque<PingBypass.Captured> copies = session.captured.get(packet);
         captured = copies == null ? null : copies.pollFirst();
         if (copies != null && copies.isEmpty()) {
            session.captured.remove(packet);
         }

         if (captured != null) {
            session.capturedBytes = session.capturedBytes - captured.bytes.length;
         }
      }

      if (captured != null && listener.shouldHandleMessage(packet)) {
         if (!Util.EVENT_BUS.post(new PacketEvent.Receive(packet))) {
            if (packet.isTerminal()) {
               this.mirrorProcessed(session, captured, packet);
            }

            ((Packet<PacketListener>)(Packet<?>)packet).handle(listener);
            if (packet instanceof ClientboundPlayerPositionPacket teleport && session.controller != null) {
               session.controller.onTeleport(teleport.id());
            } else if (packet instanceof ClientboundRespawnPacket && session.controller != null) {
               session.controller.onRespawn();
            }

            if (!packet.isTerminal()) {
               this.mirrorProcessed(session, captured, packet);
            }

            Util.EVENT_BUS.post(new PacketEvent.ReceivePost(packet));
         }
      }
   }

   private void mirrorProcessed(PingBypass.Worker session, PingBypass.Captured captured, Packet<?> packet) {
      if (!PingBypassProtocol.isInternalPayload(packet)) {
         Minecraft mc = Minecraft.getInstance();
         Packet<?> result = packet;
         if (packet instanceof ClientboundBlockChangedAckPacket ack && session.controller != null) {
            int sequence = session.controller.acknowledge(ack.sequence());
            if (sequence < 0) {
               return;
            }

            result = new ClientboundBlockChangedAckPacket(sequence);
         } else if (mc.player != null && packet instanceof ClientboundPlayerPositionPacket teleport) {
            result = new ClientboundPlayerPositionPacket(
               teleport.id(), new PositionMoveRotation(mc.player.position(), mc.player.getDeltaMovement(), mc.player.getYRot(), mc.player.getXRot()), Set.of()
            );
         } else if (mc.player != null && packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId()) {
            result = new ClientboundSetEntityMotionPacket(mc.player);
         } else if (packet instanceof ClientboundSetHeldSlotPacket slot && session.controller != null) {
            session.controller.serverHeldSlot(slot.slot());
         } else if (mc.player != null && packet instanceof ClientboundExplodePacket explosion) {
            result = new ClientboundExplodePacket(
               explosion.center(),
               explosion.radius(),
               explosion.blockCount(),
               Optional.empty(),
               explosion.explosionParticle(),
               explosion.explosionSound(),
               explosion.blockParticles()
            );
         }

         byte[] data = result == packet ? captured.bytes : encode(captured.protocol, result);
         session.wire.send(new RelayWire.Frame(2, phase(captured.protocol), data));
         if ((packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) && PingBypassProtocol.needsSyntheticLoadingStart()) {
            session.wire
               .send(
                  new RelayWire.Frame(
                     2,
                     phase(captured.protocol),
                     encode(captured.protocol, new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F))
                  )
               );
         }

         if (mc.player != null && packet instanceof ClientboundExplodePacket) {
            session.wire.send(new RelayWire.Frame(2, phase(captured.protocol), encode(captured.protocol, new ClientboundSetEntityMotionPacket(mc.player))));
         }
      }
   }

   public void encoded(ChannelHandlerContext context, ProtocolInfo<?> protocol, Packet<?> packet, ByteBuf bytes) {
      PingBypass.Viewer session = this.viewer;
      if (session != null && session.connection == connection(context) && !session.closed.get()) {
         if (packet instanceof ServerboundHelloPacket) {
            if (!session.started) {
               session.started = true;
               session.wire.json(message("start").toString());
            }
         } else if (protocol.id() == ConnectionProtocol.PLAY || protocol.id() == ConnectionProtocol.CONFIGURATION) {
            byte[] copy = new byte[bytes.readableBytes()];
            bytes.getBytes(bytes.readerIndex(), copy);
            session.wire.send(new RelayWire.Frame(3, phase(protocol), copy));
         }
      }
   }

   public boolean suppressWorkerPacket(Connection connection, Packet<?> packet) {
      PingBypass.Worker session = this.worker;
      return session != null
         && session.connection == connection
         && session.controller != null
         && !session.forwarding
         && (packet instanceof ServerboundResourcePackPacket || session.controller.suppressWorkerPacket(packet));
   }

   private void sendUpstream(PingBypass.Worker session, Packet<?> packet) {
      if (session.connection != null && session.connection.isConnected()) {
         session.forwarding = true;

         try {
            session.connection.send(packet);
         } finally {
            session.forwarding = false;
         }
      }
   }

   private void sendViewer(PingBypass.Worker session, Packet<?> packet) {
      ProtocolInfo<?> protocol = session.inbound;
      if (protocol != null && protocol.id() == ConnectionProtocol.PLAY && !session.closed.get()) {
         session.wire.send(new RelayWire.Frame(2, phase(protocol), encode(protocol, packet)));
      }
   }

   private static byte[] encode(ProtocolInfo protocol, Packet<?> packet) {
      ByteBuf buffer = Unpooled.buffer();

      byte[] var4;
      try {
         protocol.codec().encode(buffer, packet);
         byte[] bytes = new byte[buffer.readableBytes()];
         buffer.readBytes(bytes);
         var4 = bytes;
      } finally {
         buffer.release();
      }

      return var4;
   }

   public void beforePlayerTick() {
      PingBypass.Worker s = this.worker;
      if (s != null && s.controller != null) {
         ClientPacketListener clientConnection = Minecraft.getInstance().getConnection();
         if (!s.pendingModules.getAsJsonObject("modules").isEmpty()
            && s.inbound != null
            && s.inbound.id() == ConnectionProtocol.PLAY
            && clientConnection != null
            && clientConnection.hasClientLoaded()) {
            JsonObject pending = s.pendingModules;
            s.pendingModules = emptyModules();

            try {
               PingBypassModules.apply(pending);
            } catch (RuntimeException var5) {
               this.failWorker(s, "Invalid module controls: " + safeMessage(var5));
               return;
            }
         }

         s.controller.beforeTick();
      }
   }

   private static JsonObject emptyModules() {
      JsonObject state = new JsonObject();
      state.add("modules", new JsonObject());
      state.add("revisions", new JsonObject());
      return state;
   }

   private static void stageModules(PingBypass.Worker session, JsonObject update) {
      JsonObject modules = update.getAsJsonObject("modules");
      JsonObject revisions = update.getAsJsonObject("revisions");
      if (modules != null && revisions != null) {
         for (Entry<String, JsonElement> entry : modules.entrySet()) {
            if (!revisions.has(entry.getKey())) {
               throw new IllegalArgumentException("Missing module revision.");
            }

            long incoming = revisions.get(entry.getKey()).getAsLong();
            if (incoming < 0L) {
               throw new IllegalArgumentException("Invalid module revision.");
            }

            JsonElement previous = session.pendingModules.getAsJsonObject("revisions").get(entry.getKey());
            if (previous == null || incoming > previous.getAsLong()) {
               session.pendingModules.getAsJsonObject("modules").add(entry.getKey(), entry.getValue());
               session.pendingModules.getAsJsonObject("revisions").addProperty(entry.getKey(), incoming);
            }
         }
      } else {
         throw new IllegalArgumentException("Missing module snapshot/revisions.");
      }
   }

   public void afterMovementTick() {
      PingBypass.Worker s = this.worker;
      if (s != null && s.controller != null) {
         s.controller.afterMovementTick();
      }
   }

   public void afterPlayerTick() {
      PingBypass.Worker s = this.worker;
      if (s != null && s.controller != null) {
         s.controller.afterTick();
      }
   }

   public void teleported(int id) {
      PingBypass.Worker s = this.worker;
      if (s != null && s.controller != null) {
         s.controller.onTeleport(id);
      }
   }

   public boolean workerMessage(Component message) {
      PingBypass.Worker s = this.worker;
      if (s != null && s.started && !s.closed.get()) {
         this.sendViewer(s, new ClientboundSystemChatPacket(message, false));
         return true;
      } else {
         return false;
      }
   }

   public boolean suppressVanillaMovement() {
      PingBypass.Worker s = this.worker;
      return s != null && s.controller != null && s.controller.suppressVanillaMovement();
   }

   public boolean beforeVanillaMovement() {
      PingBypass.Worker s = this.worker;
      return s != null && s.controller != null && s.controller.beforeVanillaMovement();
   }

   private void applyMotion(JsonObject state) {
      PingBypass.Viewer session = this.viewer;
      Minecraft mc = Minecraft.getInstance();
      if (session != null && !session.closed.get() && mc.player != null && mc.level != null) {
         boolean authoritative = state.get("authoritative").getAsBoolean();
         Vec3 position = vector(state, "x", "y", "z");
         Vec3 velocity = vector(state, "dx", "dy", "dz");
         Entity mount = null;
         Vec3 mountPosition = null;
         if (state.has("vehicle")) {
            JsonObject vehicle = state.getAsJsonObject("vehicle");
            Entity entity = mc.level.getEntity(vehicle.get("id").getAsInt());
            if (entity != null && entity == mc.player.getRootVehicle() && entity != mc.player) {
               mount = entity;
               mountPosition = vector(vehicle, "x", "y", "z");
            }
         }

         if (!authoritative || !session.authoritativeMotion) {
            PingBypassMotionSmoothing.reset();
         }

         if (authoritative) {
            PingBypassMotionSmoothing.accept(mc.player, position, mount, mountPosition);
         }

         session.authoritativeMotion = authoritative;
         mc.player.setPos(position);
         mc.player.setDeltaMovement(velocity);
         mc.player.setOnGround(state.get("onGround").getAsBoolean());
         mc.player.horizontalCollision = state.get("horizontalCollision").getAsBoolean();
         mc.player.setSprinting(state.get("sprinting").getAsBoolean());
         mc.player.getAbilities().flying = state.get("flying").getAsBoolean();
         if (state.get("fallFlying").getAsBoolean()) {
            mc.player.startFallFlying();
         } else {
            mc.player.stopFallFlying();
         }

         if (mount != null) {
            JsonObject vehicle = state.getAsJsonObject("vehicle");
            mount.setPos(mountPosition);
            mount.setDeltaMovement(vector(vehicle, "dx", "dy", "dz"));
            mount.setYRot(vehicle.get("yaw").getAsFloat());
            mount.setXRot(vehicle.get("pitch").getAsFloat());
         }

         if (!authoritative) {
            JsonObject ack = message("motion-ack");
            ack.addProperty("generation", state.get("generation").getAsInt());
            session.wire.json(ack.toString());
         }
      }
   }

   private static Vec3 vector(JsonObject state, String x, String y, String z) {
      double dx = state.get(x).getAsDouble();
      double dy = state.get(y).getAsDouble();
      double dz = state.get(z).getAsDouble();
      if (Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)) {
         return new Vec3(dx, dy, dz);
      } else {
         throw new IllegalArgumentException("Invalid movement state.");
      }
   }

   private void tick() {
      PingBypass.Viewer local = this.viewer;
      if (local != null && local.ready) {
         PingBypassInput.tickViewer();
      }

      if (local != null && local.started) {
         Minecraft mc = Minecraft.getInstance();
         boolean attack = mc.gui.screen() == null && mc.options.keyAttack.isDown();
         boolean use = mc.gui.screen() == null && mc.options.keyUse.isDown();
         if (local.attack != attack || local.use != use) {
            local.attack = attack;
            local.use = use;
            JsonObject buttons = message("buttons");
            buttons.addProperty("attack", attack);
            buttons.addProperty("use", use);
            local.wire.json(buttons.toString());
         }
      }

      if (local != null && local.started && ++this.ticks % 20 == 0) {
         this.sendRelations(local);
         if (Minecraft.getInstance().player != null) {
            this.status = "Connected \u00b7 " + local.config.serverAddress() + " via " + local.config.vpsHost();
         }
      }

      PingBypass.Worker remote = this.worker;
      if (remote != null && remote.connection != null) {
         if (!remote.connection.isConnected()) {
            if (++remote.disconnectTicks > 100) {
               this.failWorker(remote, "The Minecraft server disconnected the VPS client.");
            }
         } else {
            remote.disconnectTicks = 0;
            if (Minecraft.getInstance().player != null && ++remote.ticks % 10 == 0) {
               JsonObject state = message("runtime");
               state.add("value", PingBypassModules.snapshotRuntime());
               remote.wire.json(state.toString());
            }

            JsonObject render = message("render");
            render.add("value", PingBypassRenderState.workerSnapshot(System.currentTimeMillis()));
            remote.wire.json(render.toString());
         }

         if (remote.relationRevision > 0L) {
            JsonObject relations = relations();
            String value = relations.toString();
            if (!value.equals(remote.lastRelations)) {
               remote.lastRelations = value;
               relations.addProperty("revision", remote.relationRevision);
               remote.wire.json(relations.toString());
            }
         }
      }
   }

   private void sendRelations(PingBypass.Viewer session) {
      JsonObject json = relations();
      String value = json.toString();
      if (!value.equals(this.lastRelations)) {
         this.lastRelations = value;
         json.addProperty("revision", ++session.relationRevision);
         session.wire.json(json.toString());
      }
   }

   private void queueRender(PingBypass.Viewer session, JsonObject snapshot) {
      session.pendingRender.set(snapshot);
      if (session.renderDispatch.compareAndSet(false, true)) {
         Minecraft.getInstance().execute(() -> {
            try {
               do {
                  JsonObject latest = session.pendingRender.getAndSet(null);
                  if (latest != null && !session.closed.get()) {
                     PingBypassRenderState.apply(latest);
                  }

                  session.renderDispatch.set(false);
               } while (session.pendingRender.get() != null && !session.closed.get() && session.renderDispatch.compareAndSet(false, true));
            } catch (Exception var3) {
               session.renderDispatch.set(false);
               this.stopViewer(session, "PingBypass render update failed: " + safeMessage(var3));
            }
         });
      }
   }

   private static JsonObject relations() {
      JsonObject json = message("relations");
      json.add("friends", KetamineClub.friendManager.toJson());
      json.add("enemies", KetamineClub.enemyManager.toJson());
      return json;
   }

   private static void applyRelations(JsonObject json) {
      KetamineClub.friendManager.clearFriends();
      KetamineClub.enemyManager.clearEnemies();
      KetamineClub.friendManager.fromJson(json.get("friends"));
      KetamineClub.enemyManager.fromJson(json.get("enemies"));
   }

   private void stopViewer(PingBypass.Viewer session, String reason) {
      if (session.closed.compareAndSet(false, true)) {
         this.closeViewerResources(session);
         if (session.deployment != null && session.deployment != Thread.currentThread()) {
            session.deployment.interrupt();
         }

         Thread.ofVirtual().name("PingBypass-SSH-cleanup").start(() -> {
            if (session.tunnel != null) {
               session.tunnel.close();
            }
         });
         Minecraft.getInstance()
            .execute(
               () -> {
                  if (this.viewer == session) {
                     if (session.connection != null) {
                        session.connection.disconnect(Component.literal(reason));
                     }

                     if (session.connection != null
                        && Minecraft.getInstance().getConnection() != null
                        && Minecraft.getInstance().getConnection().getConnection() == session.connection) {
                        Minecraft.getInstance().disconnectWithProgressScreen(false);
                     }

                     PingBypassModules.leaveViewer();
                     PingBypassRenderState.reset();
                     PingBypassMotionSmoothing.reset();
                     PingBypassInput.detachViewer();
                     this.viewer = null;
                     session.authoritativeMotion = false;
                     this.lastRelations = "";
                     this.status = reason;
                     if (session.ready && !this.shuttingDown) {
                        Minecraft.getInstance().gui.setScreen(new PingBypassScreen(new TitleScreen()));
                     }
                  }
               }
            );
      }
   }

   private void failWorker(PingBypass.Worker session, String reason) {
      if (!session.closed.get() && session.failing.compareAndSet(false, true)) {
         JsonObject error = message("error");
         error.addProperty("message", reason);
         session.wire.json(error.toString());
         CompletableFuture.delayedExecutor(1L, TimeUnit.SECONDS).execute(() -> this.stopWorker(session, reason));
      }
   }

   private void stopWorker(PingBypass.Worker session, String reason) {
      if (session.closed.compareAndSet(false, true)) {
         session.auth.complete(false);
         if (session.wire != null) {
            session.wire.close();
         }

         Minecraft.getInstance().execute(() -> {
            if (this.worker == session) {
               PingBypassModules.detachWorker();
               PingBypassRenderState.reset();
               PingBypassInput.resetWorker();
               if (session.connection != null) {
                  session.connection.disconnect(Component.literal(reason));
               }

               KetamineClub.moduleManager.getModules().forEach(module -> {
                  if (module.isToggled()) {
                     module.disable();
                  }
               });
               if (session.controller != null) {
                  session.controller.reset();
               }

               synchronized (session.captured) {
                  session.captured.clear();
                  session.capturedBytes = 0;
               }

               synchronized (session.pendingPackets) {
                  session.pendingPackets.clear();
                  session.packetDispatch = false;
               }

               this.worker = null;
            }
         });
      }
   }

   private void closeViewerResources(PingBypass.Viewer session) {
      if (session.wire != null) {
         session.wire.close();
      }

      if (session.bridge != null) {
         session.bridge.close();
      }

      if (session.connectingSocket != null) {
         try {
            session.connectingSocket.close();
         } catch (IOException var3) {
         }
      }

      if (session.tunnel != null) {
         session.tunnel.close();
      }
   }

   private void onMain(PingBypass.Session session, Runnable action) {
      this.onMain(session, 0, action);
   }

   private void onMain(PingBypass.Session session, int bytes, Runnable action) {
      int pending = session.pending.incrementAndGet();
      int pendingBytes = session.pendingBytes.addAndGet(bytes);
      if (pending <= 4096 && pendingBytes <= 33554432) {
         Minecraft.getInstance().execute(() -> {
            try {
               if (!session.closed.get()) {
                  action.run();
               }
            } catch (Exception var9) {
               if (session instanceof PingBypass.Worker remote) {
                  this.failWorker(remote, "PingBypass control error: " + safeMessage(var9));
               } else {
                  this.stopViewer((PingBypass.Viewer)session, "PingBypass control error: " + safeMessage(var9));
               }
            } finally {
               session.pending.decrementAndGet();
               session.pendingBytes.addAndGet(-bytes);
            }
         });
      } else {
         session.pending.decrementAndGet();
         session.pendingBytes.addAndGet(-bytes);
         throw new IllegalArgumentException("Too many pending control messages.");
      }
   }

   private static Connection connection(ChannelHandlerContext context) {
      return context.pipeline().get("packet_handler") instanceof Connection c ? c : null;
   }

   private static int phase(ProtocolInfo<?> protocol) {
      return protocol.id().ordinal();
   }

   private static JsonObject parse(RelayWire.Frame frame) {
      return JsonParser.parseString(new String(frame.payload(), StandardCharsets.UTF_8)).getAsJsonObject();
   }

   private static JsonObject message(String operation) {
      JsonObject json = new JsonObject();
      json.addProperty("op", operation);
      return json;
   }

   private static void sendModules(RelayWire wire, JsonObject snapshot) {
      JsonObject json = message("modules");
      json.add("value", snapshot);
      wire.json(json.toString());
   }

   private static String safeMessage(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static Path artifact(String id) throws IOException {
      ModContainer container = (ModContainer)FabricLoader.getInstance().getModContainer(id).orElseThrow(() -> new IOException("Missing " + id + " mod."));

      for (Path path : container.getOrigin().getPaths()) {
         if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
            return path;
         }
      }

      throw new IOException("PingBypass deployment requires an installed release JAR of " + id + ". Build and launch the packaged mod first.");
   }

   private record Captured(ProtocolInfo<?> protocol, byte[] bytes) {
   }

   private record PendingWorkerPacket(PacketListener listener, Packet<?> packet) {
   }

   private abstract static class Session {
      final AtomicBoolean closed = new AtomicBoolean();
      final AtomicBoolean authRequested = new AtomicBoolean();
      final AtomicInteger pending = new AtomicInteger();
      final AtomicInteger pendingBytes = new AtomicInteger();
      volatile RelayWire wire;
      volatile Connection connection;
      volatile boolean started;

      private Session() {
      }
   }

   private static final class Viewer extends PingBypass.Session {
      final PingBypassConfig config;
      final String name;
      final UUID profile;
      final String secret;
      final PingBypassProtocol.Selection targetProtocol;
      volatile Thread deployment;
      volatile SshDeployment.Tunnel tunnel;
      volatile ViewerSocket bridge;
      volatile Socket connectingSocket;
      boolean attack;
      boolean use;
      volatile boolean authoritativeMotion;
      final AtomicReference<JsonObject> pendingRender = new AtomicReference<>();
      final AtomicBoolean renderDispatch = new AtomicBoolean();
      long relationRevision;
      boolean ready;

      Viewer(PingBypassConfig config, String name, UUID profile) {
         this.config = config;
         this.name = name;
         this.profile = profile;
         this.targetProtocol = PingBypassProtocol.captureSelected();
         byte[] key = new byte[32];
         new SecureRandom().nextBytes(key);
         this.secret = HexFormat.of().formatHex(key);
      }
   }

   private static final class Worker extends PingBypass.Session {
      final String secret;
      final AtomicBoolean failing = new AtomicBoolean();
      final CompletableFuture<Boolean> auth = new CompletableFuture<>();
      volatile boolean authenticated;
      volatile ProtocolInfo<?> inbound;
      volatile ProtocolInfo<?> outbound;
      String server;
      PingBypassProtocol.Selection targetProtocol;
      RemotePlayerController controller;
      boolean forwarding;
      int ticks;
      int disconnectTicks;
      long relationRevision;
      String lastRelations = "";
      final Map<Packet<?>, ArrayDeque<PingBypass.Captured>> captured = new IdentityHashMap<>();
      int capturedBytes;
      volatile byte[] bundleDelimiter;
      JsonObject pendingModules = PingBypass.emptyModules();
      final ArrayDeque<PingBypass.PendingWorkerPacket> pendingPackets = new ArrayDeque<>();
      boolean packetDispatch;

      Worker(String secret) {
         this.secret = secret;
      }
   }
}
