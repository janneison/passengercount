package com.extreme.passenger.infrastructure.async;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.Data;

@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "app.async")
@Data
class AsyncProps {
  private int corePoolSize = 2;
  private int maxPoolSize = 2;
  private int queueCapacity = 500;
}