package com.test.tracked;

import java.util.concurrent.Callable;

/**
 *  A callable test class.
 */
public class ACallable implements Callable<String> {
  private final String who;

  public ACallable(String who) {
    this.who = who;
  }

  @Override public String call() throws Exception {
    return "Hello "+who+" from callable";
  }
}
