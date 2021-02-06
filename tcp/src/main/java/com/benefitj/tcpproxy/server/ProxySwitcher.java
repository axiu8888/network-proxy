package com.benefitj.tcpproxy.server;

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
  private TcpOptions options;
  @Autowired
  private TcpProxyServer server;

  @EventListener
  public void onAppStart(ApplicationReadyEvent event) {
    try {
      Integer port = options.getPort();
      if (port == null) {
        throw new IllegalStateException("本地监听端口不能为空!");
      }

      String[] remotes = options.getRemotes();
      if (remotes == null || remotes.length < 1) {
        throw new IllegalStateException("远程主机地址不能为空!");
      }

      server.localAddress(port);
      server.start(f ->
          log.info("tcp proxy started, local port: {}, remotes: {}, success: {}"
              , options.getPort()
              , Arrays.toString(remotes)
              , f.isSuccess()
          )
      );
    } catch (Exception e) {
      log.error("throws: " + e.getMessage(), e);
      // 5秒后停止
      EventLoop.single().schedule(() ->
          System.exit(0), 5, TimeUnit.SECONDS);
    }
  }

  @EventListener
  public void onAppStop(ContextClosedEvent event) {
    try {
      server.stop(f ->
          log.info("tcp proxy stopped, local port: {}, remotes: {}, success: {}"
              , options.getPort()
              , Arrays.toString(options.getRemotes())
              , f.isSuccess()
          )
      );
    } catch (Exception e) {
      log.error("throws: " + e.getMessage(), e);
      System.exit(0);
    }
  }
}

