package com.netease.xmpp.master.client;

import java.util.TreeMap;

import com.netease.xmpp.master.common.ConfigCache;
import com.netease.xmpp.master.common.ServerListProtos.Server.ServerInfo;

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

    private TreeMap<Long, ServerInfo> serverNodes = new TreeMap<Long, ServerInfo>();

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

    public TreeMap<Long, ServerInfo> getServerNodes() {
        return serverNodes;
    }

    public void setServerNodes(TreeMap<Long, ServerInfo> serverNodes) {
        this.serverNodes = serverNodes;
    }

    public ServerInfo getServerNodeForKey(Long key) {
        if (!serverNodes.containsKey(key)) {
            key = serverNodes.ceilingKey(key);
            if (key == null) {
                key = serverNodes.firstKey();
            }
        }

        return serverNodes.get(key);
    }
}
