package com.benefitj.udpproxy.server;

import com.benefitj.netty.client.UdpNettyClient;
import com.benefitj.netty.handler.ActiveChangeChannelHandler;
import com.benefitj.netty.handler.ByteBufCopyInboundHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramPacket;

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

}
