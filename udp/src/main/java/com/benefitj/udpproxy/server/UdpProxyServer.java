package com.benefitj.udpproxy.server;

import com.benefitj.core.HexUtils;
import com.benefitj.netty.handler.*;
import com.benefitj.netty.log.Log4jNettyLogger;
import com.benefitj.netty.log.NettyLogger;
import com.benefitj.netty.server.UdpNettyServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
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
import java.util.stream.Collectors;

/**
 * UDP服务端
 */
@Component
public class UdpProxyServer extends UdpNettyServer {
  static {
    NettyLogger.INSTANCE.setLogger(new Log4jNettyLogger());
  }

  private UdpConfig conf;
  /**
   * 远程主机地址
   */
  private final List<InetSocketAddress> remoteServers;
  /**
   * 客户端
   */
  private final AttributeKey<List<UdpClient>> clientsKey = AttributeKey.valueOf("clientsKey");
  private final AttributeKey<EventLoopGroup> groupKey = AttributeKey.valueOf("groupKey");

  @Autowired
  public UdpProxyServer(UdpConfig conf) {
    this.conf = conf;
    this.remoteServers = Collections.synchronizedList(Arrays.stream(getConf().getRemotes())
        .filter(StringUtils::isNotBlank)
        .map(s -> s.split(":"))
        .map(split -> new InetSocketAddress(split[0], Integer.parseInt(split[1])))
        .collect(Collectors.toList()));
    // 超时下线
    this.readerTimeout(conf.getReaderTimeout());
    this.writerTimeout(conf.getWriterTimeout());
  }

  @Override
  public UdpNettyServer useDefaultConfig() {
    this.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
            .addLast(ActiveChangeChannelHandler.newHandler((handler, ctx, state) -> {
              //log.info("udp active state change: {}, remote: {}", state, ctx.channel().remoteAddress());
              if (state == ActiveState.ACTIVE) {
                onClientChannelActive(ctx.channel());
              } else {
                onClientChannelInactive(ctx.channel());
              }
            }))
            .addLast(BiConsumerInboundHandler.newDatagramHandler((handler, ctx, msg) -> {
              List<UdpClient> clients = ctx.channel().attr(clientsKey).get();
              if (clients != null) {
                clients.forEach(client -> onSendRequest(client.getServeChannel(), handler, ctx, msg));
              } else {
                int size = Math.min(msg.content().readableBytes(), conf.getPrintRequestSize());
                log.warn("udp clients is empty, clientAddr: {}, remotes: {}, data: {}"
                    , ctx.channel().remoteAddress()
                    , conf.getRemotes()
                    , HexUtils.bytesToHex(handler.copyAndReset(msg, size))
                );
              }
            }))
            .addLast(ChannelShutdownEventHandler.INSTANCE)
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
    synchronized (UdpProxyServer.this) {
      if (!realityChannel.hasAttr(clientsKey)) {
        // 创建UDP客户端
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        List<UdpClient> clients = this.remoteServers.stream()
            .map(addr -> (UdpClient) new UdpClient()
                // 处理响应的数据
                .setInboundHandler(BiConsumerInboundHandler.newDatagramHandler(
                    (rhandler, rctx, rmsg) -> onSendResponse(realityChannel, rhandler, rctx, rmsg)))
                .group(group)
                .remoteAddress(addr)
                .start(f ->
                    log.info("udp client shadow started, reality: {}, shadow: {}, success: {}"
                        , realityChannel.remoteAddress(), addr, f.isSuccess())
                )
            )
            .collect(Collectors.toList());
        realityChannel.attr(clientsKey).set(clients);
        realityChannel.attr(groupKey).set(group);
      }
    }
  }

  /**
   * 客户端下线
   *
   * @param realityChannel
   */
  protected void onClientChannelInactive(Channel realityChannel) {
    synchronized (UdpProxyServer.this) {
      List<UdpClient> clients = realityChannel.attr(clientsKey).getAndSet(null);
      if (clients != null) {
        clients.forEach(c -> {
          try {
            c.closeServeChannel();
          } catch (Exception ignore) {
            /* ! */
          } finally {
            log.info("udp client shadow stopped, reality: {}, shadow: {}"
                , realityChannel.remoteAddress(), c.remoteAddress());
          }
        });
        realityChannel.attr(groupKey).getAndSet(null).shutdownGracefully();
      }
    }
  }

  /**
   * 发送请求
   *
   * @param shadowChannel 转发的连接的通道
   * @param handler       处理
   * @param ctx           上下文
   * @param msg           消息
   */
  protected void onSendRequest(Channel shadowChannel,
                               ByteBufCopyInboundHandler<DatagramPacket> handler,
                               ChannelHandlerContext ctx,
                               DatagramPacket msg) {
    InetSocketAddress recipient = (InetSocketAddress) shadowChannel.remoteAddress();
    ByteBuf content = msg.content();
    DatagramPacket packet = new DatagramPacket(content.copy(), recipient);
    if (getConf().isPrintRequest()) {
      int size = Math.min(getConf().getPrintRequestSize(), content.readableBytes());
      byte[] data = handler.copyAndReset(content, size);
      shadowChannel.writeAndFlush(packet).addListener(f ->
          log.info("request reality: {}, shadow: {}, active: {}, data[{}]: {}, success: {}"
              , ctx.channel().remoteAddress()
              , shadowChannel.remoteAddress()
              , shadowChannel.isActive()
              , content.readableBytes()
              , HexUtils.bytesToHex(data)
              , f.isSuccess()
          ));
    } else {
      shadowChannel.writeAndFlush(packet);
    }
  }

  /**
   * 发送到响应
   *
   * @param realityChannel 通道
   * @param handler        处理
   * @param ctx            上下文
   * @param msg            消息
   */
  protected void onSendResponse(Channel realityChannel,
                                ByteBufCopyInboundHandler<DatagramPacket> handler,
                                ChannelHandlerContext ctx,
                                DatagramPacket msg) {
    ByteBuf content = msg.content();
    DatagramPacket packet = new DatagramPacket(content.copy(), (InetSocketAddress) realityChannel.remoteAddress());
    if (getConf().isPrintResponse()) {
      int size = Math.min(getConf().getPrintResponseSize(), content.readableBytes());
      byte[] data = handler.copyAndReset(content, size);
      realityChannel.writeAndFlush(packet).addListener(f ->
          log.info("response reality: {}, shadow: {}, active: {}, data[{}]: {}, success: {}"
              , realityChannel.remoteAddress()
              , ctx.channel().localAddress()
              , realityChannel.isActive()
              , content.readableBytes()
              , HexUtils.bytesToHex(data)
              , f.isSuccess()
          ));
    } else {
      realityChannel.writeAndFlush(packet);
    }
  }

  @Override
  public UdpNettyServer stop(GenericFutureListener<? extends Future<Void>>... listeners) {
    return super.stop(listeners);
  }

  public UdpConfig getConf() {
    return conf;
  }

  public void setConf(UdpConfig conf) {
    this.conf = conf;
  }
}
