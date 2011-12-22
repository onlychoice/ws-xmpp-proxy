package com.netease.xmpp.websocket;

import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.filter.SSLFilter;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.ConnectionCloseListener;
import org.jivesoftware.multiplexer.PacketDeliverer;
import org.jivesoftware.multiplexer.Session;
import org.jivesoftware.multiplexer.net.MXParser;
import org.jivesoftware.multiplexer.net.SSLConfig;
import org.jivesoftware.multiplexer.net.SSLJiveKeyManagerFactory;
import org.jivesoftware.multiplexer.net.SSLJiveTrustManagerFactory;
import org.jivesoftware.multiplexer.net.ServerTrustManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import com.netease.xmpp.proxy.monitor.XmppMessageEventListener;
import com.netease.xmpp.websocket.codec.Ping;
import com.netease.xmpp.websocket.handler.WebSocketConnection;

public class CMWebSocketConnection implements WebSocketConnection, Connection {
    private final Channel channel;
    private Version version;

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";
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

    private Session session;

    private ConnectionCloseListener closeListener;

    /**
     * Deliverer to use when the connection is closed or was closed when delivering a packet.
     */
    private PacketDeliverer backupDeliverer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;

    // TODO Uso el #checkHealth????
    /**
     * TLS policy currently in use for this connection.
     */
    private TLSPolicy tlsPolicy = TLSPolicy.optional;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;
    private CharsetEncoder encoder;

    private Map<String, Object> dataMap = new ConcurrentHashMap<String, Object>();

