package com.netease.xmpp.master.event.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.ServerSurrogate;

import com.netease.xmpp.master.client.ClientConfigCache;
import com.netease.xmpp.master.common.Message;
import com.netease.xmpp.master.common.ServerHashProtos.Server;
import com.netease.xmpp.master.common.ServerHashProtos.Server.ServerHash;
import com.netease.xmpp.master.event.EventContext;
import com.netease.xmpp.master.event.EventHandler;
import com.netease.xmpp.master.event.EventType;
import com.netease.xmpp.proxy.ProxyGlobal;

public class ServerUpdateEventHandler implements EventHandler {
    private ClientConfigCache config;

    public ServerUpdateEventHandler(ClientConfigCache config) {
        this.config = config;
    }

    @Override
    public void handle(EventContext ctx) throws IOException {
        ProxyGlobal.setIsServerUpdate(false);
        ProxyGlobal.setIsAllServerUpdate(false);

        Message data = (Message) ctx.getData();
        byte[] serverData = data.getData();

        ByteArrayInputStream input = new ByteArrayInputStream(serverData);

        Server.Builder server = Server.newBuilder();
        try {
            server.mergeDelimitedFrom(input);

            List<ServerHash> serverHashList = server.getServerList();

            TreeMap<Long, ServerHash> oldServerNodes = config.getServerNodes();
            TreeMap<Long, ServerHash> newServerNodes = new TreeMap<Long, ServerHash>();
            TreeMap<Long, ServerHash> invalidServerNodes = new TreeMap<Long, ServerHash>();
            TreeMap<Long, ServerHash> addServerNodes = new TreeMap<Long, ServerHash>();

            synchronized (oldServerNodes) {
                for (ServerHash node : serverHashList) {
                    newServerNodes.put(node.getHash(), node);
                }

                for (Map.Entry<Long, ServerHash> entry : oldServerNodes.entrySet()) {
                    if (!newServerNodes.containsKey(entry.getKey())) {
                        invalidServerNodes.put(entry.getKey(), entry.getValue());
                    } else {
                        ServerHash s1 = newServerNodes.get(entry.getKey());
                        ServerHash s2 = entry.getValue();
                        
                        if(!(s1.getIp().equals(s2.getIp()) && s1.getPort() == s2.getPort())) {
                            invalidServerNodes.put(entry.getKey(), entry.getValue());
                        }
                    }
                }          
                
                for (Map.Entry<Long, ServerHash> entry : newServerNodes.entrySet()) {
                    if (!oldServerNodes.containsKey(entry.getKey())) {
                        addServerNodes.put(entry.getKey(), entry.getValue());
                    }else {
                        ServerHash s1 = oldServerNodes.get(entry.getKey());
                        ServerHash s2 = entry.getValue();
                        
                        if(!(s1.getIp().equals(s2.getIp()) && s1.getPort() == s2.getPort())) {
                            addServerNodes.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                
                config.setServerNodes(newServerNodes);
                
                if (ProxyGlobal.getIsProxyStartup()) {
                    ServerSurrogate serverSurrogate = ConnectionManager.getInstance()
                            .getServerSurrogate();
                    serverSurrogate.killInvalidClient(invalidServerNodes, addServerNodes, oldServerNodes);
                    serverSurrogate.updateServerConnection(invalidServerNodes, addServerNodes);
                }
                
                oldServerNodes.clear();
            }

            ProxyGlobal.setIsServerUpdate(true);

            ctx.getDispatcher().dispatchEvent(ctx.getChannel(), data,
                    EventType.CLIENT_SERVER_UPDATE_COMPLETE);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
