
package com.zotohlabs.frwk.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public enum ServerSide {
;
  private static Logger _log = LoggerFactory.getLogger(ServerSide.class);
  public static Logger tlog() { return _log; }

  public static ServerBootstrap initServerSide(PipelineConfigurator cfg, JSONObject options) {
    ServerBootstrap bs= new ServerBootstrap();
    bs.group( new NioEventLoopGroup(), new NioEventLoopGroup() );
    bs.channel(NioServerSocketChannel.class);
    bs.option(ChannelOption.SO_REUSEADDR,true);
    bs.option(ChannelOption.SO_BACKLOG,100);
    bs.childOption(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
    bs.childOption(ChannelOption.TCP_NODELAY,true);
    bs.childHandler( makeChannelInitor(cfg, options));
    return bs;
  }

  private static ChannelHandler makeChannelInitor(PipelineConfigurator cfg, JSONObject options) {
    return new ChannelInitializer() {
      public void initChannel(Channel ch) {
        cfg.handle(ch.pipeline(), options);
      }
    };
  }

  public static Channel start(ServerBootstrap bs, String host, int port) throws IOException {
    InetAddress ip = host==null ? InetAddress.getLocalHost()
                                : InetAddress.getByName( host);
    Channel ch= null;
    try {
      ch = bs.bind( new InetSocketAddress(ip, port)).sync().channel();
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
    tlog().debug("netty-xxx-server: running on host " + ip +  ", port " + port);
    return ch;
  }

  public static void stop(final ServerBootstrap bs, Channel ch) {
    ch.close().addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture ff) {
        EventLoopGroup gc = bs.childGroup();
        EventLoopGroup gp = bs.group();
        if (gp != null) try { gp.shutdownGracefully(); } catch (Throwable e) {}
        if (gc != null) try { gc.shutdownGracefully(); } catch (Throwable e) {}
      }
    });
  }

}

