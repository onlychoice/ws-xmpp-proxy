package com.netease.xmpp.proxy;

import java.util.List;
import java.util.TreeMap;

import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.ServerSurrogate;

import com.netease.xmpp.master.client.ClientConfigCache;
import com.netease.xmpp.master.client.ClientGlobal;
import com.netease.xmpp.master.common.Message;
import com.netease.xmpp.master.common.MessageFlag;
import com.netease.xmpp.master.common.ServerListProtos.Server;
import com.netease.xmpp.master.common.ServerListProtos.Server.ServerInfo;
import com.netease.xmpp.master.event.client.ServerUpdateEventHandler;

public class ProxyServerUpdateEventHandler extends ServerUpdateEventHandler {
    public ProxyServerUpdateEventHandler(ClientConfigCache config) {
        super(config);
    }

    public void serverInfoUpdated(Message message, Server server) {
        ClientGlobal.setServerVersion(message.getVersion());

        List<ServerInfo> serverHashList = server.getServerList();
        TreeMap<Long, ServerInfo> oldServerNodes = ClientGlobal.getServerNodes();

        TreeMap<Long, ServerInfo> newServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> invalidServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> addServerNodes = new TreeMap<Long, ServerInfo>();

        int serverFlag = server.getServerFlag();

        synchronized (oldServerNodes) {
            if (serverFlag == MessageFlag.FLAG_SERVER_ADD) {
                // Add server
                for (ServerInfo node : serverHashList) {
                    if (!oldServerNodes.containsKey(node.getHash())) {
                        addServerNodes.put(node.getHash(), node);
                        newServerNodes.put(node.getHash(), node);
                    }
                }

                newServerNodes.putAll(oldServerNodes);
            } else if (serverFlag == MessageFlag.FLAG_SERVER_DEL) {
                // Del server
                newServerNodes.putAll(oldServerNodes);

                for (ServerInfo node : serverHashList) {
                    if (oldServerNodes.containsKey(node.getHash())) {
                        invalidServerNodes.put(node.getHash(), node);
                        newServerNodes.remove(node.getHash());
                    }
                }
            }

            if (addServerNodes.size() == 0 || invalidServerNodes.size() == 0) {
                return;
            }

            ClientGlobal.setServerNodes(newServerNodes);

            if (ClientGlobal.getIsClientStarted()) {
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
