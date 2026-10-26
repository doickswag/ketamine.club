package we.ketamine.club.pingbypass;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.Deflater;

final class ViewerSocket implements AutoCloseable {
   static final int COMPRESSION_THRESHOLD = 256;
   private static final int MAX_MINECRAFT_FRAME = 2097151;
   private static final int MAX_QUEUED_BYTES = 33554432;
   private static final int MAX_QUEUED_FRAMES = 16384;
   private static final ViewerSocket.QueuedFrame CLOSED = new ViewerSocket.QueuedFrame(new byte[0], false);
   private final ServerSocket listener;
   private volatile Socket client;
   private final ArrayBlockingQueue<ViewerSocket.QueuedFrame> frames = new ArrayBlockingQueue<>(16384);
   private int queuedBytes;
   private boolean compressionScheduled;
   private final AtomicBoolean closed = new AtomicBoolean();
   private final Consumer<String> failure;

   ViewerSocket(Consumer<String> failure) throws IOException {
      this.failure = failure;
      this.listener = new ServerSocket();
      this.listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
      Thread.ofVirtual().name("PingBypass-viewer").start(this::accept);
   }

   int port() {
      return this.listener.getLocalPort();
   }

   void receive(byte[] packet) {
      this.enqueue(new ViewerSocket.QueuedFrame(packet, false));
   }

   synchronized void enableCompression(byte[] setupPacket) {
      if (!this.closed.get()) {
         if (this.compressionScheduled) {
            this.fail("PingBypass received a duplicate compression negotiation.");
         } else {
            this.compressionScheduled = true;
            this.enqueue(new ViewerSocket.QueuedFrame(setupPacket, true));
         }
      }
   }

   private synchronized void enqueue(ViewerSocket.QueuedFrame frame) {
      if (!this.closed.get()) {
         if (frame.packet.length != 0 && frame.packet.length <= 8388608) {
            while (!this.closed.get()) {
               while (!this.closed.get() && (this.queuedBytes > 33554432 - frame.packet.length || this.frames.remainingCapacity() == 0)) {
                  try {
                     this.wait(100L);
                  } catch (InterruptedException var3) {
                  }
               }

               if (this.closed.get()) {
                  return;
               }

               if (this.frames.offer(frame)) {
                  this.queuedBytes = this.queuedBytes + frame.packet.length;
                  return;
               }
            }
         } else {
            this.fail("PingBypass received an invalid local game packet size.");
         }
      }
   }

   private void accept() {
      try {
         Socket accepted = this.listener.accept();
         synchronized (this) {
            if (this.closed.get()) {
               accepted.close();
               return;
            }

            this.client = accepted;
         }

         this.listener.close();
         accepted.setTcpNoDelay(true);
         Thread.ofVirtual().name("PingBypass-viewer-output").start(this::writeLoop);
         InputStream input = accepted.getInputStream();
         byte[] discard = new byte[8192];

         while (input.read(discard) != -1) {
         }

         this.fail("Disconnected from PingBypass.");
      } catch (IOException var5) {
         this.fail("Local viewer connection closed.");
      }
   }

   private void writeLoop() {
      Deflater compressor = new Deflater();

      try {
         OutputStream output = new BufferedOutputStream(this.client.getOutputStream());
         boolean compression = false;

         while (true) {
            ViewerSocket.QueuedFrame frame = this.frames.take();
            synchronized (this) {
               if (this.closed.get()) {
                  return;
               }

               this.queuedBytes = this.queuedBytes - frame.packet.length;
               this.notifyAll();
            }

            writePacket(output, frame.packet, compression, compressor);
            output.flush();
            if (frame.enablesCompression) {
               compression = true;
            }
         }
      } catch (Exception var12) {
         this.fail(var12 instanceof ViewerSocket.PacketSizeException ? var12.getMessage() : "Local viewer disconnected.");
      } finally {
         compressor.end();
      }
   }

   private static void writePacket(OutputStream output, byte[] packet, boolean compression, Deflater compressor) throws IOException {
      if (!compression) {
         checkFrameSize(packet.length);
         writeVarInt(output, packet.length);
         output.write(packet);
      } else {
         ByteArrayOutputStream envelope = new ByteArrayOutputStream(Math.min(packet.length + 5, 8192));
         if (packet.length < 256) {
            writeVarInt(envelope, 0);
            envelope.write(packet);
         } else {
            writeVarInt(envelope, packet.length);
            compressor.reset();
            compressor.setInput(packet);
            compressor.finish();
            byte[] buffer = new byte[8192];

            while (!compressor.finished()) {
               int count = compressor.deflate(buffer);
               if (count == 0) {
                  throw new IOException("Could not compress the local game packet.");
               }

               checkFrameSize(envelope.size() + count);
               envelope.write(buffer, 0, count);
            }
         }

         checkFrameSize(envelope.size());
         writeVarInt(output, envelope.size());
         envelope.writeTo(output);
      }
   }

   private static void checkFrameSize(int size) throws ViewerSocket.PacketSizeException {
      if (size > 2097151) {
         throw new ViewerSocket.PacketSizeException("A server packet exceeds Minecraft's 2 MiB frame limit even after local compression.");
      }
   }

   static void writeVarInt(OutputStream output, int value) throws IOException {
      do {
         int next = value & 127;
         value >>>= 7;
         output.write(value == 0 ? next : next | 128);
      } while (value != 0);
   }

   private void fail(String reason) {
      boolean notify;
      synchronized (this) {
         notify = !this.closed.get();
         this.close();
      }

      if (notify) {
         this.failure.accept(reason);
      }
   }

   @Override
   public synchronized void close() {
      if (this.closed.compareAndSet(false, true)) {
         try {
            this.listener.close();
         } catch (IOException var3) {
         }

         if (this.client != null) {
            try {
               this.client.close();
            } catch (IOException var2) {
            }
         }

         this.frames.clear();
         this.queuedBytes = 0;
         this.frames.offer(CLOSED);
         this.notifyAll();
      }
   }

   private static final class PacketSizeException extends IOException {
      PacketSizeException(String message) {
         super(message);
      }
   }

   private record QueuedFrame(byte[] packet, boolean enablesCompression) {
   }
}
