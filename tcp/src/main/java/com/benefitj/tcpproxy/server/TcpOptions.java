package com.benefitj.tcpproxy.server;

import com.benefitj.proxy.ProxyOptions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TCP配置
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "tcp")
public class TcpOptions extends ProxyOptions {
  /**
   * 是否自动重连，对于部分连接，重连可能会导致错误
   */
  private boolean autoReconnect = false;
  /**
   * 自动重连的时间
   */
  private Integer reconnectDelay = 3;
  /**
   * 是否快速失败
   */
  private boolean fastFailover = false;

}
