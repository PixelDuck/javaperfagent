package com.test.tracked;

import com.test.nottracked.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by olmartin on 2014-11-05.
 */
public class Bar {

  private final ExecutorService executorService;
  Test test = new Test();

  public Bar() {
    executorService = Executors.newFixedThreadPool(1);
  }

  public String bar(String who) throws InterruptedException, ExecutionException, TimeoutException {
    test.foo();
    return callFuture(who);
  }

  public String callFuture(String who) throws InterruptedException, ExecutionException, TimeoutException {
    return executorService.submit(new ACallable(who)).get(1000L, TimeUnit.MILLISECONDS);

  }
}
