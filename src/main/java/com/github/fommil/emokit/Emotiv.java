// Copyright Samuel Halliday 2012
package com.github.fommil.emokit;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import javax.annotation.concurrent.NotThreadSafe;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import com.github.fommil.emokit.jpa.EmotivDatum;
import com.github.fommil.emokit.jpa.EmotivSession;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unencrypted access to an Emotiv EEG.
 * <p/>
 * The device is constantly polled in a background thread, filling up a buffer
 * (which could cause the application to OutOfMemory if not evacuated).
 *
 * @author Sam Halliday
 */
@Log
@NotThreadSafe
public final class Emotiv implements Closeable {

    public static void main(String[] args) throws Exception {
        Emotiv emotiv = new Emotiv();

        try {
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
        } finally {
            emotiv.close();
        }
    }

    private final EmotivHid raw;
    private final AtomicBoolean accessed = new AtomicBoolean();
    private final Cipher cipher;
    private final EmotivParser parser = new EmotivParser();

    @Getter
    private final String serial;

    @Getter
    private final Executor executor;

    @Getter
    @Setter
    private RejectionHandlerPolicy rejectionHandlerPolicy;

    private static Config config = ConfigFactory.load();
    
    private static int getConfigInt(String key) {
        return config.getConfig("com.github.fommil.emokit").getInt(key);
    }

    /**
     * @throws IOException
     *             if there was a problem discovering the device.
     */
    public Emotiv() throws IOException {
        this(Executors.newFixedThreadPool(getConfigInt("threads")), RejectionHandlerPolicy.DISCARD, false);
    }

    public Emotiv(Executor executor) throws IOException {
        this(executor, RejectionHandlerPolicy.DISCARD, true);
    }

    public Emotiv(RejectionHandlerPolicy policy) throws IOException {
        this(Executors.newFixedThreadPool(getConfigInt("threads")), policy, false);
    }

    public Emotiv(Executor executor, RejectionHandlerPolicy policy) throws IOException {
        this(executor, policy, true);
    }
    
    private Emotiv(Executor executor, RejectionHandlerPolicy policy, boolean handlerCheck) throws IOException {
        raw = new EmotivHid();
        try {
            cipher = Cipher.getInstance("AES/ECB/NoPadding");
            SecretKeySpec key = raw.getKey();
            cipher.init(Cipher.DECRYPT_MODE, key);
        } catch (Exception e) {
            throw new IllegalStateException("no javax.crypto support");
        }
        serial = raw.getSerial();

        this.executor = executor;
        setRejectionHandlerPolicy(policy);

        setDefaultRejectionHandlerWithCheck(handlerCheck);
    }

    /**
     * Poll the device in a background thread and sends signals to registered
     * listeners using a thread pool.
     */
    public void start() {
        if (accessed.getAndSet(true)) throw new IllegalStateException("Cannot be called more than once.");

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

        while (!raw.isClosed()) {
            raw.poll(bytes);

            long timestamp = System.currentTimeMillis();
            byte[] decrypted = cipher.doFinal(bytes);

            Packet packet = parser.parse(timestamp, decrypted);
            fireReceivePacket(packet);
        }
    }

    private void setDefaultRejectionHandlerWithCheck(boolean check) {
        if (!(executor instanceof ThreadPoolExecutor)) {
            log.warning("Cannot set default rejection handler for " + executor.getClass().getName());
            return;
        }

        log.fine("Setting default rejection handler");

        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;

        if (check && tpe.getRejectedExecutionHandler() != null) {
            log.fine("Rejection handler already set");
            return;
        }

        tpe.setRejectedExecutionHandler(getRejectionHandlerPolicy().getHandler());
    }

    @Override
    public void close() throws IOException {
        raw.close();
    }

    // https://github.com/peichhorn/lombok-pg/issues/139
    protected void fireReceivePacket(final Packet arg0) {
        for (final EmotivListener l : $registeredEmotivListener) {
            Runnable runnable = new Runnable() {
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
            Runnable runnable = new Runnable() {
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
