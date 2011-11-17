package com.netease.xmpp.proxy;

import org.jboss.netty.bootstrap.ClientBootstrap;

import com.netease.xmpp.master.client.ClientConfigCache;
import com.netease.xmpp.master.client.SyncClient;
import com.netease.xmpp.master.event.EventDispatcher;
import com.netease.xmpp.master.event.EventType;
import com.netease.xmpp.master.event.client.HashUpdateEventHandler;
import com.netease.xmpp.master.event.client.ServerConnectionEventHandler;

public class ProxySyncClient extends SyncClient {
    public ProxySyncClient(int clientType) {
        super(clientType);
    }

    public void registerCustomEvent(EventDispatcher eventDispatcher, ClientConfigCache clientConfig,
            ClientBootstrap bootstrap) {
        HashUpdateEventHandler hashUpdateEventHandler = new HashUpdateEventHandler(clientConfig);
        ProxyServerUpdateEventHandler serverUpdateEventHandler = new ProxyServerUpdateEventHandler(
                clientConfig);
        ProxyUpdateCompletedEventHandler updateCompletedEventHandler = new ProxyUpdateCompletedEventHandler();
        ServerConnectionEventHandler serverConnectionEventHandler = new ServerConnectionEventHandler(
                bootstrap, eventDispatcher);

        eventDispatcher.registerEvent(hashUpdateEventHandler, EventType.CLIENT_HASH_UPDATED);

        eventDispatcher.registerEvent(serverUpdateEventHandler, EventType.CLIENT_SERVER_UPDATED);
        eventDispatcher.registerEvent(updateCompletedEventHandler, //
                EventType.CLIENT_SERVER_UPDATE_COMPLETE, //
                EventType.CLIENT_HASH_UPDATE_COMPLETE);

        eventDispatcher.registerEvent(updateCompletedEventHandler, //
                EventType.CLIENT_SERVER_ALL_COMPLETE, //
                EventType.CLIENT_HASH_ALL_COMPLETE);

        eventDispatcher.registerEvent(serverConnectionEventHandler, //
                EventType.CLIENT_SERVER_CONNECTED, //
                EventType.CLIENT_SERVER_DISCONNECTED, //
                EventType.CLIENT_SERVER_HEARTBEAT, //
                EventType.CLIENT_SERVER_HEARTBEAT_TIMOUT);
    }
}
