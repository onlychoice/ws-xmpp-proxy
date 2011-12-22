package com.netease.xmpp.proxy.monitor;

import org.jivesoftware.multiplexer.Session;

public interface MessageEventListener {
    public void onMessageReceived(Session session, String message);
    public void onMessageSend(Session session, String message);
}
