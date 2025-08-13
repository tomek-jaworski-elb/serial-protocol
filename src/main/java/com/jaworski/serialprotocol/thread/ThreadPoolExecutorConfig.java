package com.jaworski.serialprotocol.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolExecutorConfig {

  @Value("${threads.core-pool-size}")
  private int corePoolSize;

  @Value("${threads.max-pool-size}")
  private int maxPoolSize;

  @Value("${threads.queue-capacity}")
  private int queueCapacity;
  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolExecutorConfig.class);

  @Bean(name = "customThreadPoolExecutor")
  public ThreadPoolExecutor executor() {
    LOG.info("Creating custom thread pool executor with corePoolSize: {}, maxPoolSize: {}, queueCapacity: {}", this.corePoolSize, this.maxPoolSize, this.queueCapacity);
    return new ThreadPoolExecutor(
            this.corePoolSize,
            this.maxPoolSize,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(this.queueCapacity),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
  }
}
