package com.netease.xmpp.master.event;

public enum EventType {
    // ==================CLIENT=====================//
    /**
     * Same to HASH_UPDATE, but for client event.
     */
    CLIENT_HASH_UPDATED,

    /**
     * Server list updated.
     */
    CLIENT_SERVER_UPDATED,

    /**
     * Server info update successfully, for client event.
     */
    CLIENT_SERVER_UPDATE_COMPLETE,
    /**
     * Hash info update successfully, for client event.
     */
    CLIENT_HASH_UPDATE_COMPLETE,
    
    /**
     * All clients have been synced to the latest sever info, for client event.
     */
    CLIENT_SERVER_ALL_COMPLETE,
    /**
     * All clients have been synced to the latest hash info, for client event.
     */
    CLIENT_HASH_ALL_COMPLETE,

    /**
     * Client disconnected from master server.
     */
    CLIENT_SERVER_DISCONNECTED,
    /**
     * Client connected with master server.
     */
    CLIENT_SERVER_CONNECTED,
    /**
     * Heart beat from master server.
     */
    CLIENT_SERVER_HEARTBEAT,
    /**
     * Heart beat timeout.
     */
    CLIENT_SERVER_HEARTBEAT_TIMOUT
}
