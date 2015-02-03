package com.test.tracked;

/**
 * Created by olmartin on 2014-11-11.
 */
public class AnotherSimpleCall {

  public void anotherSimpleCall() {
    System.out.println("aSimpleCall from "+getClass().getName());
    internallCall();
    anotherInternalCall();
    internallCallLonger();
  }

  public void internallCall() {
    System.out.println("internallCall from "+getClass().getName());
    anotherInternalCall();
  }

  public void internallCallLonger() {
    System.out.println("internallCallLonger from "+getClass().getName());
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void anotherInternalCall() {
    System.out.println("anotherInternalCall from "+getClass().getName());
  }
}
