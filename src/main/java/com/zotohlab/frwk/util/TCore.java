/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.frwk.util;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;


/**
 * @author kenl
 */
public class TCore implements RejectedExecutionHandler {

  private static final Logger _log = getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  //private val serialVersionUID = 404521678153694367L

  private ExecutorService _scd;
  private boolean _trace;
  private boolean _paused;
  private String _id ="";
  private int _tds = 4;

  public TCore (String id, int tds, boolean traceable) {
    _tds= Math.max(1,tds);
    _id=id;
    _trace=traceable;
    _paused=true;
  }

  public TCore (String id, int tds) {
    this(id, tds, true);
  }

  public void start() {
    activate();
    _paused=false;
  }

  public void stop() {
    _paused=true;
  }

  public void dispose() {
    stop();
    //_scd.shutdownNow()
    _scd.shutdown();
    if (_trace) {
      tlog().debug("Core \"{}\"  disposed and shut down." , _id );
    }
  }

  public void schedule(Runnable work) {
    if (! _paused) {
      _scd.execute(work);
    }
  }

  public void rejectedExecution(Runnable r, ThreadPoolExecutor x) {
    //TODO: deal with too much work for the core...
    tlog().error("\"{}\" rejected work - threads/queue are max'ed out" , _id);
  }

  public String toString() {
    return "Core \"" + _id + "\" with threads = " + _tds;
  }

  private void activate() {
//    _scd= Executors.newCachedThreadPool( new TFac(_id) )
    _scd= new ThreadPoolExecutor( _tds, _tds, 5000L,
        TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
        new BasicThreadFactory.Builder()
        .priority(Thread.NORM_PRIORITY)
        .namingPattern(_id + "-%d")
        .daemon(false)
        .build(),
        this );
    if (_trace) {
      tlog().debug("Core \"{}\" activated with threads = {}" , _id , "" + _tds, "");
    }
  }

}

