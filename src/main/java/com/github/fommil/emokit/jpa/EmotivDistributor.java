package com.github.fommil.emokit.jpa;

import com.github.fommil.emokit.Emotiv;
import com.github.fommil.emokit.Packet;
import lombok.Data;
import lombok.ListenerSupport;

/**
 * @author Sam Halliday
 */
@Data
@ListenerSupport(Emotiv.PacketListener.class)
public class EmotivDistributor {

    private final Emotiv emotiv;

    public void start() {
        Runnable runner = new Runnable() {
            @Override
            public void run() {
                for (Packet packet : emotiv) {
                    fireReceivePacket(packet);
                }
            }
        };
        new Thread(runner).start();
    }

}
