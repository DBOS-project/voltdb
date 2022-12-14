/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
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
package org.voltdb;

public enum LogEntryType {
    INITIATION,
    FAULT,     // UNUSED BUT NEEDS TO STAY TO MAINTAIN BACKWARD COMPATIBILITY
               // Eliminate the next time we bump the CL version number?

    TOPOLOGY,
    HEARTBEAT, // UNUSED BUT NEEDS TO STAY TO MAINTAIN BACKWARD COMPATIBILITY
               // Eliminate the next time we bump the CL version number?

    INITIATORFAULT,  // UNUSED BUT NEEDS TO STAY TO MAINTAIN BACKWARD COMPATIBILITY
                     // Eliminate the next time we bump the CL version number?

    IV2FAULT,

    MASTERMODE; //Cluster is in master-only mode via @StopReplicas

    public byte asByte() {
        assert(this.ordinal() < Byte.MAX_VALUE);
        return (byte)this.ordinal();
    }

    public static LogEntryType valueOf(byte val) {
        for (LogEntryType t : LogEntryType.values()) {
            if (t.ordinal() == val) {
                return t;
            }
        }
        return null;
    }
}
