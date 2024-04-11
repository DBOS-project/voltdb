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
import java.util.HashSet;
import java.util.List;
import java.lang.reflect.Field;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

//Notes on Stored Procedure:
//return VoltTable[] has 1 element:
//1) stock_count, represented as a 1x1 table representing a Long.

public class slevTemplate extends VoltProcedure {

    public final SQLStmt GetOId = new SQLStmt("SELECT D_NEXT_O_ID FROM DISTRICT WHERE D_W_ID = ? AND D_ID = ?;");

    public final SQLStmt GetStockCount = new SQLStmt(
        "SELECT COUNT(DISTINCT(OL_I_ID)) FROM ORDER_LINE, STOCK " +
        "WHERE OL_W_ID = ? AND " +
        "OL_D_ID = ? AND " +
        "OL_O_ID < ? AND " +
        "OL_O_ID >= ? AND " +
        "S_W_ID = ? AND " +
        "S_I_ID = OL_I_ID AND " +
        "S_QUANTITY < ?;");


    public final HashMap<String, SQLStmt> stmtMap = new HashMap<String, SQLStmt>();
    
    public slevTemplate() {
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

    public VoltTable[] run(short w_id, List<String> SQLStmts, List<List<Object>> params) {
        for (int i = 0; i < SQLStmts.size(); i++) {
            final String sql = SQLStmts.get(i);
            final List<Object> param = params.get(i);
            final SQLStmt stmt = stmtMap.get(sql);
            voltQueueSQL(stmt, param.toArray());
        }
        return voltExecuteSQL();
    }
}
