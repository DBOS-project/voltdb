/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This file contains original code and/or modifications of original code.
 * Any modifications made by Volt Active Data Inc. are licensed under the following
 * terms and conditions:
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
/* Copyright (C) 2008
 * Michael McCanna
 * Massachusetts Institute of Technology
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
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.procedures;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.VoltType;
import com.Constants;
import org.voltdb.types.TimestampType;

public class deliveryPart4 extends VoltProcedure {
    private final VoltTable result_template = new VoltTable(
        new VoltTable.ColumnInfo("d_id", VoltType.TINYINT),
        new VoltTable.ColumnInfo("o_id", VoltType.INTEGER)
    );

    //BLOCK: Replaces use of cursor with a limited select.
    public final SQLStmt getNewOrder =
        new SQLStmt("SELECT NO_O_ID FROM NEW_ORDER WHERE NO_D_ID = ? AND NO_W_ID = ? AND NO_O_ID > -1 LIMIT 1;");

    public final SQLStmt deleteNewOrder =
        new SQLStmt("DELETE FROM NEW_ORDER WHERE NO_D_ID = ? AND NO_W_ID = ? AND NO_O_ID = ?;"); // d_id, w_id, no_o_id
    //END OF BLOCK

    public final SQLStmt getCId =
        new SQLStmt("SELECT O_C_ID FROM ORDERS WHERE O_ID = ? AND O_D_ID = ? AND O_W_ID = ?;"); //no_o_id, d_id, w_id
    //into c_id

    public final SQLStmt updateOrders =
        new SQLStmt("UPDATE ORDERS SET O_CARRIER_ID = ? WHERE O_ID = ? AND O_D_ID = ? AND O_W_ID = ?;"); //o_carrier_id, no_o_id, d_id, w_id

    public final SQLStmt updateOrderLine =
        new SQLStmt("UPDATE ORDER_LINE SET OL_DELIVERY_D = ? WHERE OL_O_ID = ? AND OL_D_ID = ? AND OL_W_ID = ?;"); //timestamp, no_o_id, d_id, w_id

    //is ol_amount a column? assuming so (and that this sum() is being supported)
    public final SQLStmt sumOLAmount =
        new SQLStmt("SELECT SUM(OL_AMOUNT) FROM ORDER_LINE WHERE OL_O_ID = ? AND OL_D_ID = ? AND OL_W_ID = ?;");//no_o_id, d_id, w_id
    //into ol_total

    public final SQLStmt updateCustomer =
        new SQLStmt("UPDATE CUSTOMER SET C_BALANCE = C_BALANCE + ? WHERE C_ID = ? AND C_D_ID = ? AND C_W_ID = ?;"); //ol_total, c_id, d_id, w_id

    public long run(short w_id,  double ol_totals[], long c_ids[]) throws VoltAbortException {
        for (long d_id  = 1; d_id <= Constants.DISTRICTS_PER_WAREHOUSE; ++d_id) {
            if (c_ids[(int) d_id - 1] == -1) {
                continue;
            }

            voltQueueSQL(updateCustomer, ol_totals[(int) d_id - 1], c_ids[(int) d_id - 1], d_id, w_id);
        }
        voltExecuteSQL();
        return 0;
    }
}
