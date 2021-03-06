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

package c5db.interfaces;

import c5db.ConfigDirectory;
import c5db.messages.generated.ModuleType;
import c5db.util.C5FiberFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import org.jetlang.channels.Channel;

import java.util.function.Consumer;

/**
 * A C5Server stands in for global resources that modules might need.  It provides global
 * services, configuration, notification buses and more.
 * <p>
 * Right now this interface is a little too kitchen-sinky, and it should probably have
 * individual responsibilities broken off to make dependencies a bit more clear.
 * <p>
 * Note that multiple {@link c5db.interfaces.C5Server} may be in a single JVM, so avoiding
 * static method calls is of paramount importance.
 */
public interface C5Server extends Service {
  /**
   * Every server has a persistent id that is independent of it's (in some cases temporary)
   * network or host identification.  Normally this would be persisted in a configuration
   * file, and generated randomly (64 bits is enough for everyone, right?).  There may be
   * provisions to allow administrators to assign node ids.
   * <p>
   *
   * @return THE node id for this server.
   */
  public long getNodeId();

  // TODO this could be generified if we used an interface instead of ModuleType

  /**
   * This is primary mechanism via which modules with compile time binding via interfaces
   * that live in {@link c5db.interfaces} may obtain instances of their dependencies.
   * <p>
   * This method returns a future, which implies that the module may not be started yet.
   * The future will be signalled when the module is started, and callers may just add a
   * callback and wait.
   * <p>
   * In the future when automatic service startup order is working, this method might just
   * return the type without a future, or may not require much/any waiting.
   * <p>
   * Right now modules are specified via an enum, in the future perhaps we should
   * use a Java interface type?
   *
   * @param moduleType the specific module type you wish to retrieve
   * @return a future that will be set when the module is running
   */
  public ListenableFuture<C5Module> getModule(ModuleType moduleType);

  public Channel<ModuleStateChange> getModuleStateChangeChannel();

  public ConfigDirectory getConfigDirectory();

  /**
   * Return a C5FiberFactory using the passed exception handler, which will be run on the fiber
   * that throws an uncaught exception.
   *
   * @param throwableHandler Exception handler for pool fibers to use.
   * @return C5FiberFactory instance.
   */
  public C5FiberFactory getFiberFactory(Consumer<Throwable> throwableHandler);

  public static class ModuleStateChange {
    public final C5Module module;
    public final State state;

    @Override
    public String toString() {
      return "ModuleStateChange{" +
          "module=" + module +
          ", state=" + state +
          '}';
    }

    public ModuleStateChange(C5Module module, State state) {
      this.module = module;
      this.state = state;
    }
  }
}
