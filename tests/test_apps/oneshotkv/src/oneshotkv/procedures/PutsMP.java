/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package oneshotkv.procedures;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class PutsMP extends VoltProcedure {
    // Checks if key exists
    public final SQLStmt checkStmt = new SQLStmt("SELECT key FROM store WHERE key = ?;");

    // Updates a key/value pair
    public final SQLStmt updateStmt = new SQLStmt("UPDATE store SET value = ? WHERE key = ?;");

    // Inserts a key/value pair
    public final SQLStmt insertStmt = new SQLStmt("INSERT INTO store (key, value) VALUES (?, ?);");

    public VoltTable[] run(byte[] value, String [] keys) {
        for (String key: keys) {
            // Check whether the pair exists
            voltQueueSQL(checkStmt, key);
        }

        VoltTable [] checkResults = voltExecuteSQL();

        for (int i = 0; i < keys.length; ++i) {
            String key = keys[i];
            // Insert new or update existing key depending on result
            if (checkResults[i].getRowCount() == 0)
                voltQueueSQL(insertStmt, key, value);
            else
                voltQueueSQL(updateStmt, value, key);
        }

        return voltExecuteSQL(true);
    }
}
