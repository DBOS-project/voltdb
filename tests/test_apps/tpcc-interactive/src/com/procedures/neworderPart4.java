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

import java.util.HashMap;
import java.util.List;
import java.lang.reflect.Field;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.VoltType;
import com.Constants;
import org.voltdb.types.TimestampType;

//Notes on Stored Procedure:
//Need to add error handling to catch invalid items, and still return needed values.

public class neworderPart4 extends VoltProcedure {
    private final VoltTable item_data_template = new VoltTable(
            new VoltTable.ColumnInfo("i_name", VoltType.STRING),
            new VoltTable.ColumnInfo("s_quantity", VoltType.INTEGER),
            new VoltTable.ColumnInfo("brand_generic", VoltType.STRING),
            new VoltTable.ColumnInfo("i_price", VoltType.FLOAT),
            new VoltTable.ColumnInfo("ol_amount", VoltType.FLOAT)
    );
    private final VoltTable misc_template = new VoltTable(
            new VoltTable.ColumnInfo("w_tax", VoltType.FLOAT),
            new VoltTable.ColumnInfo("d_tax", VoltType.FLOAT),
            new VoltTable.ColumnInfo("o_id", VoltType.INTEGER),
            new VoltTable.ColumnInfo("total", VoltType.FLOAT)
    );

    public final SQLStmt getWarehouseTaxRate =
        new SQLStmt("SELECT W_TAX FROM WAREHOUSE WHERE W_ID = ?;"); //w_id

    public final SQLStmt getDistrict =
        new SQLStmt("SELECT D_TAX, D_NEXT_O_ID FROM DISTRICT WHERE D_ID = ? AND D_W_ID = ?;"); //d_id, w_id

    public final SQLStmt incrementNextOrderId =
        new SQLStmt("UPDATE DISTRICT SET D_NEXT_O_ID = ? WHERE D_ID = ? AND D_W_ID = ?;"); //d_next_o_id, d_id, w_id

    public final SQLStmt getCustomer =
        new SQLStmt("SELECT C_DISCOUNT, C_LAST, C_CREDIT FROM CUSTOMER WHERE C_W_ID = ? AND C_D_ID = ? AND C_ID = ?;"); //w_id, d_id, c_id

    public final SQLStmt createOrder =
        new SQLStmt("INSERT INTO ORDERS (O_ID, O_D_ID, O_W_ID, O_C_ID, O_ENTRY_D, O_CARRIER_ID, O_OL_CNT, O_ALL_LOCAL) VALUES (?, ?, ?, ?, ?, ?, ?, ?);"); //d_next_o_id, d_id, w_id, c_id, timestamp, o_carrier_id, o_ol_cnt, o_all_local

    public final SQLStmt createNewOrder =
        new SQLStmt("INSERT INTO NEW_ORDER (NO_O_ID, NO_D_ID, NO_W_ID) VALUES (?, ?, ?);"); //o_id, d_id, w_id

    public final SQLStmt getItemInfo =
        new SQLStmt("SELECT I_PRICE, I_NAME, I_DATA FROM ITEM WHERE I_ID = ?;"); //ol_i_id

    public final SQLStmt getStockInfo01 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_01 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo02 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_02 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo03 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_03 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo04 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_04 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo05 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_05 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo06 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_06 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo07 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_07 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo08 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_08 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo09 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_09 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id
    public final SQLStmt getStockInfo10 = new SQLStmt("SELECT S_QUANTITY, S_DATA, S_YTD, S_ORDER_CNT, S_REMOTE_CNT, S_DIST_10 FROM STOCK WHERE S_I_ID = ? AND S_W_ID = ?;"); //ol_i_id, ol_supply_w_id

    public final SQLStmt[] getStockInfo = {
            getStockInfo01,
            getStockInfo02,
            getStockInfo03,
            getStockInfo04,
            getStockInfo05,
            getStockInfo06,
            getStockInfo07,
            getStockInfo08,
            getStockInfo09,
            getStockInfo10,
    };

    public final SQLStmt updateStock = new SQLStmt("UPDATE STOCK SET S_QUANTITY = ?, S_YTD = ?, S_ORDER_CNT = ?, S_REMOTE_CNT = ? WHERE S_I_ID = ? AND S_W_ID = ?;"); //s_quantity, s_order_cnt, s_remote_cnt, ol_i_id, ol_supply_w_id

    public final SQLStmt createOrderLine = new SQLStmt("INSERT INTO ORDER_LINE (OL_O_ID, OL_D_ID, OL_W_ID, OL_NUMBER, OL_I_ID, OL_SUPPLY_W_ID, OL_DELIVERY_D, OL_QUANTITY, OL_AMOUNT, OL_DIST_INFO) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"); //o_id, d_id, w_id, ol_number, ol_i_id, ol_supply_w_id, ol_quantity, ol_amount, ol_dist_info

    public final HashMap<String, SQLStmt> stmtMap = new HashMap<String, SQLStmt>();
    
    public neworderPart4() {
        // Get the Class object of the instance
        Class<?> clazz = this.getClass();
        // Get all declared fields of the class
        Field[] fields = clazz.getDeclaredFields();
        // Print the names of all fields
        for (Field field : fields) {
            // Set the field accessible if it is private or protected
            try {
                field.setAccessible(true);
                Object o = field.get(this);
                if (o instanceof SQLStmt) {
                    stmtMap.put(field.getName(), (SQLStmt) o);
                } else if (o instanceof SQLStmt[]) {
                    SQLStmt[] stmts = (SQLStmt[]) o;
                    for (int i = 0; i < stmts.length; i++) {
                        stmtMap.put(field.getName() + "_" + String.valueOf(i), stmts[i]);
                    }
                }
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
        }
    }

    private int indexOf(byte[] array, byte[] subarray) {
        for (int i = 0; i <= array.length - subarray.length; ++i) {
            boolean match = true;
            for (int j = 0; j < subarray.length; ++j) {
                if (array[i + j] != subarray[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }

        return -1;
    }

    public VoltTable[] run(short w_id, byte d_id, int c_id,long d_next_o_id,  long[] s_quantities, long[] s_ytds, long[] s_order_cnts , long[] s_remote_cnts, long[] ol_numbers,long[] ol_i_ids, long[] ol_quantities, long[] ol_supply_w_ids, double[]ol_amounts, byte[][] s_dist_xxs, TimestampType timestamp) throws VoltAbortException {
        for (int i = 0; i < ol_quantities.length; ++i) {
            voltQueueSQL(updateStock, s_quantities[i], s_ytds[i], s_order_cnts[i], s_remote_cnts[i], ol_i_ids[i], ol_supply_w_ids[i]);
            voltQueueSQL(createOrderLine, d_next_o_id, d_id, w_id, ol_numbers[i], ol_i_ids[i], ol_supply_w_ids[i], timestamp, ol_quantities[i], ol_amounts[i], s_dist_xxs[i]);
        }

        return voltExecuteSQL();
    }
}
