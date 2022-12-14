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

package txnIdSelfCheck.procedures;

import org.voltdb.*;

public class ValidateLoadBase extends VoltProcedure {

    public VoltTable[] doValidate(SQLStmt select, long cid, VoltTable vt) {
        if (vt.getRowCount() != 1)
            throw new VoltAbortException("expected data exception " + vt.getRowCount());
        VoltTableRow vtrow = vt.fetchRow(0);
        if (cid != vtrow.getLong(0))
            throw new VoltAbortException("cid column exception expected: " + vtrow.getLong(0) + " actual: " + cid);
        voltQueueSQL(select, cid);
        VoltTable[] results = voltExecuteSQL(false);
        if (results.length != 1)
            throw new VoltAbortException("length exception " + results.length);
        VoltTable data = results[0];
        if (data.getRowCount() > 1)
            throw new VoltAbortException("rowcount exception " + data.getRowCount());
        if (data.getRowCount() > 0) {
            VoltTableRow row = data.fetchRow(0);
            int ncol = data.getColumnCount();
            if (vtrow.getColumnCount() != ncol)
                throw new VoltAbortException("column count exception expected: " + vtrow.getColumnCount() + " actual: " + ncol);
            for (int c = 0; c < ncol; c++) {
                VoltType vtype = vtrow.getColumnType(c);
                Object expected = vtrow.get(c, vtype);
                Object actual = row.get(c, vtype);
                if (!expected.equals(actual))
                    throw new VoltAbortException("actual data wrong column " + c
                            + " expected: " + expected.toString()
                            + " actual: " + actual.toString() + " cid: " + cid);
            }
        }
        return results;
    }


    public long run() {
        return 0; // never called in base procedure
    }

}
