package com.benefitj.tcpproxy.server;

import com.benefitj.core.HexUtils;
import com.benefitj.netty.handler.*;
import com.benefitj.netty.log.Log4jNettyLogger;
import com.benefitj.netty.log.NettyLogger;
import com.benefitj.netty.server.TcpNettyServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TCP 服务端
 */
@Component
public class TcpProxyServer extends TcpNettyServer {
  static {
    NettyLogger.INSTANCE.setLogger(new Log4jNettyLogger());
  }

  private TcpConfig conf;
  /**
   * 远程主机地址
   */
  private final List<InetSocketAddress> remoteServers;
  /**
   * 客户端
   */
  private final AttributeKey<List<TcpClient>> clientsKey = AttributeKey.valueOf("clientsKey");

  @Autowired
  public TcpProxyServer(TcpConfig conf) {
    this.conf = conf;
    this.remoteServers = Collections.synchronizedList(Arrays.stream(getConf().getRemotes())
        .filter(StringUtils::isNotBlank)
        .map(s -> s.split(":"))
        .map(split -> new InetSocketAddress(split[0], Integer.parseInt(split[1])))
        .collect(Collectors.toList()));
  }

  @Override
  public TcpNettyServer useDefaultConfig() {
    //this.handler(new LoggingHandler(LogLevel.INFO));
    this.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
            .addLast(ChannelShutdownEventHandler.INSTANCE)
            .addLast(new IdleStateHandler(conf.getReaderTimeout(), conf.getWriterTimeout(), 10, TimeUnit.SECONDS))
            .addLast(ActiveChangeChannelHandler.newHandler((handler, ctx, state) -> {
              //log.info("tcp active state change: {}, remote: {}", state, ctx.channel().remoteAddress());
              if (state == ActiveState.ACTIVE) {
                onClientChannelActive(ctx.channel());
              } else {
                onClientChannelInactive(ctx.channel());
              }
            }))
            .addLast(BiConsumerInboundHandler.newByteBufHandler((handler, ctx, msg) -> {
              List<TcpClient> clients = ctx.channel().attr(clientsKey).get();
              if (clients != null && !clients.isEmpty()) {
                clients.forEach(c -> {
                  if (c.getServeChannel() != null) {
                    onSendRequest(c.getServeChannel(), handler, ctx, msg);
                  }/* else {
                    // ignore, 客户端未连接
                  }*/
                });
              } else {
                int size = Math.min(msg.readableBytes(), conf.getPrintRequestSize());
                log.warn("tcp clients is empty, clientAddr: {}, remotes: {}, data: {}"
                    , ctx.channel().remoteAddress()
                    , conf.getRemotes()
                    , HexUtils.bytesToHex(handler.copyAndReset(msg, size))
                );
              }
            }))
            .addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                cause.printStackTrace();
              }
            })
        ;
      }
    });
    return super.useDefaultConfig();
  }

  /**
   * 客户端上线
   *
   * @param realityChannel
   */
  protected void onClientChannelActive(Channel realityChannel) {
    synchronized (TcpProxyServer.this) {
      if (!realityChannel.hasAttr(clientsKey)) {
        // 创建TCP客户端
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        List<TcpClient> clients = this.remoteServers.stream()
            .map(addr -> (TcpClient) new TcpClient()
                    // 处理响应的数据
                    .setInboundHandler(BiConsumerInboundHandler.newByteBufHandler(
                        (rhandler, rctx, rmsg) -> onSendResponse(realityChannel, rhandler, rctx, rmsg)))
                    .group(group)
                    .remoteAddress(addr)
                    .autoReconnect(conf.getAutoReconnect(), conf.getReconnectDelay(), TimeUnit.SECONDS)
//                .addStartListeners(f -> log.info("tcp client started, remote: {}, success: {}", addr, f.isSuccess()))
//                .addStopListeners(f -> log.info("tcp client stopped, remote: {}, success: {}", addr, f.isSuccess()))
                    .start(f ->
                        log.info("tcp client shadow started, reality: {}, shadow: {}, success: {}"
                            , realityChannel.remoteAddress()
                            , addr
                            , f.isSuccess()
                        ))
            )
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        realityChannel.attr(clientsKey).set(clients);

        if (clients.isEmpty()) {
          group.shutdownGracefully();
        }
      }
    }
  }

  /**
   * 客户端下线
   *
   * @param realityChannel
   */
  protected void onClientChannelInactive(Channel realityChannel) {
    synchronized (TcpProxyServer.this) {
      List<TcpClient> clients = realityChannel.attr(clientsKey).getAndSet(null);
      if (clients != null) {
        clients.forEach(c ->
            c.stop(f ->
                log.info("tcp client shadow stopped, reality: {}, shadow: {}, success: {}"
                    , realityChannel.remoteAddress()
                    , c.remoteAddress()
                    , f.isSuccess())
            )
        );
      }
    }
  }

  /**
   * 发送请求
   *
   * @param shadowChannel 代理连接的通道
   * @param handler       处理
   * @param ctx           上下文
   * @param msg           消息
   */
  protected void onSendRequest(Channel shadowChannel,
                               ByteBufCopyInboundHandler<ByteBuf> handler,
                               ChannelHandlerContext ctx,
                               ByteBuf msg) {
    ByteBuf copy = msg.copy();
    if (getConf().isPrintRequest()) {
      int size = Math.min(getConf().getPrintRequestSize(), msg.readableBytes());
      byte[] data = handler.copyAndReset(copy, size);
      shadowChannel.writeAndFlush(copy).addListener(f ->
          log.info("request reality: {}, shadow: {}, active: {}, data[{}]: {}, success: {}"
              , ctx.channel().remoteAddress()
              , shadowChannel.remoteAddress()
              , shadowChannel.isActive()
              , msg.readableBytes()
              , HexUtils.bytesToHex(data)
              , f.isSuccess()
          ));
    } else {
      shadowChannel.writeAndFlush(copy);
    }
  }

  /**
   * 发送到响应
   *
   * @param realityChannel 远程客户端通道
   * @param handler        处理
   * @param ctx            上下文
   * @param msg            消息
   */
  protected void onSendResponse(Channel realityChannel,
                                ByteBufCopyInboundHandler<ByteBuf> handler,
                                ChannelHandlerContext ctx,
                                ByteBuf msg) {
    ByteBuf copy = msg.copy();
    if (getConf().isPrintResponse()) {
      int size = Math.min(getConf().getPrintResponseSize(), copy.readableBytes());
      byte[] data = handler.copyAndReset(copy, size);
      realityChannel.writeAndFlush(copy).addListener(f ->
          log.info("response reality: {}, shadow: {}, active: {}, data[{}]: {}, success: {}"
              , realityChannel.remoteAddress()
              , ctx.channel().localAddress()
              , realityChannel.isActive()
              , msg.readableBytes()
              , HexUtils.bytesToHex(data)
              , f.isSuccess()
          ));
    } else {
      realityChannel.writeAndFlush(copy);
    }
  }

  @Override
  public TcpNettyServer stop(GenericFutureListener<? extends Future<Void>>... listeners) {
    return super.stop(listeners);
  }

  public TcpConfig getConf() {
    return conf;
  }

  public void setConf(TcpConfig conf) {
    this.conf = conf;
  }


}
