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
package c5db.driver;

import c5db.ConfigDirectory;
import c5db.discovery.BeaconService;
import c5db.interfaces.C5Module;
import c5db.interfaces.C5Server;
import c5db.interfaces.ReplicationModule;
import c5db.log.LogService;
import c5db.messages.generated.ModuleType;
import c5db.replication.ReplicatorService;
import c5db.util.C5FiberFactory;
import c5db.util.C5Futures;
import c5db.util.ExceptionHandlingBatchExecutor;
import c5db.util.FiberOnly;
import c5db.util.PoolFiberFactoryWithExecutor;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Server extends AbstractService implements C5Server {
  private static final Logger LOG = LoggerFactory.getLogger(Server.class);

  private final long nodeId;
  private final Fiber serverFiber;
  //  private final Channel<Message<?>> commandChannel = new MemoryChannel<>();
  private final PoolFiberFactory allFibers;
  private final NioEventLoopGroup listenerGroup;
  private final NioEventLoopGroup ioWorkerGroup;
  private final ConfigDirectory configDirectory;
  private final Map<ModuleType, C5Module> allModules = new HashMap<>();
  private final Channel<ModuleStateChange> serviceRegisteredNotices = new MemoryChannel<>();
  private ReplicationModule.Replicator theOnlyReplicator = null;
  private Disposable dataLogger = null;

  public Server(ConfigDirectory configDirectory) throws IOException {
    this.configDirectory = configDirectory;

    String data = configDirectory.getNodeId();
    if (data != null) {
      try {
        this.nodeId = Long.parseLong(data);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("NodeId was not parsable: " + data);
      }
    } else {
      throw new IllegalArgumentException("NodeId was not parsable: (null)");
    }
    allFibers = new PoolFiberFactory(Executors.newFixedThreadPool(4));
    listenerGroup = new NioEventLoopGroup(1);
    ioWorkerGroup = new NioEventLoopGroup();
    serverFiber = allFibers.create();
  }

  @Override
  protected void doStart() {

    serverFiber.execute(this::startModules);

    serverFiber.start();

    notifyStarted();
  }

  @Override
  protected void doStop() {
    serverFiber.dispose();
    allFibers.dispose();

    notifyStopped();
  }

  @FiberOnly
  private void startModules() {
    Random portRandomizer = new Random();

    final int discoveryPort = 20012;
    final int replicatorPort = 24000 + portRandomizer.nextInt(1000);

    try {
      startModule(
          new BeaconService(nodeId,
              discoveryPort,
              allFibers.create(),
              ioWorkerGroup,
              new HashMap<>(),
              this)
      );

      startModule(new LogService(this));

      ReplicatorService replicatorService = new ReplicatorService(listenerGroup, ioWorkerGroup, replicatorPort, this);
      startModule(
          replicatorService);

      // required to number the 3 nodes 1,2,3, to change modify here.
      C5Futures.addCallback(replicatorService.createReplicator(Main.THE_ONLY_QUORUM, Main.PEERS),
          replicator -> {
            Server.this.theOnlyReplicator = replicator;
            replicator.getStateChannel().subscribe(serverFiber, state -> {
              if (state == ReplicationModule.Replicator.State.LEADER) {
                dataLogger = serverFiber.scheduleAtFixedRate(this::insertData, 1, 1, TimeUnit.SECONDS);
              } else {
                // if we have a datalogger, we are no longer leader, and therefore should remove it
                if (dataLogger != null) {
                  dataLogger.dispose();
                  dataLogger = null;
                }
              }
            });

            replicator.start();
          }, failureCause -> {
            LOG.error("Unable to start replicator", failureCause);
          }, serverFiber
      );

    } catch (InterruptedException | SocketException e) {
      LOG.error("Startup failure due to exception", e);
    }
  }

  private void insertData() {
    assert theOnlyReplicator != null;

    List<ByteBuffer> randomData = randomData(200);

    try {
      theOnlyReplicator.logData(randomData);
    } catch (InterruptedException e) {
      LOG.warn("insertData got IE", e);
    }
  }

  private Random randomDataSource = new Random();

  private List<ByteBuffer> randomData(int byteLengthToGenerate) {
    byte[] bytesDatum = new byte[byteLengthToGenerate];
    randomDataSource.nextBytes(bytesDatum);

    List<ByteBuffer> returnedList = new ArrayList<>();
    returnedList.add(ByteBuffer.wrap(bytesDatum));
    return returnedList;
  }


  @Override
  public long getNodeId() {
    return this.nodeId;
  }

  @Override
  public ListenableFuture<C5Module> getModule(ModuleType moduleType) {
    final SettableFuture<C5Module> futureToReturn = SettableFuture.create();

    serverFiber.execute(() -> {
      if (!allModules.containsKey(moduleType)) {
        Disposable[] d = new Disposable[]{null};
        d[0] = getModuleStateChangeChannel().subscribe(serverFiber, moduleStateNotice -> {
          if (moduleStateNotice.state != State.RUNNING) {
            return;
          }

          if (moduleStateNotice.module.getModuleType().equals(moduleType)) {
            futureToReturn.set(moduleStateNotice.module);

            d[0].dispose();
          }
        });
      }

      futureToReturn.set(allModules.get(moduleType));
    });

    return futureToReturn;
  }

  @Override
  public Channel<ModuleStateChange> getModuleStateChangeChannel() {
    return serviceRegisteredNotices;
  }

  @Override
  public ConfigDirectory getConfigDirectory() {
    return configDirectory;
  }

  @Override
  public C5FiberFactory getFiberFactory(Consumer<Throwable> throwableHandler) {
    return new PoolFiberFactoryWithExecutor(this.allFibers,
        new ExceptionHandlingBatchExecutor(throwableHandler));
  }

  private void startModule(final C5Module module) {
    module.addListener(new ModuleStatePublisher(module), serverFiber);

    module.start();
    allModules.put(module.getModuleType(), module);
  }

  /**
   * *************  ------------------ *******************
   */
  private class ModuleStatePublisher implements Listener {
    private final C5Module module;

    private ModuleStatePublisher(C5Module module) {
      this.module = module;
    }

    private void publishEvent(State state) {
      ModuleStateChange p = new ModuleStateChange(module, state);
      getModuleStateChangeChannel().publish(p);
    }


    @Override
    public void starting() {
      publishEvent(State.STARTING);
    }

    @Override
    public void running() {
      publishEvent(State.RUNNING);
    }

    @Override
    public void stopping(State from) {
      publishEvent(State.STOPPING);
    }

    @Override
    public void terminated(State from) {
      publishEvent(State.TERMINATED);

    }

    @Override
    public void failed(State from, Throwable failure) {
      LOG.error("Failed module: " + module, failure);
      publishEvent(State.FAILED);
    }
  }
}
