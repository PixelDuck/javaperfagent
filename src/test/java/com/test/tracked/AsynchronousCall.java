package com.test.tracked;

import com.test.nottracked.NotTrackedCalled;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by olmartin on 2014-11-05.
 */
public class AsynchronousCall {

  private final ExecutorService executorService;
  NotTrackedCalled test = new NotTrackedCalled();

  public AsynchronousCall() {
    executorService = Executors.newFixedThreadPool(1);
  }

  public String callFuture(String who) throws InterruptedException, ExecutionException, TimeoutException {
    System.out.println("callFuture from "+getClass().getName());
    return executorService.submit(new ACallable(who)).get(1000L, TimeUnit.MILLISECONDS);

  }
}
