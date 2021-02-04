package com.benefitj.tcpproxy.server;

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
@ConfigurationProperties(prefix = "tcp.proxy")
public class TcpConfig {
  /**
   * 本地监听端口
   */
  private Integer port;
  /**
   * 远程主机地址，比如：192.168.1.100:8080
   */
  private String[] remotes;
  /**
   * 写入超时时间
   */
  private Integer writerTimeout = 60;
  /**
   * 读取超时时间
   */
  private Integer readerTimeout = 60;
  /**
   * 是否打印请求日志
   */
  private boolean printRequest = false;
  /**
   * 是否打印响应日志
   */
  private boolean printResponse = false;
  /**
   * 打印请求数据的长度
   */
  private Integer printRequestSize = 30;
  /**
   * 打印响应数据的长度
   */
  private Integer printResponseSize = 30;

  /**
   * 延迟结束，默认5秒
   */
  private Integer delayExit = 5;
  /**
   * 是否自动重连，对于部分连接，重连可能会导致错误
   */
  private Boolean autoReconnect = false;
  /**
   * 自动重连的时间
   */
  private Integer reconnectDelay = 3;

}
