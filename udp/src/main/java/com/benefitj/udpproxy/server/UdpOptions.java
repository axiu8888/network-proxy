package com.benefitj.udpproxy.server;

import com.benefitj.proxy.ProxyOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * UDP配置
 */
@Component
@ConfigurationProperties(prefix = "udp")
public class UdpOptions extends ProxyOptions {
}