    public CMWebSocketConnection(Channel channel, PacketDeliverer packetDeliverer) {
        this.channel = channel;
        this.backupDeliverer = packetDeliverer;
        encoder = Charset.forName(CHARSET).newEncoder();
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public CMWebSocketConnection send(String message) {
        return send(new DefaultWebSocketFrame(message));
    }

    @Override
    public CMWebSocketConnection send(byte[] message) {
        return send(new DefaultWebSocketFrame(0x00, ChannelBuffers.wrappedBuffer(message)));
    }

    @Override
    public CMWebSocketConnection ping(String message) {
        return send(new Ping(message));
    }

    private CMWebSocketConnection send(WebSocketFrame frame) {
        channel.write(frame);
        return this;
    }

    @Override
    public void close() {
        boolean wasClosed = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    // deliverRawText(flashClient ? "</flash:stream>" : "</stream:stream>", false);
                } catch (Exception e) {
                    // Ignore
                }
                closeConnection();
                wasClosed = true;
            }
        }
        if (wasClosed) {
            notifyCloseListeners();
        }
    }

    private void closeConnection() {
        channel.close();
    }

    /**
     * Notifies all close listeners that the connection has been closed. Used by subclasses to
     * properly finish closing the connection.
     */
    private void notifyCloseListeners() {
        if (closeListener != null) {
            try {
                closeListener.onConnectionClose(session);
            } catch (Exception e) {
                Log.error("Error notifying listener: " + closeListener, e);
            }
        }
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public Version version() {
        return version;
    }

    @Override
    public Object getData(String key) {
        return dataMap.get(key);
    }

    @Override
    public void setData(String key, Object value) {
        dataMap.put(key, value);
    }

    @Override
    public void deliver(String stanza) {
        if (isClosed()) {
            XMPPPacketReader xmppReader = new XMPPPacketReader();
            xmppReader.setXPPFactory(factory);
            try {
                Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
                backupDeliverer.deliver(doc);
            } catch (Exception e) {
                Log.error("Error parsing stanza: " + stanza, e);
            }
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(stanza.length());
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                buffer.putString(stanza, encoder);
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                send(buffer.array());

                if (stanza.indexOf("message") > 0) {
                    XmppMessageEventListener.getInstance().onMessageSend(session, stanza);
                }
            } catch (Exception e) {
                Log.debug("Error delivering packet" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                XMPPPacketReader xmppReader = new XMPPPacketReader();
                xmppReader.setXPPFactory(factory);
                try {
                    Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
                    backupDeliverer.deliver(doc);
                } catch (Exception e) {
                    Log.error("Error parsing stanza: " + stanza, e);
                }
            }
        }

    }

    public void deliverRawText(String text) {
        // Deliver the packet in asynchronous mode
        deliverRawText(text, true);
    }

    public void deliverRawText(String text, boolean asynchronous) {
        if (!isClosed()) {
            ByteBuffer buffer = ByteBuffer.allocate(text.length());
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                // Charset charset = Charset.forName(CHARSET);
                // buffer.putString(text, charset.newEncoder());
                buffer.put(text.getBytes(CHARSET));
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                if (asynchronous) {
                    send(buffer.array());
                } else {
                    send(buffer.array());
                    // Send stanza and wait for ACK (using a 2 seconds default timeout)
                    boolean ok = channel.write(
                            new DefaultWebSocketFrame(0xFF, ChannelBuffers.wrappedBuffer(buffer
                                    .array()))).awaitUninterruptibly(
                            JiveGlobals.getIntProperty("connection.ack.timeout", 2000));
                    if (!ok) {
                        Log.warn("No ACK was received when sending stanza to: " + this.toString());
                    }
                }
            } catch (Exception e) {
                Log.debug("Error delivering raw text" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
            }
        }
    }

    @Override
    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    @Override
    public InetAddress getInetAddress() throws UnknownHostException {
        return ((InetSocketAddress) channel.getRemoteAddress()).getAddress();
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    @Override
    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    @Override
    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    @Override
    public void init(Session session) {
        this.session = session;
    }

    @Override
    public boolean isClosed() {
        if (session == null) {
            return !channel.isConnected();
        }
        return session.getStatus() == Session.STATUS_CLOSED && !channel.isConnected();
    }

    @Override
    public boolean isCompressed() {
        // TODO is compress?
        return false;
    }

    @Override
    public boolean isFlashClient() {
        return flashClient;
    }

    @Override
    public boolean isSecure() {
        return channel.getPipeline().get("tls") != null;
    }

    @Override
    public void registerCloseListener(ConnectionCloseListener listener, Object ignore) {
        if (closeListener != null) {
            throw new IllegalStateException("Close listener already configured");
        }
        if (isClosed()) {
            listener.onConnectionClose(session);
        } else {
            closeListener = listener;
        }
    }

    @Override
    public void removeCloseListener(ConnectionCloseListener listener) {
        if (closeListener == listener) {
            closeListener = null;
        }
    }

    @Override
    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    @Override
    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
    }

    @Override
    public void setLanaguage(String language) {
        this.language = language;
    }

    @Override
    public void setTlsPolicy(TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    @Override
    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    public void startCompression() {
        // TODO startCompression
    }

    @Override
    public void startTLS(boolean clientMode, String remoteServer) throws Exception {
        // KeyStore ksKeys = SSLConfig.getKeyStore();
        // String keypass = SSLConfig.getKeyPassword();
        //
        // KeyStore ksTrust = SSLConfig.getTrustStore();
        // String trustpass = SSLConfig.getTrustPassword();
        //
        // // KeyManager's decide which key material to use.
        // KeyManager[] km = SSLJiveKeyManagerFactory.getKeyManagers(ksKeys, keypass);
        //
        // // TrustManager's decide whether to allow connections.
        // TrustManager[] tm = SSLJiveTrustManagerFactory.getTrustManagers(ksTrust, trustpass);
        // // TODO Set proper value when s2s is supported
        // boolean needClientAuth = false;
        // if (clientMode || needClientAuth) {
        // // Check if we can trust certificates presented by the server
        // tm = new TrustManager[] { new ServerTrustManager(remoteServer, ksTrust) };
        // }
        //
        // SSLContext tlsContext = SSLContext.getInstance("TLS");
        // tlsContext.init(km, tm, null);
        // SSLEngine sslEngine = tlsContext.createSSLEngine();
        // sslEngine.setUseClientMode(clientMode);
        // if (needClientAuth) {
        // // Only REQUIRE client authentication if we are fully verifying certificates
        // if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true)
        // && JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true)
        // && !JiveGlobals.getBooleanProperty("xmpp.server.certificate.accept-selfsigned",
        // false)) {
        // sslEngine.setWantClientAuth(true);
        // } else {
        // // Just indicate that we would like to authenticate the client but if client
        // // certificates are self-signed or have no certificate chain then we are still
        // // good
        // sslEngine.setWantClientAuth(true);
        // }
        // }
        //
        // SslHandler handler = new SslHandler(sslEngine, true);
        //
        // if (channel.getPipeline().get("tls") != null) {
        // channel.getPipeline().remove("tls");
        // }
        //
        // channel.getPipeline().addFirst("tls", handler);

        if (!clientMode) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
        }
    }

    @Override
    public void systemShutdown() {
        deliverRawText("<stream:error><system-shutdown "
                + "xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");
        close();
    }

    @Override
    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        deliverRawText(" ");
        return !isClosed();
    }
}
