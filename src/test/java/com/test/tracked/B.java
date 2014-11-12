package com.test.tracked;

/**
 * Created by olmartin on 2014-11-11.
 */
public class B {

  public void a() {
    System.out.println("a");
    aa();
    ab();
  }

  public void aa() {
    System.out.println("aa");
    aaa();
    aab();
  }

  public void aaa() {
    System.out.println("aaa");
  }

  public void aab() {
    System.out.println("aab");
  }

  public void ab() {
    System.out.println("ab");
    ACallable aCallable = new ACallable("test");
    try {
      aCallable.call();
    } catch (Exception e) {

    }
  }

  public static void main(String[] args) {
    new B().a();
  }
}
