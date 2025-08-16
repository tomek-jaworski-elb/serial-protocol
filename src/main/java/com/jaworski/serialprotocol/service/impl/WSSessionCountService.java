package com.jaworski.serialprotocol.service.impl;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WSSessionCountService {

  private final AtomicInteger counter = new AtomicInteger(0);

  public int getCounter() {
    return counter.get();
  }

  public void setCounter(int size) {
    counter.set(size);
  }
}
