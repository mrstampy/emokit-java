// Copyright Samuel Halliday 2012
package com.github.fommil.emokit;

import com.github.fommil.emokit.jpa.EmotivDatum;
import com.github.fommil.emokit.jpa.EmotivSession;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import lombok.extern.java.Log;

import javax.annotation.concurrent.NotThreadSafe;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Unencrypted access to an Emotiv EEG.
 * <p/>
 * The device is constantly polled in a background thread,
 * filling up a buffer (which could cause the application
 * to OutOfMemory if not evacuated).
 *
 * @author Sam Halliday
 */
@Log
@NotThreadSafe
public final class Emotiv implements Closeable {

    public static void main(String[] args) throws Exception {
        Emotiv emotiv = new Emotiv();

        final EmotivSession session = new EmotivSession();
        session.setName("My Session");
        session.setNotes("My Notes for " + emotiv.getSerial());

        final Condition condition = new ReentrantLock().newCondition();

        emotiv.addEmotivListener(new EmotivListener() {
            @Override
            public void receivePacket(Packet packet) {
                EmotivDatum datum = EmotivDatum.fromPacket(packet);
                datum.setSession(session);
                Emotiv.log.info(datum.toString());
            }

            @Override
            public void connectionBroken() {
                condition.signal();
            }
        });

       emotiv.start();
       condition.await();
    }


    private final EmotivHid raw;
    private final AtomicBoolean accessed = new AtomicBoolean();
    private final Cipher cipher;
    private final Map<Packet.Sensor, Integer> quality = Maps.newEnumMap(Packet.Sensor.class);

    private volatile int battery;
    
    @Getter
    private final String serial;

    private final Executor executor;

    /**
     * @throws IOException if there was a problem discovering the device.
     */
    public Emotiv() throws IOException {
        raw = new EmotivHid();
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
            SecretKeySpec key = raw.getKey();
            cipher.init(Cipher.DECRYPT_MODE, key);
        } catch (Exception e) {
            throw new IllegalStateException("no javax.crypto support");
        }
        serial = raw.getSerial();

        Config config = ConfigFactory.load().getConfig("com.github.fommil.emokit");
        int threads = config.getInt("threads");
        executor = Executors.newFixedThreadPool(threads);
    }

    /**
     * Poll the device in a background thread and sends signals to registered
     * listeners using a thread pool.
     */
    public void start() {
        if (accessed.getAndSet(true))
            throw new IllegalStateException("Cannot be called more than once.");

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    poller();
                } catch (Exception e) {
                    Emotiv.log.log(Level.SEVERE, "Problem when polling", e);
                    try {
                        close();
                    } catch (IOException ignored) {
                    }
                    fireConnectionBroken();
                }
            }
        };

        Thread thread = new Thread(runnable, "Emotiv polling and decryption");
        thread.setDaemon(true);
        thread.start();
    }

    private void poller() throws TimeoutException, IOException, BadPaddingException, IllegalBlockSizeException {
        byte[] bytes = new byte[EmotivHid.BUFSIZE];
        byte lastCounter = -1;

//                    long lastTimestamp = System.currentTimeMillis();
        while (!raw.isClosed()) {
//                        sun.misc.Unsafe.getUnsafe().park(true, lastTimestamp + 7);
            raw.poll(bytes);

            long timestamp = System.currentTimeMillis();

            byte[] decrypted = cipher.doFinal(bytes);

            // the counter is used to mixin battery and quality levels
            byte counter = decrypted[0];
            if (counter != lastCounter + 1 && lastCounter != 127)
                log.config("missed a packet");

            if (counter < 0) {
                lastCounter = -1;
                battery = 0xFF & counter;
            } else {
                lastCounter = counter;
            }

            Packet.Sensor channel = getQualityChannel(counter);
            if (channel != null) {
                int reading = Packet.Sensor.QUALITY.apply(decrypted);
                quality.put(channel, reading);
            }

            Packet packet = new Packet(timestamp, battery, decrypted, Maps.newEnumMap(quality));
            fireReceivePacket(packet);
        }

    }

    private Packet.Sensor getQualityChannel(byte counter) {
        if (64 <= counter && counter <= 75) {
            counter = (byte) (counter - 64);
        }
        // TODO: https://github.com/fommil/emokit-java/issues/3
//        else if (76 <= counter) {
//            counter = (byte) ((counter - 76) % 4 + 15);
//        }
        switch (counter) {
            case 0:
                return Packet.Sensor.F3;
            case 1:
                return Packet.Sensor.FC5;
            case 2:
                return Packet.Sensor.AF3;
            case 3:
                return Packet.Sensor.F7;
            case 4:
                return Packet.Sensor.T7;
            case 5:
                return Packet.Sensor.P7;
            case 6:
                return Packet.Sensor.O1;
            case 7:
                return Packet.Sensor.O2;
            case 8:
                return Packet.Sensor.P8;
            case 9:
                return Packet.Sensor.T8;
            case 10:
                return Packet.Sensor.F8;
            case 11:
                return Packet.Sensor.AF4;
            case 12:
                return Packet.Sensor.FC6;
            case 13:
                return Packet.Sensor.F4;
            case 14:
                return Packet.Sensor.F8;
            case 15:
                return Packet.Sensor.AF4;
            default:
                return null;
        }
    }

    @Override
    public void close() throws IOException {
        raw.close();
    }

    // https://github.com/peichhorn/lombok-pg/issues/139
    protected void fireReceivePacket(final Packet arg0) {
        for (final EmotivListener l : $registeredEmotivListener) {
            Runnable runnable = new Runnable(){
                @Override
                public void run() {
                    l.receivePacket(arg0);
                }
            };
            executor.execute(runnable);
        }
    }

    protected void fireConnectionBroken() {
        for (final EmotivListener l : $registeredEmotivListener) {
            Runnable runnable = new Runnable(){
                @Override
                public void run() {
                    l.connectionBroken();
                }
            };
            executor.execute(runnable);
        }
    }

    // http://code.google.com/p/projectlombok/issues/detail?id=460
    private final List<EmotivListener> $registeredEmotivListener = Lists.newCopyOnWriteArrayList();
    public void addEmotivListener(final EmotivListener l) {
        if (!$registeredEmotivListener.contains(l)) $registeredEmotivListener.add(l);
    }
    public void removeEmotivListener(final EmotivListener l) {
        $registeredEmotivListener.remove(l);
    }

}
