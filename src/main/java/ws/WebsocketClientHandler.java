package ws;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WebsocketClientHandler extends SimpleChannelInboundHandler<Object> {

    private final WebsocketClient.Handler handler;
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private final WebsocketClient client;
    private long lastTime = System.currentTimeMillis();
    private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private Channel channel;

    public WebsocketClientHandler(final WebsocketClient.Handler handler, final WebsocketClient client, final WebSocketClientHandshaker handshaker) {
        this.handler = handler;
        this.client = client;
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
        channel = ctx.channel();
        service.scheduleAtFixedRate(() -> {
            try {
                if (System.currentTimeMillis() - lastTime > 30000) {
                    if (ctx.channel().isOpen()) {
                        ctx.channel().close();
                    }
                } else if (System.currentTimeMillis() - lastTime > 10000) {
                    ctx.channel().writeAndFlush(new PingWebSocketFrame());
                }
            } catch (final Exception ex) {
            }
        }, 1L, 5L, TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        handler.onClose();
        restart(ctx);
    }

    private void restart(final ChannelHandlerContext ctx) {
        service.shutdownNow();
        final EventLoop eventLoop = ctx.channel().eventLoop();
        eventLoop.schedule(() -> client.start(), 2L, TimeUnit.SECONDS);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        handler.onError(cause);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
        lastTime = System.currentTimeMillis();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(channel, (FullHttpResponse) msg);
                handshakeFuture.setSuccess();
                handler.onConnected(client);
            } catch (final WebSocketHandshakeException e) {
                final FullHttpResponse response = (FullHttpResponse) msg;
                handshakeFuture.setFailure(e);
            }
            return;
        }
        if (msg instanceof FullHttpResponse) {
            final FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException("Unexpected FullHttpResponse (getStatus=" + response.status() + ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }
        final WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            final String text = ((TextWebSocketFrame) frame).text();
            handler.onTextMessage(text.getBytes());
        } else if (frame instanceof BinaryWebSocketFrame) {
            handler.onBinaryMessage(new ByteBufInputStream(frame.content()));
        } else if (frame instanceof PingWebSocketFrame) {
            channel.writeAndFlush(new PongWebSocketFrame());
        } else if (frame instanceof PongWebSocketFrame) {
        } else if (frame instanceof CloseWebSocketFrame) {
        }
    }

}
