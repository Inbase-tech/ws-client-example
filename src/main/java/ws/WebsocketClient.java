package ws;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WebsocketClient {

    private SslContext sslCtx;
    private URI uri;
    private String host;
    private int port;
    private final Handler handler;
    private Channel channel;
    private final Map<String, String> header = new HashMap<>();
    private final EventLoopGroup group = new EpollEventLoopGroup();
    private int retryCount = 0;

    public WebsocketClient(final String endpoint, final Handler handler) throws URISyntaxException, SSLException {
        this.handler = handler;
        setEndPoint(endpoint);
    }

    public WebsocketClient(final Handler handler) {
        this.handler = handler;
    }

    public void setEndPoint(final String endpoint) throws URISyntaxException, SSLException {
        uri = new URI(endpoint);
        final String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        if (ssl) {
            sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            return;
        }
    }

    public WebsocketClient header(final String key, final String value) {
        header.put(key, value);
        return this;
    }

    public void keyManagerFactory(final KeyManagerFactory keyManagerFactory) throws SSLException {
        sslCtx = SslContextBuilder.forClient().keyManager(keyManagerFactory).build();
    }

    public void beforeStart() {
        // leave for
    }

    public void start() {
        beforeStart();
        final HttpHeaders httpHeaders = new DefaultHttpHeaders();
        header.forEach((k, v) -> {
            httpHeaders.add(k, v);
        });
        final WebsocketClientHandler websocketClientHandler =
                new WebsocketClientHandler(handler, this, WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, httpHeaders, 65536 * 1024));
        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true).option(ChannelOption.TCP_NODELAY, true).group(group).channel(EpollSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) throws Exception {
                        final ChannelPipeline p = ch.pipeline();
                        if (sslCtx != null) {
                            p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                        }
                        p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), websocketClientHandler);
                    }
                });

        try {
            bootstrap.connect(host, port).addListener((ChannelFutureListener) channelFuture -> {
                if (!channelFuture.isSuccess()) {
                    retryCount++;
                    final EventLoop loop = channelFuture.channel().eventLoop();
                    loop.schedule(() -> start(), (long) Math.pow(2, retryCount), TimeUnit.SECONDS);
                } else {
                    retryCount = 0;
                    channel = channelFuture.channel();
                }
            }).sync();
            websocketClientHandler.handshakeFuture().sync();
        } catch (final InterruptedException e) {
        }
    }

    public void sendString(final String message) {
        channel.writeAndFlush(new TextWebSocketFrame(message));
    }

    public void ping() {
        channel.writeAndFlush(new PingWebSocketFrame());
    }

    public interface Handler {
        void onConnected(WebsocketClient client);

        void onClose();

        void onError(Throwable cause);

        void onTextMessage(byte[] bytes);

        void onBinaryMessage(InputStream inputStream);
    }

}
