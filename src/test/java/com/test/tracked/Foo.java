package com.test.tracked;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by olmartin on 2014-11-05.
 */
public class Foo extends Bar{

  public void foo() {
    try {
      System.out.println("Foo call bar :"+super.bar("world"));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      e.printStackTrace();
    }
  }


}
