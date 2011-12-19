package com.netease.xmpp.websocket;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jivesoftware.multiplexer.net.SSLConfig;
import org.jivesoftware.multiplexer.net.SSLJiveKeyManagerFactory;
import org.jivesoftware.multiplexer.net.SSLJiveTrustManagerFactory;
import org.jivesoftware.multiplexer.net.ServerTrustManager;
import org.jivesoftware.util.JiveGlobals;

import com.netease.xmpp.websocket.handler.NettyWebSocketChannelHandler;

public class WebSocketServer {
    private static final int DEFAULT_WEBSOCKET_PORT = 5333;

    private int port;
    private ServerBootstrap bootstrap;
    private ChannelFactory factory;
    private ThreadPoolExecutor executor = null;
    private ChannelGroup webSocketChannelGroup = new DefaultChannelGroup("websocket");

    public WebSocketServer() {
        this(DEFAULT_WEBSOCKET_PORT);
    }

    public WebSocketServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 100, 60,
                    TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
            factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors
                    .newCachedThreadPool());
            bootstrap = new ServerBootstrap(factory);

            // Set up the event pipeline factory.
            bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                @Override
                public ChannelPipeline getPipeline() throws Exception {
                    final ChannelPipeline pipeline = pipeline();

                    // pipeline.addLast("tls", new SslHandler(getSSLEngine()));

                    pipeline.addLast("decoder", new HttpRequestDecoder());
                    pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
                    pipeline.addLast("encoder", new HttpResponseEncoder());
                    pipeline.addLast("handler", new NettyWebSocketChannelHandler(executor,
                            new CMWebSocketChannelHandler()));

                    return pipeline;
                }
            });

            // Bind and start to accept incoming connections.
            Channel channel = bootstrap.bind(new InetSocketAddress(port));
            webSocketChannelGroup.add(channel);

            System.out.println("-=[ STARTED ]=- on port#: " + port);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private SSLEngine getSSLEngine() throws Exception {
        KeyStore ksKeys = SSLConfig.getKeyStore();
        String keypass = SSLConfig.getKeyPassword();

        KeyStore ksTrust = SSLConfig.getTrustStore();
        String trustpass = SSLConfig.getTrustPassword();

        // KeyManager's decide which key material to use.
        KeyManager[] km = SSLJiveKeyManagerFactory.getKeyManagers(ksKeys, keypass);

        // TrustManager's decide whether to allow connections.
        TrustManager[] tm = SSLJiveTrustManagerFactory.getTrustManagers(ksTrust, trustpass);
        // TODO Set proper value when s2s is supported
        boolean needClientAuth = false;
        if (false || needClientAuth) {
            // Check if we can trust certificates presented by the server
            tm = new TrustManager[] { new ServerTrustManager(null, ksTrust) };
        }

        SSLContext tlsContext = SSLContext.getInstance("TLS");
        tlsContext.init(km, tm, null);
        SSLEngine sslEngine = tlsContext.createSSLEngine();
        sslEngine.setUseClientMode(false);

        if (needClientAuth) {
            // Only REQUIRE client authentication if we are fully verifying certificates
            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true)
                    && JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true)
                    && !JiveGlobals.getBooleanProperty("xmpp.server.certificate.accept-selfsigned",
                            false)) {
                sslEngine.setWantClientAuth(true);
            } else {
                // Just indicate that we would like to authenticate the client but if client
                // certificates are self-signed or have no certificate chain then we are still
                // good
                sslEngine.setWantClientAuth(true);
            }
        }

        return sslEngine;
    }

    public void stop() {
        executor.shutdownNow();
        webSocketChannelGroup.close().awaitUninterruptibly();
        factory.releaseExternalResources();
    }

    public static void main(final String[] args) {
        final WebSocketServer websok = new WebSocketServer();
        websok.start();
    }
}
