package com.netease.xmpp.master.client;

import java.util.TreeMap;

import com.netease.xmpp.master.common.ConfigCache;
import com.netease.xmpp.master.common.ServerHashProtos.Server.ServerHash;

public class ClientConfigCache extends ConfigCache {
    private static ClientConfigCache instance = null;

    public static ClientConfigCache getInstance() {
        if (instance == null) {
            instance = new ClientConfigCache();
        }

        return instance;
    }

    private String masterServerHost = null;
    private int masterServerPort = 0;

    private TreeMap<Long, ServerHash> serverNodes = new TreeMap<Long, ServerHash>();

    private ClientConfigCache() {
        // Do nothing
    }

    public String getMasterServerHost() {
        return masterServerHost;
    }

    public void setMasterServerHost(String masterServerHost) {
        this.masterServerHost = masterServerHost;
    }

    public int getMasterServerPort() {
        return masterServerPort;
    }

    public void setMasterServerPort(int masterServerPort) {
        this.masterServerPort = masterServerPort;
    }

    public TreeMap<Long, ServerHash> getServerNodes() {
        return serverNodes;
    }

    public void setServerNodes(TreeMap<Long, ServerHash> serverNodes) {
        this.serverNodes = serverNodes;
    }

    public ServerHash getServerNodeForKey(Long key) {
        if (!serverNodes.containsKey(key)) {
            key = serverNodes.ceilingKey(key);
            if (key == null) {
                key = serverNodes.firstKey();
            }
        }

        return serverNodes.get(key);
    }
}
