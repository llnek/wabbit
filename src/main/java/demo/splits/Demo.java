// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package demo.splits;

import static com.zotohlab.wflow.PTask.PTaskWrapper;
import static java.lang.System.out;

import com.zotohlab.wflow.Activity;
import com.zotohlab.server.WorkFlow;
import com.zotohlab.wflow.Split;

/**
 * @author kenl
 *
    parent(s1) --> split&nowait
                   |-------------> child(s1)----> split&wait --> grand-child
                   |                              |                    |
                   |                              |<-------------------+
                   |                              |---> child(s2) -------> end
                   |
                   |-------> parent(s2)----> end
 */
public class Demo implements WorkFlow {

    // split but no wait
    // parent continues;

  public Activity startsWith() {

    Activity a0= PTaskWrapper( (c,j) -> {
      out.println("I am the *Parent*");
      out.println("I am programmed to fork off a parallel child process, " +
        "and continue my business.");
      return null;
    });
    Activity a1= Split.fork( PTaskWrapper( (c,j) -> {
      out.println("*Child*: will create my own child (blocking)");
      j.setv("rhs", 60);
      j.setv("lhs", 5);

      Split s1= Split.applyAnd(PTaskWrapper( (c1,j1) -> {
        out.println("*Child*: the result for (5 * 60) according to my own child is = "  +
                    j1.getv("result"));
        out.println("*Child*: done.");
        return null;
      }));

      // split & wait
      return s1.include(PTaskWrapper( (c2, j2) -> {
        out.println("*Child->child*: taking some time to do this task... ( ~ 6secs)");
        for (int i= 1; i < 7; ++i) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          out.print("...");
        }
        out.println("");
        out.println("*Child->child*: returning result back to *Child*.");
        j2.setv("result",  (Integer) j2.getv("rhs") * (Integer) j2.getv("lhs"));
        out.println("*Child->child*: done.");
        return null;
      }));
    }));

    Activity a2= PTaskWrapper( (c,j) -> {
      out.println("*Parent*: after fork, continue to calculate fib(6)...");
      StringBuilder b=new StringBuilder("*Parent*: ");
      for (int i=1; i < 7; ++i) {
        b.append( fib(i) + " ");
      }
      out.println(b.toString()  + "\n" + "*Parent*: done.");
      return null;
    });

    return a0.chain(a1).chain(a2);
  }

  private int fib(int n) {
    return (n <3) ? 1 : fib(n-2) + fib(n-1);
  }

}

