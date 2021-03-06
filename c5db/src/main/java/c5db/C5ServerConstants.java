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

package c5db;

public class C5ServerConstants {
  public static final int MSG_SIZE = 100;
  public static final String LOG_NAME = "log";
  public static final String WAL_DIR = "wal";
  public static final String ARCHIVE_DIR = "old_wal";
  public static final int MAX_CALL_SIZE = Integer.MAX_VALUE;
  public static final long MAX_CONTENT_LENGTH_HTTP_AGG = 8192;
  public static final String CLUSTER_NAME_PROPERTY_NAME = "clusterName";
  public static final String LOCALHOST = "localhost";
  public static final int WAL_THREAD_POOL_SIZE = 1;
  public static final byte[] META_INFO_CF = {1, 2, 3, 4};
  public static final byte[] META_ROW = {1, 2, 3, 4, 5};
  public static final byte[] META_INFO_CQ = {1, 2, 3, 4};
  public static final byte[] INTERNAL_NAMESPACE = {1, 2, 3, 4};
  public static final byte[] META_TABLE_NAME = {1, 2, 3, 4};
  public static final byte[] META_START_KEY = {0x00};

  public static final byte[] META_END_KEY = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
}
