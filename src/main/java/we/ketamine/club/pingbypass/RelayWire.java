package we.ketamine.club.pingbypass;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RelayWire implements AutoCloseable {
   public static final int VERSION = 1;
   public static final int JSON = 1;
   public static final int CLIENTBOUND = 2;
   public static final int SERVERBOUND = 3;
   public static final int HEARTBEAT = 4;
   public static final int MAX_FRAME = 8388608;
   private static final int MAX_QUEUED_BYTES = 33554432;
   private static final int MAX_QUEUED_FRAMES = 16384;
   private final Socket socket;
   private final ArrayBlockingQueue<RelayWire.Frame> outgoing = new ArrayBlockingQueue<>(16384);
   private final AtomicInteger queuedBytes = new AtomicInteger();
   private final AtomicBoolean closed = new AtomicBoolean();
   private final Consumer<RelayWire.Frame> receiver;
   private final Consumer<String> failure;

   public RelayWire(Socket socket, Consumer<RelayWire.Frame> receiver, Consumer<String> failure) throws IOException {
      this.socket = socket;
      this.receiver = receiver;
      this.failure = failure;
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(25000);
   }

   public void start() {
      Thread.ofVirtual().name("PingBypass-reader").start(this::readLoop);
      Thread.ofVirtual().name("PingBypass-writer").start(this::writeLoop);
   }

   public boolean isOpen() {
      return !this.closed.get();
   }

   public void json(String json) {
      this.send(new RelayWire.Frame(1, 0, json.getBytes(StandardCharsets.UTF_8)));
   }

   public synchronized void send(RelayWire.Frame frame) {
      if (!this.closed.get()) {
         int frameBytes = frame.payload.length + 6;
         if (frame.payload.length > 8388608) {
            this.fail("PingBypass frame exceeds the 8 MiB relay limit.");
         } else {
            while (!this.closed.get()) {
               while (!this.closed.get() && (this.queuedBytes.get() > 33554432 - frameBytes || this.outgoing.remainingCapacity() == 0)) {
                  try {
                     this.wait(100L);
                  } catch (InterruptedException var4) {
                  }
               }

               if (this.closed.get()) {
                  return;
               }

               if (this.outgoing.offer(frame)) {
                  this.queuedBytes.addAndGet(frameBytes);
                  return;
               }
            }
         }
      }
   }

   private void readLoop() {
      try {
         DataInputStream input = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));

         while (!this.closed.get()) {
            RelayWire.Frame frame = readFrame(input);
            if (frame.type != 4) {
               this.receiver.accept(frame);
            }
         }
      } catch (Exception var3) {
         this.fail(var3 instanceof EOFException ? "PingBypass connection closed." : "PingBypass link failed: " + var3.getMessage());
      }
   }

   private void writeLoop() {
      try {
         for (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(this.socket.getOutputStream())); !this.closed.get(); output.flush()) {
            RelayWire.Frame frame = this.outgoing.poll(5L, TimeUnit.SECONDS);
            if (frame == null) {
               frame = new RelayWire.Frame(4, 0, new byte[0]);
            } else {
               synchronized (this) {
                  this.queuedBytes.addAndGet(-frame.payload.length - 6);
                  this.notifyAll();
               }
            }

            writeFrame(output, frame);

            for (int i = 0; i < 63; i++) {
               RelayWire.Frame next = this.outgoing.poll();
               if (next != null) {
                  synchronized (this) {
                     this.queuedBytes.addAndGet(-next.payload.length - 6);
                     this.notifyAll();
                  }

                  writeFrame(output, next);
                  continue;
               }
            }
         }
      } catch (Exception var9) {
         this.fail("PingBypass write failed: " + var9.getMessage());
      }
   }

   public static RelayWire.Frame readFrame(DataInputStream input) throws IOException {
      int length = input.readInt();
      if (length >= 2 && length <= 8388610) {
         int type = input.readUnsignedByte();
         int phase = input.readUnsignedByte();
         if (type < 1 || type > 4 || phase > 4) {
            throw new IOException("Unknown relay message.");
         } else if (type == 1 && length > 1048576) {
            throw new IOException("Control message is too large.");
         } else if (type == 4 && length != 2) {
            throw new IOException("Invalid heartbeat.");
         } else {
            byte[] payload = new byte[length - 2];
            input.readFully(payload);
            return new RelayWire.Frame(type, phase, payload);
         }
      } else {
         throw new IOException("Invalid relay frame length.");
      }
   }

   public static void writeFrame(DataOutputStream output, RelayWire.Frame frame) throws IOException {
      output.writeInt(frame.payload.length + 2);
      output.writeByte(frame.type);
      output.writeByte(frame.phase);
      output.write(frame.payload);
   }

   private void fail(String reason) {
      synchronized (this) {
         if (this.closed.compareAndSet(false, true)) {
            try {
               this.socket.close();
            } catch (IOException var5) {
            }

            this.outgoing.clear();
            this.queuedBytes.set(0);
            this.notifyAll();
            this.failure.accept(reason);
         }
      }
   }

   @Override
   public synchronized void close() {
      if (this.closed.compareAndSet(false, true)) {
         try {
            this.socket.close();
         } catch (IOException var2) {
         }

         this.outgoing.clear();
         this.queuedBytes.set(0);
         this.notifyAll();
      }
   }

   public record Frame(int type, int phase, byte[] payload) {
   }
}
