package com.github.fommil.emokit;

/**
 * Asynchronous listener interface for packets from an Emotiv.
 *
 * @author Sam Halliday
 */
public interface EmotivListener {
    /**
     * @param packet
     */
    public void receivePacket(Packet packet);

}
