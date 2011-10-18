package com.netease.xmpp.master.event.client;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;
import org.jivesoftware.multiplexer.ConnectionManager;

import com.netease.xmpp.master.common.MessageFlag;
import com.netease.xmpp.master.common.Message;
import com.netease.xmpp.master.event.EventContext;
import com.netease.xmpp.master.event.EventHandler;
import com.netease.xmpp.master.event.EventType;
import com.netease.xmpp.master.event.UnrecognizedEvent;
import com.netease.xmpp.proxy.ProxyGlobal;
import com.netease.xmpp.proxy.TaskExecutor;

public class UpdateCompletedEventHandler implements EventHandler {
    private static Logger logger = Logger.getLogger(UpdateCompletedEventHandler.class);

    @Override
    public void handle(EventContext ctx) throws IOException {
        EventType event = ctx.getEvent();
        Channel serverChannel = ctx.getChannel();
        Message data = (Message) ctx.getData();

        switch (event) {
        case CLIENT_SERVER_UPDATE_COMPLETE:
            serverChannel.write(new Message(MessageFlag.FLAG_SERVER_UPDATE_COMPLETE, data
                    .getVersion(), 0, null));
            break;
        case CLIENT_HASH_UPDATE_COMPLETE:
            serverChannel.write(new Message(MessageFlag.FLAG_HASH_UPDATE_COMPLETE, data
                    .getVersion(), 0, null));
            break;
        case CLIENT_SERVER_ALL_COMPLETE:
            ProxyGlobal.setIsAllServerUpdate(true);
            checkProxyStatus();
            break;
        case CLIENT_HASH_ALL_COMPLETE:
            ProxyGlobal.setIsAllHashUpdate(true);
            checkProxyStatus();
            break;
        default:
            throw new UnrecognizedEvent(event.toString());
        }
    }

    private void checkProxyStatus() {
        if (!ProxyGlobal.getIsUpdating()) {
            if (!ProxyGlobal.getIsProxyStartup()) {
                ConnectionManager.getInstance().start();
            }
            TaskExecutor.getInstance().resume();
        }
    }
}
