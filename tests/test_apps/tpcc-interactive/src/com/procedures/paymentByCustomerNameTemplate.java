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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.lang.reflect.Field;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.VoltType;
import com.Constants;
import org.voltdb.types.TimestampType;

//Notes on Stored Procedure:
//Does Transaction in slightly different order than Profile specifies--gets the customer first.
//return VoltTable[] has N element(s):
//1) var_name, represented as a NxN table representing typeFOOBAR.

public class paymentByCustomerNameTemplate extends VoltProcedure {

    final int misc_expected_string_len = 32 + 2 + 32 + 32 + 32 + 32 + 2 + 9 + 32 + 2 + 500;

    final VoltTable misc_template = new VoltTable(
            new VoltTable.ColumnInfo("c_id", VoltType.INTEGER),
            new VoltTable.ColumnInfo("c_first", VoltType.STRING),
            new VoltTable.ColumnInfo("c_middle", VoltType.STRING),
            new VoltTable.ColumnInfo("c_last", VoltType.STRING),
            new VoltTable.ColumnInfo("c_street_1", VoltType.STRING),
            new VoltTable.ColumnInfo("c_street_2", VoltType.STRING),
            new VoltTable.ColumnInfo("c_city", VoltType.STRING),
            new VoltTable.ColumnInfo("c_state", VoltType.STRING),
            new VoltTable.ColumnInfo("c_zip", VoltType.STRING),
            new VoltTable.ColumnInfo("c_phone", VoltType.STRING),
            new VoltTable.ColumnInfo("c_since", VoltType.TIMESTAMP),
            new VoltTable.ColumnInfo("c_credit", VoltType.STRING),
            new VoltTable.ColumnInfo("c_credit_lim", VoltType.FLOAT),
            new VoltTable.ColumnInfo("c_discount", VoltType.FLOAT),
            new VoltTable.ColumnInfo("c_balance", VoltType.FLOAT),
            new VoltTable.ColumnInfo("c_data", VoltType.STRING)
    );

    private final int C_ID_IDX = 0;
    private final int C_FIRST_IDX = 1;
    private final int C_MIDDLE_IDX = 2;
    private final int C_LAST_IDX = 3;
    private final int C_STREET_1_IDX = 4;
    private final int C_STREET_2_IDX = 5;
    private final int C_CITY_IDX = 6;
    private final int C_STATE_IDX = 7;
    private final int C_ZIP_IDX = 8;
    private final int C_PHONE_IDX = 9;
    private final int C_SINCE_IDX = 10;
    private final int C_CREDIT_IDX = 11;
    private final int C_CREDIT_LIM_IDX = 12;
    private final int C_DISCOUNT_IDX = 13;
    private final int C_BALANCE_IDX = 14;
    private final int C_YTD_PAYMENT_IDX = 15;
    private final int C_PAYMENT_CNT_IDX = 16;
    private final int C_DATA_IDX = 17;

    public final SQLStmt getWarehouse = new SQLStmt("SELECT W_NAME, W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP FROM WAREHOUSE WHERE W_ID = ?;"); //w_id
    private final int W_NAME_IDX = 0;

    public final SQLStmt updateWarehouseBalance = new SQLStmt("UPDATE WAREHOUSE SET W_YTD = W_YTD + ? WHERE W_ID = ?;"); //h_amount, w_id

    public final SQLStmt getDistrict = new SQLStmt("SELECT D_NAME, D_STREET_1, D_STREET_2, D_CITY, D_STATE, D_ZIP FROM DISTRICT WHERE D_W_ID = ? AND D_ID = ?;"); //w_id, d_id
    private final int D_NAME_IDX = 0;

    //Does this work?
    //h_amount, d_w_id, d_id
    public final SQLStmt updateDistrictBalance = new SQLStmt("UPDATE DISTRICT SET D_YTD = D_YTD + ? WHERE D_W_ID = ? AND D_ID = ?;");

    public final SQLStmt updateBCCustomer = new SQLStmt("UPDATE CUSTOMER SET C_BALANCE = ?, C_YTD_PAYMENT = ?, C_PAYMENT_CNT = ?, C_DATA = ? WHERE C_W_ID = ? AND C_D_ID = ? AND C_ID = ?;"); //c_balance, c_ytd_payment, c_payment_cnt, c_data, c_w_id, c_d_id, c_id

    public final SQLStmt updateGCCustomer = new SQLStmt("UPDATE CUSTOMER SET C_BALANCE = ?, C_YTD_PAYMENT = ?, C_PAYMENT_CNT = ? WHERE C_W_ID = ? AND C_D_ID = ? AND C_ID = ?;"); //c_balance, c_ytd_payment, c_payment_cnt, c_w_id, c_d_id, c_id

    public final SQLStmt getCustomersByLastName = new SQLStmt("SELECT C_ID, C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2, C_CITY, C_STATE, C_ZIP, C_PHONE, C_SINCE, C_CREDIT, C_CREDIT_LIM, C_DISCOUNT, C_BALANCE, C_YTD_PAYMENT, C_PAYMENT_CNT, C_DATA FROM CUSTOMER WHERE C_LAST = ? AND C_D_ID = ? AND C_W_ID = ? ORDER BY C_FIRST;");// c_last, d_id, w_id

    public final SQLStmt insertHistory = new SQLStmt("INSERT INTO HISTORY VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

    public final HashMap<String, SQLStmt> stmtMap = new HashMap<String, SQLStmt>();
    
    public paymentByCustomerNameTemplate() {
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
                }    
            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
            
        }
    }

    public VoltTable[] run(short w_id, List<String> SQLStmts, List<List<Object>> params) throws VoltAbortException {
        for (int i = 0; i < SQLStmts.size(); i++) {
            final String sql = SQLStmts.get(i);
            final List<Object> param = params.get(i);
            final SQLStmt stmt = stmtMap.get(sql);
            voltQueueSQL(stmt, param.toArray());
        }
        return voltExecuteSQL();
    }
}
