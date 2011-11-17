package com.netease.xmpp.proxy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.ServerSurrogate;

import com.netease.xmpp.master.client.ClientConfigCache;
import com.netease.xmpp.master.client.ClientGlobal;
import com.netease.xmpp.master.common.Message;
import com.netease.xmpp.master.common.ServerListProtos.Server.ServerInfo;
import com.netease.xmpp.master.event.client.ServerUpdateEventHandler;

public class ProxyServerUpdateEventHandler extends ServerUpdateEventHandler {
    public ProxyServerUpdateEventHandler(ClientConfigCache config) {
        super(config);
    }

    public void serverInfoUpdated(Message message, List<ServerInfo> serverHashList) {
        TreeMap<Long, ServerInfo> oldServerNodes = ClientGlobal.getServerNodes();

        TreeMap<Long, ServerInfo> newServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> invalidServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> addServerNodes = new TreeMap<Long, ServerInfo>();

        synchronized (oldServerNodes) {
            for (ServerInfo node : serverHashList) {
                newServerNodes.put(node.getHash(), node);
            }

            for (Map.Entry<Long, ServerInfo> entry : oldServerNodes.entrySet()) {
                if (!newServerNodes.containsKey(entry.getKey())) {
                    invalidServerNodes.put(entry.getKey(), entry.getValue());
                } else {
                    ServerInfo s1 = newServerNodes.get(entry.getKey());
                    ServerInfo s2 = entry.getValue();

                    if (!(s1.getIp().equals(s2.getIp()) && s1.getCMPort() == s2.getCMPort())) {
                        invalidServerNodes.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            for (Map.Entry<Long, ServerInfo> entry : newServerNodes.entrySet()) {
                if (!oldServerNodes.containsKey(entry.getKey())) {
                    addServerNodes.put(entry.getKey(), entry.getValue());
                } else {
                    ServerInfo s1 = oldServerNodes.get(entry.getKey());
                    ServerInfo s2 = entry.getValue();

                    if (!(s1.getIp().equals(s2.getIp()) && s1.getCMPort() == s2.getCMPort())) {
                        addServerNodes.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            ClientGlobal.setServerNodes(newServerNodes);

            if (ClientGlobal.getIsClientStartup()) {
                ServerSurrogate serverSurrogate = ConnectionManager.getInstance()
                        .getServerSurrogate();
                serverSurrogate.killInvalidClient(invalidServerNodes, addServerNodes,
                        oldServerNodes);
                serverSurrogate.updateServerConnection(invalidServerNodes, addServerNodes);
            }

            oldServerNodes.clear();
        }
    }
}
