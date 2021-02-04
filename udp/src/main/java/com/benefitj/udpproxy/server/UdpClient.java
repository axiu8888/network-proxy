package com.benefitj.udpproxy.server;

import com.benefitj.netty.client.UdpNettyClient;
import com.benefitj.netty.handler.ActiveChangeChannelHandler;
import com.benefitj.netty.handler.ByteBufCopyInboundHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.net.PortUnreachableException;

/**
 * UDP客户端
 */
public class UdpClient extends UdpNettyClient {

  private ByteBufCopyInboundHandler<DatagramPacket> inboundHandler;

  public UdpClient() {
  }

  public UdpClient setInboundHandler(ByteBufCopyInboundHandler<DatagramPacket> inboundHandler) {
    this.inboundHandler = inboundHandler;
    return this;
  }

  public ByteBufCopyInboundHandler<DatagramPacket> getInboundHandler() {
    return inboundHandler;
  }

  @Override
  public UdpNettyClient useDefaultConfig() {
    this.useLinuxNativeEpoll(false);
    this.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
            .addLast(ActiveChangeChannelHandler.newHandler((handler, ctx, state) ->
                log.info("udp client active state change: {}, remote: {}", state, ch.remoteAddress())))
            .addLast(getInboundHandler())
            .addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof PortUnreachableException) {
                  log.error("PortUnreachableException: " + ctx.channel().remoteAddress() + ", active: " + ctx.channel().isActive());
                } else {
                  ctx.fireExceptionCaught(cause);
                }
              }
            })
        ;
      }
    });
    return super.useDefaultConfig();
  }

  @Override
  protected ChannelFuture startOnly(Bootstrap bootstrap,
                                    GenericFutureListener<? extends Future<Void>>... listeners) {
    return bootstrap.connect().syncUninterruptibly().addListeners(listeners);
  }

  @Override
  public UdpNettyClient stop(GenericFutureListener<? extends Future<Void>>... listeners) {
    return super.stop(listeners);
  }

}
