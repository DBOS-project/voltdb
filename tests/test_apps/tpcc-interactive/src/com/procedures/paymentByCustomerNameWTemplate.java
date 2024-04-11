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

import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.lang.reflect.Field;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.types.TimestampType;


/**
 * Multi-partition version of {@link paymentByCustomerName} split for
 * Warehouse related queries. See deps.pdf for dependencies between the queries
 * in original paymentByCustomerName.
 */

public class paymentByCustomerNameWTemplate extends VoltProcedure {
    public final SQLStmt getCidByLastName = new SQLStmt("SELECT C_ID FROM CUSTOMER_NAME WHERE C_LAST = ? AND C_D_ID = ? AND C_W_ID = ? ORDER BY C_FIRST;");// c_last, d_id, w_id

    public final SQLStmt getWarehouse = new SQLStmt("SELECT W_NAME, W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP FROM WAREHOUSE WHERE W_ID = ?;"); //w_id
    private final int W_NAME_IDX = 0;

    //Does this work?
    public final SQLStmt updateWarehouseBalance = new SQLStmt("UPDATE WAREHOUSE SET W_YTD = W_YTD + ? WHERE W_ID = ?;"); //h_amount, w_id

    public final SQLStmt getDistrict = new SQLStmt("SELECT D_NAME, D_STREET_1, D_STREET_2, D_CITY, D_STATE, D_ZIP FROM DISTRICT WHERE D_W_ID = ? AND D_ID = ?;"); //w_id, d_id
    private final int D_NAME_IDX = 0;

    //Does this work?
    //h_amount, d_w_id, d_id
    public final SQLStmt updateDistrictBalance = new SQLStmt("UPDATE DISTRICT SET D_YTD = D_YTD + ? WHERE D_W_ID = ? AND D_ID = ?;");

    public final SQLStmt insertHistory = new SQLStmt("INSERT INTO HISTORY VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

    public final HashMap<String, SQLStmt> stmtMap = new HashMap<String, SQLStmt>();
    
    public paymentByCustomerNameWTemplate() {
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
