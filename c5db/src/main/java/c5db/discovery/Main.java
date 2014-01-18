/*
 * Copyright (C) 2014  Ohm Data
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package c5db.discovery;

import c5db.interfaces.DiscoveryModule;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static c5db.discovery.generated.Beacon.Availability;
import static c5db.interfaces.DiscoveryModule.NodeInfo;


public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    final DiscoveryModule beaconService = null;
    private final String clusterName;
    private final int discoveryPort;
    private final int servicePort;
    private final long nodeId;

    Main(String clusterName) throws SocketException, InterruptedException {
        this.clusterName = clusterName;

        // a non-privileged port between 1024 -> ....
        this.discoveryPort = (Math.abs(clusterName.hashCode()) % 16384) + 1024;
        System.out.println("Cluster port = " + discoveryPort);

        this.servicePort = discoveryPort + (int)(Math.random() * 5000);

        Availability.Builder builder = Availability.newBuilder();
        builder.setBaseNetworkPort(servicePort);
        nodeId = new Random().nextLong();
        builder.setNodeId(nodeId);

        // nodeId
        // servicePort
        // discoveryPort

        //beaconService = new BeaconService(discoveryPort, builder.buildPartial());
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Specify cluster name as arg1 pls");
            System.exit(1);
        }
        String clusterName = args[0];


        new Main(clusterName).run();
    }

    public void run() throws Exception {
        beaconService.startAndWait();


        System.out.println("Started");

//        Thread.sleep(10000);

//        System.out.println("making state request to beacon module");
        // now try to RPC myself a tad:
        final Fiber fiber = new ThreadFiber();
        fiber.start();

        fiber.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                ListenableFuture<ImmutableMap<Long,NodeInfo>> fut = beaconService.getState();
                try {
                    ImmutableMap<Long,NodeInfo> state = fut.get();

                    System.out.println("State info:");
                    for(DiscoveryModule.NodeInfo info : state.values()) {
                        System.out.println(info);
                    }

                } catch (InterruptedException | ExecutionException e) {
                    // ignore
                }
            }
        }, 10, 10, TimeUnit.SECONDS);


        ServerBootstrap b = new ServerBootstrap();
        NioEventLoopGroup parentGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup childGroup = new NioEventLoopGroup();
        b.group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new InboundHandler());
                    }
                });
        Channel serverChannel = b.bind(servicePort).sync().channel();

        ImmutableMap<Long,NodeInfo> peers = waitForAPeerOrMore();

        // make a new bootstrap, it's just so silly:
        Bootstrap b2 = new Bootstrap();
        b2.group(childGroup)
                .option(ChannelOption.TCP_NODELAY, true)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
                    }
                });
        // we dont even need to do anything else!


        System.out.println("Listening on module port: " + servicePort);
        // now send messages to all my peers except myself of course, duh.
        for ( NodeInfo peer: peers.values()) {
            if (peer.availability.getNodeId() == nodeId) {
                // yes this is me, and continue
                continue;
            }
            InetSocketAddress remotePeerAddr = new InetSocketAddress(peer.availability.getAddresses(0), peer.availability.getBaseNetworkPort());
            // uh ok lets connect and make hash:

            SocketAddress localAddr = serverChannel.localAddress();
            System.out.println("Writing some junk to: " + remotePeerAddr + " from: " + localAddr);
            Channel peerChannel = b2.connect(remotePeerAddr, localAddr).channel();
            System.out.println("Channel RemoteAddr: " + peerChannel.remoteAddress() + " localaddr: " + peerChannel.localAddress());
            // now write a little bit:
            peerChannel.write("42");
        }
    }

    private ImmutableMap<Long,NodeInfo> waitForAPeerOrMore() throws ExecutionException, InterruptedException {
        final Fiber fiber = new ThreadFiber();
        fiber.start();
        final SettableFuture<ImmutableMap<Long,NodeInfo>> future = SettableFuture.create();


        LOG.info("Waiting for peers...");
        return future.get();
    }

}
