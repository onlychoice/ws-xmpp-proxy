package com.netease.xmpp.websocket.handler;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.jivesoftware.multiplexer.spi.ClientFailoverDeliverer;

import com.netease.xmpp.websocket.CMWebSocketConnection;
import com.netease.xmpp.websocket.codec.Hybi10WebSocketFrameDecoder;
import com.netease.xmpp.websocket.codec.Hybi10WebSocketFrameEncoder;
import com.netease.xmpp.websocket.codec.Pong;

import sun.misc.BASE64Encoder;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.ORIGIN;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY1;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_KEY2;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_LOCATION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_ORIGIN;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_PROTOCOL;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_LOCATION;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_ORIGIN;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.WEBSOCKET_PROTOCOL;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static org.jboss.netty.handler.codec.http.HttpMethod.GET;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyWebSocketChannelHandler extends SimpleChannelUpstreamHandler {
    private static final MessageDigest SHA_1;
    static {
        try {
            SHA_1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-1 not supported on this platform");
        }
    }

    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final BASE64Encoder encoder = new BASE64Encoder();
    private static final Charset ASCII = Charset.forName("ASCII");

    protected final Executor executor;
    protected final WebSocketHandler handler;

    protected CMWebSocketConnection webSocketConnection;

    public NettyWebSocketChannelHandler(Executor executor, WebSocketHandler handler) {
        this.handler = handler;
        this.executor = executor;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    handler.onClose(webSocketConnection);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, final ExceptionEvent e) throws Exception {
        System.out.println("EXCEPTION");
        e.getChannel().close();
        e.getCause().printStackTrace();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest req) throws Exception {
        // Allow only GET .
        if (req.getMethod() != GET) {
            sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        // Serve the WebSocket handshake request.
        if (Values.UPGRADE.equalsIgnoreCase(req.getHeader(CONNECTION))
                && WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {

            // Create the WebSocket handshake response.
            HttpResponse res = new DefaultHttpResponse(HTTP_1_1, new HttpResponseStatus(101,
                    "Web Socket Protocol Handshake"));
            res.addHeader(Names.UPGRADE, WEBSOCKET);
            res.addHeader(CONNECTION, Values.UPGRADE);

            prepareConnection(req, res, ctx);

            try {
                handler.onOpen(this.webSocketConnection);
            } catch (Exception e) {
                // TODO
                e.printStackTrace();
            }
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, final WebSocketFrame frame) {
        try {
            if (frame instanceof Pong) {
                handler.onPong(webSocketConnection, frame.getTextData());
            } else {
                if (frame.isText()) {
                    handler.onMessage(webSocketConnection, frame.getTextData());
                } else {
                    handler.onMessage(webSocketConnection, frame.getBinaryData().array());
                }
            }
        } catch (Throwable t) {
            // TODO
            t.printStackTrace();
        }
    }

    private void prepareConnection(HttpRequest req, HttpResponse res, ChannelHandlerContext ctx) {
        this.webSocketConnection = new CMWebSocketConnection(ctx.getChannel(),
                new ClientFailoverDeliverer());

        if (isHybi10WebSocketRequest(req)) {
            this.webSocketConnection.setVersion(WebSocketConnection.Version.HYBI_10);
            upgradeResponseHybi10(req, res);
            ctx.getChannel().write(res);
            adjustPipelineToHybi(ctx);
        } else if (isHixie76WebSocketRequest(req)) {
            this.webSocketConnection.setVersion(WebSocketConnection.Version.HIXIE_76);
            upgradeResponseHixie76(req, res);
            ctx.getChannel().write(res);
            adjustPipelineToHixie(ctx);
        } else {
            this.webSocketConnection.setVersion(WebSocketConnection.Version.HIXIE_75);
            upgradeResponseHixie75(req, res);
            ctx.getChannel().write(res);
            adjustPipelineToHixie(ctx);
        }
    }

    private void adjustPipelineToHixie(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getChannel().getPipeline();
        p.remove("aggregator");
        p.replace("decoder", "wsdecoder", new WebSocketFrameDecoder());
        p.replace("handler", "wshandler", this);
        p.replace("encoder", "wsencoder", new WebSocketFrameEncoder());
    }

    private void adjustPipelineToHybi(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getChannel().getPipeline();
        p.remove("aggregator");
        p.replace("decoder", "wsdecoder", new Hybi10WebSocketFrameDecoder());
        p.replace("handler", "wshandler", this);
        p.replace("encoder", "wsencoder", new Hybi10WebSocketFrameEncoder());
    }

    private boolean isHybi10WebSocketRequest(HttpRequest req) {
        return req.containsHeader("Sec-WebSocket-Version");
    }

    private boolean isHixie76WebSocketRequest(HttpRequest req) {
        return req.containsHeader(SEC_WEBSOCKET_KEY1) && req.containsHeader(SEC_WEBSOCKET_KEY2);
    }

    private static synchronized String generateAccept(String key) {
        String s = key + ACCEPT_GUID;
        byte[] b = SHA_1.digest(s.getBytes(ASCII));
        return encoder.encode(b);
    }

    private void upgradeResponseHybi10(HttpRequest req, HttpResponse res) {
        String version = req.getHeader("Sec-WebSocket-Version");
        if (!"8".equals(version)) {
            res.setStatus(HttpResponseStatus.UPGRADE_REQUIRED);
            res.setHeader("Sec-WebSocket-Version", "8");
            return;
        }

        String key = req.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            res.setStatus(HttpResponseStatus.BAD_REQUEST);
            return;
        }

        String accept = generateAccept(key);

        res.setStatus(new HttpResponseStatus(101, "Switching Protocols"));
        res.addHeader(UPGRADE, WEBSOCKET.toLowerCase());
        res.addHeader(CONNECTION, UPGRADE);
        res.addHeader("Sec-WebSocket-Accept", accept);
    }

    private void upgradeResponseHixie76(HttpRequest req, HttpResponse res) {
        res.setStatus(new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
        res.addHeader(UPGRADE, WEBSOCKET);
        res.addHeader(CONNECTION, UPGRADE);
        res.addHeader(SEC_WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
        res.addHeader(SEC_WEBSOCKET_LOCATION, getWebSocketLocation(req));
        String protocol = req.getHeader(SEC_WEBSOCKET_PROTOCOL);
        if (protocol != null) {
            res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol);
        }

        // Calculate the answer of the challenge.
        String key1 = req.getHeader(SEC_WEBSOCKET_KEY1);
        String key2 = req.getHeader(SEC_WEBSOCKET_KEY2);
        int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "")
                .length());
        int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "")
                .length());
        long c = req.getContent().readLong();
        ChannelBuffer input = ChannelBuffers.buffer(16);
        input.writeInt(a);
        input.writeInt(b);
        input.writeLong(c);
        try {
            ChannelBuffer output = ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5")
                    .digest(input.array()));
            res.setContent(output);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void upgradeResponseHixie75(HttpRequest req, HttpResponse res) {
        res.setStatus(new HttpResponseStatus(101, "Web Socket Protocol Handshake"));
        res.addHeader(UPGRADE, WEBSOCKET);
        res.addHeader(CONNECTION, HttpHeaders.Values.UPGRADE);
        res.addHeader(WEBSOCKET_ORIGIN, req.getHeader(ORIGIN));
        res.addHeader(WEBSOCKET_LOCATION, getWebSocketLocation(req));
        String protocol = req.getHeader(WEBSOCKET_PROTOCOL);
        if (protocol != null) {
            res.addHeader(WEBSOCKET_PROTOCOL, protocol);
        }
    }

    private String getWebSocketLocation(HttpRequest req) {
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + req.getUri();
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(),
                    CharsetUtil.UTF_8));
            setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.getChannel().write(res);
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
