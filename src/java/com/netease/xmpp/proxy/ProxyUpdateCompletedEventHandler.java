package com.netease.xmpp.proxy;

import org.jivesoftware.multiplexer.ConnectionManager;

import com.netease.xmpp.master.client.ClientGlobal;
import com.netease.xmpp.master.client.TaskExecutor;
import com.netease.xmpp.master.event.client.UpdateCompletedEventHandler;

public class ProxyUpdateCompletedEventHandler extends UpdateCompletedEventHandler {
    public void allHashUpdated() {
        checkProxyStatus();
    }

    public void allServerUpdated() {
        checkProxyStatus();
    }
    
    private void checkProxyStatus() {
        if (!ClientGlobal.getIsUpdating()) {
            if (!ClientGlobal.getIsClientStarted()) {
                ConnectionManager.getInstance().start();
            }
            TaskExecutor.getInstance().resume();
        }
    }
}
