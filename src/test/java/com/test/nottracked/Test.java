package com.test.nottracked;

import com.test.tracked.Foo;

/**
 * Created by olmartin on 2014-11-05.
 */
public class Test {
  public String foo() {
    return "Should not be monitored";
  }


  public static void main(String[] args) {
    Foo foo =
        new Foo();
    foo.foo();
    System.out.println("finish");
    System.exit(0);
  }
}
