package com.netease.xmpp.proxy;

import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyGlobal {
    /**
     * Is proxy startup?
     */
    private static AtomicBoolean isProxyStartup = new AtomicBoolean(false);

    /**
     * Is server info update completely?
     */
    private static AtomicBoolean isServerUpdate = new AtomicBoolean(false);

    /**
     * Is hash info update completely?
     */
    private static AtomicBoolean isHashUpdate = new AtomicBoolean(false);

    /**
     * Is all client synced to the latest server info?
     */
    private static AtomicBoolean isAllServerUpdate = new AtomicBoolean(false);

    /**
     * Is all client synced to the latest hash info?
     */
    private static AtomicBoolean isAllHashUpdate = new AtomicBoolean(false);

    public static boolean getIsProxyStartup() {
        return isProxyStartup.get();
    }

    public static void setIsProxyStartup(boolean flag) {
        isProxyStartup.set(flag);
    }

    public static boolean getIsServerUpdate() {
        return isServerUpdate.get();
    }

    public static void setIsServerUpdate(boolean flag) {
        isServerUpdate.set(flag);
    }

    public static boolean getIsHashUpdate() {
        return isHashUpdate.get();
    }

    public static void setIsHashUpdate(boolean flag) {
        isHashUpdate.set(flag);
    }

    public static boolean getIsAllServerUpdate() {
        return isAllServerUpdate.get();
    }

    public static void setIsAllServerUpdate(boolean flag) {
        isAllServerUpdate.set(flag);
    }

    public static boolean getIsAllHashUpdate() {
        return isAllHashUpdate.get();
    }

    public static void setIsAllHashUpdate(boolean flag) {
        isAllHashUpdate.set(flag);
    }

    public static synchronized boolean getIsUpdating() {
        if (getIsAllServerUpdate() && getIsAllHashUpdate()) {
            return false;
        }

        return true;
    }
}
