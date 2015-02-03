package com.test.tracked;

/**
 * Created by olmartin on 2015-02-02.
 */
public class SuperCallToIgnore {
  public void call() {
    System.out.println("call from "+getClass().getName());

    SimpleCall a = new SimpleCall();
    a.aSimpleCall();
  }
}
