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
import c5db.NioFileConfigDirectory;
import com.google.common.collect.ImmutableList;

import java.nio.file.Paths;

/**
 * Run a little test server.
 */
public class Main {
  public static final ImmutableList<Long> PEERS = ImmutableList.of(1L, 2L, 3L);
  public static final String THE_ONLY_QUORUM = "the-only-quorum";

  private static Server serverInstance;

  public static void main(String[] args) throws Exception {

    String username = System.getProperty("user.name");
    // require node id on the command line:
    if (args.length < 1) {
      System.err.println("Please provide node id on the command line!");
      System.exit(1);
    }

    long nodeId = Long.parseLong(args[0]);

    String runPath = "/tmp/" + username + "/c5-" + Long.toString(nodeId);

    ConfigDirectory configDirectory = new NioFileConfigDirectory(Paths.get(runPath));
    configDirectory.setNodeIdFile(String.valueOf(nodeId));

    serverInstance = new Server(configDirectory);

    serverInstance.start();
  }
}
