package com.benefitj.udpproxy.server;

import com.benefitj.core.EventLoop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ProxySwitcher {

  @Autowired
  private UdpConfig conf;
  @Autowired
  private UdpProxyServer server;

  @EventListener
  public void onAppStart(ApplicationReadyEvent event) {
    try {
      Integer port = conf.getPort();
      if (port == null) {
        throw new IllegalStateException("本地监听端口不能为空!");
      }

      String[] remotes = conf.getRemotes();
      if (remotes == null || remotes.length < 1) {
        throw new IllegalStateException("远程主机地址不能为空!");
      }

      server.localAddress(port);
      server.start(f ->
          log.info("udp proxy started, local port: {}, remotes: {}, success: {}"
              , conf.getPort()
              , Arrays.toString(remotes)
              , f.isSuccess()
          )
      );
    } catch (Exception e) {
      log.error("throws: " + e.getMessage(), e);
      // 10秒后停止
      EventLoop.single().schedule(() ->
          System.exit(0), 5, TimeUnit.SECONDS);
    }
  }

  @EventListener
  public void onAppStop(ContextClosedEvent event) {
    try {
      server.stop(f ->
          log.info("udp proxy stopped, local port: {}, remotes: {}, success: {}"
              , conf.getPort()
              , Arrays.toString(conf.getRemotes())
              , f.isSuccess()
          )
      );
    } catch (Exception e) {
      log.error("throws: " + e.getMessage(), e);
      System.exit(0);
    }
  }
}

