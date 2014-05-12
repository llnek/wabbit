/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/



package com.zotohlabs.mock.jms;

import javax.jms.*;

/**
 * @author kenl
 *
 */
public class MockQueueConnection implements QueueConnection {

  private volatile boolean _active=false;
  private String _user;
  private String _pwd;

  public MockQueueConnection( String user, String pwd) {
    _user = user;
    _pwd= pwd;
  }

  public MockQueueConnection()  {
    this("","");
    _active=true;
  }

  public void close() {
    stop();
  }

  public ConnectionConsumer createConnectionConsumer(Destination d,
      String a1, ServerSessionPool p, int a3) {
    return null;
  }

  public ConnectionConsumer createDurableConnectionConsumer(Topic t,
      String a1 , String a2 , ServerSessionPool p, int a4 ) {
    return null;
  }

  public Session createSession(boolean b, int a) { return null; }

  public String getClientID() { return ""; }

  public ExceptionListener getExceptionListener() { return null; }

  public ConnectionMetaData getMetaData() { return null; }

  public void setClientID(String a ) {}

  public void setExceptionListener(ExceptionListener e) {}

  public void start() {
    _active=true;
  }

  public void stop() {
    _active=false;
  }

  /**
   * @return
   */
  public boolean isActive() { return _active; }

  public ConnectionConsumer createConnectionConsumer(Queue q, String a1, ServerSessionPool p,
      int a3) {
    return null;
  }

  public QueueSession createQueueSession(boolean tx, int ack) {
    QueueSession s= new MockQueueSession(this, tx, ack);
    s.run() ;
    return s;
  }

}