package com.benefitj.tcpudpproxy.server;

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
@ConfigurationProperties(prefix = "tcp-udp")
public class TcpUdpOptions extends ProxyOptions {
}
