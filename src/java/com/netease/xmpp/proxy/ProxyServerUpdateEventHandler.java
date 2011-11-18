package com.netease.xmpp.proxy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
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
    private static Logger logger = Logger.getLogger(ProxyServerUpdateEventHandler.class);

    public ProxyServerUpdateEventHandler(ClientConfigCache config) {
        super(config);
    }

    public void serverInfoUpdated(Message message, Server server) {
        List<ServerInfo> serverHashList = server.getServerList();

        System.out.println("receive server: ");
        for (ServerInfo s : serverHashList) {
            System.out.println("    " + s.getIp() + "->" + s.getHash());
        }

        TreeMap<Long, ServerInfo> newServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> invalidServerNodes = new TreeMap<Long, ServerInfo>();
        TreeMap<Long, ServerInfo> addServerNodes = new TreeMap<Long, ServerInfo>();

        int serverFlag = server.getServerFlag();
        TreeMap<Long, ServerInfo> oldServerNodes = ClientGlobal.getServerNodes();
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

            if (addServerNodes.size() == 0 && invalidServerNodes.size() == 0) {
                return;
            }

            logger.debug("updating server...");
            if (addServerNodes.size() != 0) {
                System.out.println("Add server: ");
                for (Map.Entry<Long, ServerInfo> entry : addServerNodes.entrySet()) {
                    System.out.println("    " + entry.getValue().getIp() + "->" + entry.getKey());
                }
            }

            if (invalidServerNodes.size() != 0) {
                System.out.println("Del server: ");
                for (Map.Entry<Long, ServerInfo> entry : invalidServerNodes.entrySet()) {
                    System.out.println("    " + entry.getValue().getIp() + "->" + entry.getKey());
                }
            }

            ClientGlobal.setServerNodes(newServerNodes);
            ClientGlobal.setServerVersion(message.getVersion());

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
