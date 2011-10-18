package com.netease.xmpp.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.common.ByteBuffer;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.multiplexer.net.ClientStanzaHandler;
import org.jivesoftware.multiplexer.net.MXParser;
import org.jivesoftware.multiplexer.net.XMLLightweightParser;
import org.jivesoftware.multiplexer.spi.ServerRouter;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.netease.xmpp.proxy.TaskExecutor;
import com.netease.xmpp.websocket.handler.WebSocketConnection;
import com.netease.xmpp.websocket.handler.WebSocketHandler;

public class CMWebSocketChannelHandler implements WebSocketHandler {
    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    static final String CHARSET = "UTF-8";
    static final String XML_PARSER = "XML-PARSER";
    private static final String HANDLER = "HANDLER";

    protected static PacketRouter router = new ServerRouter();
    protected static String serverName = ConnectionManager.getInstance().getServerName();
    private static Map<Integer, XmlPullParser> parsers = new ConcurrentHashMap<Integer, XmlPullParser>();

    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    class ClientTask implements Runnable {
        private WebSocketConnection connection = null;
        private ClientStanzaHandler handler = null;
        private String msg = null;
        private XmlPullParser parser = null;

        public ClientTask(WebSocketConnection connection, ClientStanzaHandler handler, String msg,
                XmlPullParser parser) {
            this.connection = connection;
            this.handler = handler;
            this.msg = msg;
            this.parser = parser;
        }

        @Override
        public void run() {
            try {
                handler.process(msg, parser);
            } catch (Exception e) {
                e.printStackTrace();
                connection.close();
            }
        }
    }

    @Override
    public void onClose(WebSocketConnection connection) throws Exception {
        connection.close();
    }

    @Override
    public void onMessage(WebSocketConnection connection, String msg) throws Throwable {
        ClientStanzaHandler handler = (ClientStanzaHandler) connection.getData(HANDLER);

        // Get the parser to use to process stanza. For optimization there is going
        // to be a parser for each running thread. Each Filter will be executed
        // by the Executor placed as the first Filter. So we can have a parser associated
        // to each Thread
        int hashCode = Thread.currentThread().hashCode();
        XmlPullParser parser = parsers.get(hashCode);
        if (parser == null) {
            parser = factory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parsers.put(hashCode, parser);
        }

        XMLLightweightParser lightwightParser = (XMLLightweightParser) connection
                .getData(XML_PARSER);
        lightwightParser.read(ByteBuffer.wrap(msg.getBytes(CHARSET)));

        if (lightwightParser.areThereMsgs()) {
            for (String stanza : lightwightParser.getMsgs()) {
                TaskExecutor.getInstance().addClientTask(
                        new ClientTask(connection, handler, stanza, parser));
            }
        }
    }

    @Override
    public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
        onMessage(connection, new String(msg, "UTF-8"));
    }

    @Override
    public void onOpen(WebSocketConnection connection) throws Exception {
        // Create a new XML parser for the new connection. The parser will be used by the
        // XMPPDecoder filter.
        XMLLightweightParser parser = new XMLLightweightParser(CHARSET);
        ClientStanzaHandler handler = new ClientStanzaHandler(router, serverName,
                (Connection) connection);

        connection.setData(XML_PARSER, parser);
        connection.setData(HANDLER, handler);
    }

    @Override
    public void onPong(WebSocketConnection connection, String msg) throws Throwable {
        System.out.println("pong");
    }
}
