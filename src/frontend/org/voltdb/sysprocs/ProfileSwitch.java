/* This file is part of VoltDB.
 * Copyright (C) 2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.sysprocs;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.voltcore.logging.VoltLogger;
import org.voltdb.VoltDB;
import org.voltdb.VoltNTSystemProcedure;
import org.voltdb.client.ClientResponse;
import org.voltdb.jni.ExecutionEngine;

public class ProfileSwitch  extends VoltNTSystemProcedure {
    private final static VoltLogger log = new VoltLogger("ProfileSwitch");


    public long run(int option) throws InterruptedException, ExecutionException {
        if (option == 0) {
            ExecutionEngine.VoltDBEnableTracing(false);
            System.out.println("disable tracing");
        } else if (option == 1) {
            System.out.println("enable tracing");
            ExecutionEngine.VoltDBEnableTracing(true);
        } else if (option == 2) {
            ExecutionEngine.VoltDBEnableTracing(false);
            System.out.println("dump traces");
            ExecutionEngine.VoltDBDumpTraces("/home/zxjcarrot/Workspace/networking-xj/voltdb-trace".getBytes());
        } else {
            VoltDB.crashLocalVoltDB("Invalid option for ProfileSwitch: " + option, true);
        }
        return 0;
    }
}
