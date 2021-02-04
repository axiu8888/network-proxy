package com.benefitj.tcpproxy.server;

import com.benefitj.netty.client.TcpNettyClient;
import com.benefitj.netty.handler.ActiveChangeChannelHandler;
import com.benefitj.netty.handler.ByteBufCopyInboundHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;

import java.net.PortUnreachableException;

/**
 * TCP客户端
 */
public class TcpClient extends TcpNettyClient {

  private ByteBufCopyInboundHandler<ByteBuf> inboundHandler;

  public TcpClient() {
  }

  public TcpClient setInboundHandler(ByteBufCopyInboundHandler<ByteBuf> inboundHandler) {
    this.inboundHandler = inboundHandler;
    return this;
  }

  public ByteBufCopyInboundHandler<ByteBuf> getInboundHandler() {
    return inboundHandler;
  }

  @Override
  public TcpNettyClient useDefaultConfig() {
    this.useLinuxNativeEpoll(false);
    // 监听
    this.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
            .addLast(ActiveChangeChannelHandler.newHandler((handler, ctx, state) ->
                log.info("tcp client active change, state: {}, remote: {}", state, ch.remoteAddress())))
            .addLast(getInboundHandler())
            .addLast(new ChannelInboundHandlerAdapter() {
              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause instanceof PortUnreachableException) {
                  Channel ch = ctx.channel();
                  log.error("PortUnreachableException: " + ch.remoteAddress() + ", active: " + ch.isActive());
                } else {
                  log.warn("exceptionCaught: {}", cause.getMessage());
                  //ctx.fireExceptionCaught(cause);
                }
              }
            })
        ;
      }
    });
    return super.useDefaultConfig();
  }

}
