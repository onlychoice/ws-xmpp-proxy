package com.netease.xmpp.websocket.codec;

import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;

public class Ping extends DefaultWebSocketFrame {
    public Ping(String message) {
        super(message);
    }
}
