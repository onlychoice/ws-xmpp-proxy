package com.netease.xmpp.websocket.handler;

import org.jboss.netty.channel.Channel;

public interface WebSocketConnection {
    enum Version {
        HIXIE_75, HIXIE_76, HYBI_10
    }
    
    Channel getChannel();

    /**
     * Sends a text frame
     * 
     * @param message
     *            frame payload
     * @return this
     */
    WebSocketConnection send(String message);

    /**
     * Sends a binary frame
     * 
     * @param message
     *            frame payload
     * @return this
     */
    WebSocketConnection send(byte[] message);

    /**
     * Sends a ping frame
     * 
     * @param message
     *            the payload of the ping
     * @return this
     */
    WebSocketConnection ping(String message);

    void close();

    Version version();
    
    Object getData(String key);
    void setData(String key, Object value);
}