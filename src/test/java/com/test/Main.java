package com.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import com.test.nottracked.NotTrackedCalled;
import com.test.tracked.AnotherSimpleCall;
import com.test.tracked.AsynchronousCall;
import com.test.tracked.SimpleCall;
import com.test.tracked.SuperCallToIgnore;

/**
 * Created by olmartin on 2015-02-02.
 */
public class Main {

  public static void main(String[] args) {
    new SimpleCall().aSimpleCall();
    new AnotherSimpleCall().anotherSimpleCall();
    try {
      new AsynchronousCall().callFuture("test");
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (TimeoutException e) {
      e.printStackTrace();
    }
    new NotTrackedCalled().call();
    new SuperCallToIgnore().call();
  }
}
