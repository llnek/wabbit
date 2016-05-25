/* Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package czlab.skaro.mvc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedInput;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;


/**
 * @author kenl
 */
public class HTTPRangeInput implements ChunkedInput<ByteBuf> {

  protected static final String DEF_BD= "21458390-ebd6-11e4-b80c-0800200c9a66";

  public static final Logger TLOG= getLogger(lookup().lookupClass());

  private boolean _bad = false;
  private ByteRange[] _ranges;
  private String _cType;
  private int _current= 0;
  private long _clen= 0L;

  private RandomAccessFile _file;

  public HTTPRangeInput(RandomAccessFile file, String cType, String range) {
    _cType= cType;
    _file=file;
    init(range);
  }

  public static boolean isAcceptable(String range) {
    return range != null && range.length() > 0;
  }

  public long process(HttpResponse rsp) {
    HttpHeaders.addHeader(rsp, "accept-ranges", "bytes");
    long last= _clen > 0 ? _clen-1 : 0;

    if (_bad) {
      rsp.setStatus(HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
      HttpHeaders.setHeader(rsp, "content-range",
          "bytes " + "0-" + last + "/" + _clen);
      HttpHeaders.setHeader(rsp, "content-length", "0");
      return 0L;
    }

    rsp.setStatus(HttpResponseStatus.PARTIAL_CONTENT);
    if(_ranges.length == 1) {
      ByteRange r= _ranges[0];
      HttpHeaders.setHeader(rsp, "content-range",
          "bytes " + r.start() + "-" + r.end() + "/" + _clen);
    } else {
      HttpHeaders.setHeader(rsp, "content-type", "multipart/byteranges; boundary="+ DEF_BD);
    }
    long len=0L;
    for (int n=0; n < _ranges.length; ++n) {
      len += _ranges[n].calcTotalSize();
    }
    HttpHeaders.setHeader(rsp, "content-length", Long.toString(len));
    return len;
  }

  public ByteBuf readChunk(ChannelHandlerContext ctx) throws IOException {

    byte[] buff= new byte[ 8192];
    int mlen= buff.length;
    int count = 0;

    while ( count < mlen && _current < _ranges.length &&
      _ranges[_current] != null) {
      if ( _ranges[_current].remaining() > 0) {
        count += _ranges[_current].pack(buff, count);
      } else {
        _current += 1;
      }
    }
    return (count == 0) ? null : Unpooled.copiedBuffer(buff);
  }

  private boolean hasNext() {
    return _current < _ranges.length && _ranges[_current].remaining() > 0;
  }

  public boolean isEndOfInput() { return !hasNext(); }

  public void close() {
    try {
      _file.close();
    } catch (IOException e) {
      TLOG.warn("",e);
    }
  }

  private void init(String s /* range */) {
    try {
      //val ranges = mutable.ArrayBuffer[ (Long,Long) ]()
      // strip off "bytes="
      int pos= s.indexOf("bytes=");
      String[] rvs= (pos < 0) ?  null : s.substring(pos+6).trim().split(",");
      List<Long[]> ranges= new ArrayList<>();
      _clen= _file.length();
      long last= _clen-1;

      if (rvs != null) for (int n=0; n < rvs.length; ++n) {
        String rs= rvs[n].trim();
        long start=0L;
        long end=0L;
        if (rs.startsWith("-")) {
          start = last - Long.valueOf(rs.substring(1).trim() );
          end = last;
        } else {
          String[] range = rs.split("-");
          start = Long.valueOf(range[0].trim() );
          end = (range.length > 1)
            ? Long.valueOf(range[1].trim()) : last;
        }
        if (end > last) { end = last; }
        if (start <= end) { ranges.add( new Long[]{ start, end } ); }
      }

      List<ByteRange> bytes = new ArrayList<>();
      List<Long[]> nrs = normalize(ranges);
      for (Long[] rr : nrs) {
        bytes.add( new ByteRange( _file, nrs.size() > 1, rr[0], rr[1], _cType) );
      }
      _ranges = bytes.toArray( new ByteRange[0]) ;
      _bad = (_ranges.length == 0);
    }
    catch (Throwable e) {
      _bad = true;
      TLOG.error("", e);
    }
  }

  private boolean maybeIntersect(Long[] r1, Long[] r2) {
    return  ( r1[0] >= r2[0] && r1[0] <= r2[1] ) ||
               ( r1[1] >= r2[0] && r1[0] <= r2[1] );
  }

  private Long[] merge(Long[] r1, Long[] r2) {
    return new Long[] { (r1[0] < r2[0]) ?  r1[0] : r2[0],
                        (r1[1] > r2[1]) ? r1[1] : r2[1] } ;
  }

  private List<Long[]> normalize( List<Long[]> chunks ) {

    if (chunks.size() == 0) { return new ArrayList<Long[]>(); }

    Long[][] sortedChunks = chunks.toArray(new Long[][] {} );
    List<Long[]> rc= new ArrayList<>();

    Arrays.sort(sortedChunks, new Comparator< Long[]> () {
        public int compare( Long[] t1, Long[] t2) {
          return t1[0].compareTo(t2[0]);
        }
    });

    rc.add(sortedChunks[0] );
    for (int n = 1; n < sortedChunks.length; ++n) {
        Long[] r1 = rc.get(rc.size() - 1);
        Long[] c1 = sortedChunks[n];
        if ( maybeIntersect(c1, r1)) {
          rc.set(rc.size() - 1, merge(c1, r1));
        } else {
          rc.add(c1);
        }
    }
    return rc;
  }

}

