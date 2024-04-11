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

/**
 * Multi-partition version of {@link paymentByCustomerId} split for
 * Customer related queries. See deps.pdf for dependencies between the queries
 * in original paymentByCustomerId.
 */
public class paymentByCustomerIdCTemplate extends VoltProcedure {

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

    // c_id, d_id, w_id
    public final SQLStmt getCustomersByCustomerId = new SQLStmt("SELECT C_ID, C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2, C_CITY, C_STATE, C_ZIP, C_PHONE, C_SINCE, C_CREDIT, C_CREDIT_LIM, C_DISCOUNT, C_BALANCE, C_YTD_PAYMENT, C_PAYMENT_CNT, C_DATA FROM CUSTOMER WHERE C_ID = ? AND C_D_ID = ? AND C_W_ID = ?;");
    // private final int C_ID_IDX = 0;
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

    public final SQLStmt updateBCCustomer = new SQLStmt("UPDATE CUSTOMER SET C_BALANCE = ?, C_YTD_PAYMENT = ?, C_PAYMENT_CNT = ?, C_DATA = ? WHERE C_W_ID = ? AND C_D_ID = ? AND C_ID = ?;"); //c_balance, c_ytd_payment, c_payment_cnt, c_data, c_w_id, c_d_id, c_id

    public final SQLStmt updateGCCustomer = new SQLStmt("UPDATE CUSTOMER SET C_BALANCE = ?, C_YTD_PAYMENT = ?, C_PAYMENT_CNT = ? WHERE C_W_ID = ? AND C_D_ID = ? AND C_ID = ?;"); //c_balance, c_ytd_payment, c_payment_cnt, c_w_id, c_d_id, c_id

    public final HashMap<String, SQLStmt> stmtMap = new HashMap<String, SQLStmt>();
    
    public paymentByCustomerIdCTemplate() {
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
    
    public VoltTable[] run(short c_w_id, List<String> SQLStmts, List<List<Object>> params) throws VoltAbortException {
        for (int i = 0; i < SQLStmts.size(); i++) {
            final String sql = SQLStmts.get(i);
            final List<Object> param = params.get(i);
            final SQLStmt stmt = stmtMap.get(sql);
            voltQueueSQL(stmt, param.toArray());
        }
        return voltExecuteSQL();
    }
}
