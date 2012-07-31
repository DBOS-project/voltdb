/* This file is part of VoltDB.
 * Copyright (C) 2008-2012 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.hsqldb_voltpatches.VoltXMLElement;
import org.voltdb.VoltType;
import org.voltdb.catalog.Column;
import org.voltdb.catalog.Database;
import org.voltdb.catalog.Table;
import org.voltdb.expressions.AbstractExpression;
import org.voltdb.expressions.AggregateExpression;
import org.voltdb.expressions.ConstantValueExpression;
import org.voltdb.expressions.ExpressionUtil;
import org.voltdb.expressions.FunctionExpression;
import org.voltdb.expressions.ParameterValueExpression;
import org.voltdb.expressions.TupleValueExpression;
import org.voltdb.plannodes.SchemaColumn;
import org.voltdb.types.ExpressionType;

public abstract class AbstractParsedStmt {

    public static class TablePair {
        public Table t1;
        public Table t2;

        @Override
        public boolean equals(Object obj) {
            if ((obj instanceof TablePair) == false)
                return false;
            TablePair tp = (TablePair)obj;

            return (((t1 == tp.t1) && (t2 == tp.t2)) ||
                    ((t1 == tp.t2) && (t2 == tp.t1)));
        }

        @Override
        public int hashCode() {
            assert((t1.hashCode() ^ t2.hashCode()) == (t2.hashCode() ^ t1.hashCode()));

            return t1.hashCode() ^ t2.hashCode();
        }
    }


    public String sql;

    public VoltType[] paramList = new VoltType[0];

    public HashMap<Long, Integer> paramsById = new HashMap<Long, Integer>();

    public ArrayList<Table> tableList = new ArrayList<Table>();

    public AbstractExpression where = null;

    public ArrayList<AbstractExpression> whereSelectionList = new ArrayList<AbstractExpression>();

    public ArrayList<AbstractExpression> noTableSelectionList = new ArrayList<AbstractExpression>();

    public ArrayList<AbstractExpression> multiTableSelectionList = new ArrayList<AbstractExpression>();

    public HashMap<Table, ArrayList<AbstractExpression>> tableFilterList = new HashMap<Table, ArrayList<AbstractExpression>>();

    public HashMap<TablePair, ArrayList<AbstractExpression>> joinSelectionList = new HashMap<TablePair, ArrayList<AbstractExpression>>();

    public HashMap<AbstractExpression, Set<AbstractExpression> > valueEquivalence = new HashMap<AbstractExpression, Set<AbstractExpression>>();

    //User specified join order, null if none is specified
    public String joinOrder = null;

    // Store a table-hashed list of the columns actually used by this statement.
    // XXX An unfortunately counter-intuitive (but hopefully temporary) meaning here:
    // if this is null, that means ALL the columns get used.
    public HashMap<String, ArrayList<SchemaColumn>> scanColumns = null;

    /**
     *
     * @param sql
     * @param xmlSQL
     * @param db
     */
    public static AbstractParsedStmt parse(String sql, VoltXMLElement xmlSQL, Database db, String joinOrder) {
        final String INSERT_NODE_NAME = "insert";
        final String UPDATE_NODE_NAME = "update";
        final String DELETE_NODE_NAME = "delete";
        final String SELECT_NODE_NAME = "select";

        AbstractParsedStmt retval = null;

        if (xmlSQL == null) {
            System.err.println("Unexpected error parsing hsql parsed stmt xml");
            throw new RuntimeException("Unexpected error parsing hsql parsed stmt xml");
        }

        // create non-abstract instances
        if (xmlSQL.name.equalsIgnoreCase(INSERT_NODE_NAME)) {
            retval = new ParsedInsertStmt();
        }
        else if (xmlSQL.name.equalsIgnoreCase(UPDATE_NODE_NAME)) {
            retval = new ParsedUpdateStmt();
        }
        else if (xmlSQL.name.equalsIgnoreCase(DELETE_NODE_NAME)) {
            retval = new ParsedDeleteStmt();
        }
        else if (xmlSQL.name.equalsIgnoreCase(SELECT_NODE_NAME)) {
            retval = new ParsedSelectStmt();
        }
        else {
            throw new RuntimeException("Unexpected Element: " + xmlSQL.name);
        }

        // parse tables and parameters
        for (VoltXMLElement node : xmlSQL.children) {
            if (node.name.equalsIgnoreCase("parameters")) {
                retval.parseParameters(node, db);
            }
            if (node.name.equalsIgnoreCase("tablescans")) {
                retval.parseTables(node, db);
            }
            if (node.name.equalsIgnoreCase("scan_columns"))
            {
                retval.parseScanColumns(node, db);
            }
        }

        // parse specifics
        retval.parse(xmlSQL, db);

        // split up the where expression into categories
        retval.analyzeWhereExpression(db);
        // these just shouldn't happen right?
        assert(retval.multiTableSelectionList.size() == 0);
        assert(retval.noTableSelectionList.size() == 0);

        retval.sql = sql;
        retval.joinOrder = joinOrder;

        return retval;
    }

    /**
     *
     * @param stmtElement
     * @param db
     */
    abstract void parse(VoltXMLElement stmtElement, Database db);

    /**
     * Convert a HSQL VoltXML expression to an AbstractExpression tree.
     * @param root
     * @param db
     * @return configured AbstractExpression
     */
    AbstractExpression parseExpressionTree(VoltXMLElement root, Database db) {
        String elementName = root.name.toLowerCase();
        AbstractExpression retval = null;

        if (elementName.equals("value")) {
            retval = parseValueExpression(root);
        }
        else if (elementName.equals("columnref")) {
            retval = parseColumnRefExpression(root, db);
        }
        else if (elementName.equals("bool")) {
            retval = parseBooleanExpresion(root);
        }
        else if (elementName.equals("operation")) {
            retval = parseOperationExpression(root, db);
        }
        else if (elementName.equals("function")) {
            retval = parseFunctionExpression(root, db);
        }
        else if (elementName.equals("asterisk")) {
            return null;
        }
        else
            throw new PlanningErrorException("Unsupported expression node '" + elementName + "'");

        return retval;
    }

    /**
     *
     * @param exprNode
     * @param attrs
     * @return
     */
    AbstractExpression parseValueExpression(VoltXMLElement exprNode) {
        String type = exprNode.attributes.get("type");
        String isParam = exprNode.attributes.get("isparam");

        VoltType vt = VoltType.typeFromString(type);
        int size = VoltType.MAX_VALUE_LENGTH;
        assert(vt != VoltType.VOLTTABLE);

        if ((vt != VoltType.STRING) && (vt != VoltType.VARBINARY)) {
            if (vt == VoltType.NULL) size = 0;
            else size = vt.getLengthInBytesForFixedTypes();
        }
        if ((isParam != null) && (isParam.equalsIgnoreCase("true"))) {
            ParameterValueExpression expr = new ParameterValueExpression();
            long id = Long.parseLong(exprNode.attributes.get("id"));
            int paramIndex = paramIndexById(id);

            expr.setValueType(vt);
            expr.setValueSize(size);
            expr.setParameterIndex(paramIndex);

            return expr;
        }
        else {
            ConstantValueExpression expr = new ConstantValueExpression();
            expr.setValueType(vt);
            expr.setValueSize(size);
            if (vt == VoltType.NULL)
                expr.setValue(null);
            else
                expr.setValue(exprNode.attributes.get("value"));
            return expr;
        }
    }

    /**
     *
     * @param exprNode
     * @param attrs
     * @param db
     * @return
     */
    AbstractExpression parseColumnRefExpression(VoltXMLElement exprNode, Database db) {
        TupleValueExpression expr = new TupleValueExpression();

        String alias = exprNode.attributes.get("alias");
        String tableName = exprNode.attributes.get("table");
        String columnName = exprNode.attributes.get("column");

        Table table = db.getTables().getIgnoreCase(tableName);
        assert(table != null);
        Column column = table.getColumns().getIgnoreCase(columnName);
        assert(column != null);

        expr.setColumnAlias(alias);
        expr.setColumnName(columnName);
        expr.setColumnIndex(column.getIndex());
        expr.setTableName(tableName);
        expr.setValueType(VoltType.get((byte)column.getType()));
        expr.setValueSize(column.getSize());

        return expr;
    }

    /**
     *
     * @param exprNode
     * @param attrs
     * @return
     */
    AbstractExpression parseBooleanExpresion(VoltXMLElement exprNode) {
        ConstantValueExpression expr = new ConstantValueExpression();

        expr.setValueType(VoltType.BIGINT);
        expr.setValueSize(VoltType.BIGINT.getLengthInBytesForFixedTypes());
        if (exprNode.attributes.get("attrs").equalsIgnoreCase("true"))
            expr.setValue("1");
        else
            expr.setValue("0");
        return expr;
    }

    /**
     *
     * @param exprNode
     * @param attrs
     * @param db
     * @return
     */
    AbstractExpression parseOperationExpression(VoltXMLElement exprNode, Database db) {
        String type = exprNode.attributes.get("type");
        ExpressionType exprType = ExpressionType.get(type);
        AbstractExpression expr = null;

        if (exprType == ExpressionType.INVALID) {
            throw new PlanningErrorException("Unsupported operation type '" + type + "'");
        }
        try {
            expr = exprType.getExpressionClass().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
        expr.setExpressionType(exprType);

        // If the operation type was 'simplecolumn' then it's going to turn
        // into a TVE and we need to bail out before we try parsing the
        // left and right subtrees
        if (expr instanceof TupleValueExpression)
        {
            return expr;
        }

        // Allow expressions to read expression-specific data from exprNode.
        // Looks like the design fully abstracts other volt classes from
        // the XML serialization?  Putting this here instead of in derived
        // Expression implementations.

        if (expr instanceof AggregateExpression) {
            String node;
            if ((node = exprNode.attributes.get("distinct")) != null) {
                AggregateExpression ae = (AggregateExpression)expr;
                ae.m_distinct = Boolean.parseBoolean(node);
            }
        }

        // get the first (left) node that is an element
        VoltXMLElement leftExprNode = exprNode.children.get(0);
        assert(leftExprNode != null);

        // get the second (right) node that is an element (might be null)
        VoltXMLElement rightExprNode = null;
        if (exprNode.children.size() > 1)
            rightExprNode = exprNode.children.get(1);

        // recursively parse the left subtree (could be another operator or
        // a constant/tuple/param value operand).
        AbstractExpression leftExpr = parseExpressionTree(leftExprNode, db);
        assert((leftExpr != null) || (exprType == ExpressionType.AGGREGATE_COUNT));
        expr.setLeft(leftExpr);

        if (expr.needsRightExpression()) {
            assert(rightExprNode != null);

            // recursively parse the right subtree
            AbstractExpression rightExpr = parseExpressionTree(rightExprNode, db);
            assert(rightExpr != null);
            expr.setRight(rightExpr);
        }

        return expr;
    }


    /**
     *
     * @param exprNode
     * @param db
     * @return a new Function Expression
     */
    AbstractExpression parseFunctionExpression(VoltXMLElement exprNode, Database db) {
        String name = exprNode.attributes.get("name").toLowerCase();
        String disabled = exprNode.attributes.get("disabled");
        if (disabled != null) {
            throw new PlanningErrorException("Function '" + name + "' is not supported in VoltDB: " + disabled);
        }
        String value_type_name = exprNode.attributes.get("type");
        VoltType value_type = VoltType.typeFromString(value_type_name);
        String id = exprNode.attributes.get("id");
        assert(id != null);
        int idArg = Integer.parseInt(id);
        String parameter = exprNode.attributes.get("parameter");
        int parameter_idx = -1;
        if (parameter != null) {
            try {
                parameter_idx = Integer.parseInt(parameter);
            } catch (NumberFormatException nfe) {}
        }
        String volt_alias = exprNode.attributes.get("volt_alias");
        if (volt_alias == null) {
            volt_alias = name; // volt shares the function name with HSQL
        }

        ArrayList<AbstractExpression> args = new ArrayList<AbstractExpression>();
        for (VoltXMLElement argNode : exprNode.children) {
            assert(argNode != null);
            // recursively parse each argument subtree (could be any kind of expression).
            AbstractExpression argExpr = parseExpressionTree(argNode, db);
            assert(argExpr != null);
            args.add(argExpr);
        }

        if (parameter_idx != -1) {
            // Avoid a non-castable types runtime exception by negotiating the type(s) of the parameterized function's result
            // and its parameter argument. Either of these types could be null or a specific supported value type, or a generic
            // NUMERIC. Replace any "generic" type (null or NUMERIC) with the more specific type without over-specifying
            // -- the BEST type might only become clear later when the context/caller of this function is parsed, so don't
            // risk guessing wrong here just for the sake of specificity.
            // There will be a "finalize" pass over the completed expression tree to finish specifying any remaining "generics".
            AbstractExpression param_arg = args.get(parameter_idx);
            // But DO use the type chosen by HSQL for the parameterized function as a specific type hint
            // for numeric constant arguments that could either go decimal or float.
            VoltType param_type = param_arg.getValueType();
            // The heuristic for which types to change is that any type (parameter type or return type) specified so far,
            // including "NUMERIC" is better than nothing. And that anything else is better than NUMERIC.
            if (value_type != param_type) {
                if (value_type == null) {
                    value_type = param_type;
                } else if (value_type == VoltType.NUMERIC) {
                    if (param_type != null) {
                        value_type = param_type;
                    }
                    // Pushing a type DOWN to the argument is a lot like work, and not worth it just to
                    // propagate down a known NUMERIC return type,
                    // since it will just have to be re-specialized when a more specific type is inferred from
                    // the context or finalized when the expression is complete.
                } else if ((param_type == null) || (param_type == VoltType.NUMERIC)) {
                    // The only purpose of refining the parameter argument's type is to force a more specific
                    // refinement than NUMERIC as implied by HSQL, in case that might be more specific than
                    // what can be be inferred later from the function call context.
                    param_arg.refineValueType(value_type);
                }
            }
        }

        FunctionExpression expr = new FunctionExpression();
        expr.setAttributes(name, volt_alias, idArg, parameter_idx);
        if (value_type != null) {
            expr.setValueType(value_type);
            expr.setValueSize(value_type.getMaxLengthInBytes());
        }
        expr.setArgs(args);
        return expr;
    }

    /**
     * Parse the scan_columns element out of the HSQL-generated XML.
     * Fills scanColumns with a list of the columns used in the plan, hashed by
     * table name.
     *
     * @param columnsNode
     * @param db
     */
    void parseScanColumns(VoltXMLElement columnsNode, Database db)
    {
        scanColumns = new HashMap<String, ArrayList<SchemaColumn>>();

        for (VoltXMLElement child : columnsNode.children) {
            assert(child.name.equals("columnref"));
            AbstractExpression col_exp = parseExpressionTree(child, db);
            // TupleValueExpressions are always specifically typed,
            // so there is no need for expression type specialization, here.
            assert(col_exp != null);
            assert(col_exp instanceof TupleValueExpression);
            TupleValueExpression tve = (TupleValueExpression)col_exp;
            SchemaColumn col = new SchemaColumn(tve.getTableName(),
                                                tve.getColumnName(),
                                                tve.getColumnAlias(),
                                                col_exp);
            ArrayList<SchemaColumn> table_cols = null;
            if (!scanColumns.containsKey(col.getTableName()))
            {
                table_cols = new ArrayList<SchemaColumn>();
                scanColumns.put(col.getTableName(), table_cols);
            }
            table_cols = scanColumns.get(col.getTableName());
            table_cols.add(col);
        }
    }

    /**
     *
     * @param tablesNode
     * @param db
     */
    private void parseTables(VoltXMLElement tablesNode, Database db) {
        for (VoltXMLElement node : tablesNode.children) {
            if (node.name.equalsIgnoreCase("tablescan")) {
                String tableName = node.attributes.get("table");
                Table table = db.getTables().getIgnoreCase(tableName);
                assert(table != null);
                tableList.add(table);
            }
        }
    }

    private void parseParameters(VoltXMLElement paramsNode, Database db) {
        paramList = new VoltType[paramsNode.children.size()];

        for (VoltXMLElement node : paramsNode.children) {
            if (node.name.equalsIgnoreCase("parameter")) {
                long id = Long.parseLong(node.attributes.get("id"));
                int index = Integer.parseInt(node.attributes.get("index"));
                String typeName = node.attributes.get("type");
                VoltType type = VoltType.typeFromString(typeName);
                paramsById.put(id, index);
                paramList[index] = type;
            }
        }
    }

    /**
     *
     * @param db
     */
    void analyzeWhereExpression(Database db) {

        // nothing to do if there's no where expression
        if (where == null) return;

        // this first chunk of code breaks the code into a list of expression that
        // all have to be true for the where clause to be true

        ArrayDeque<AbstractExpression> in = new ArrayDeque<AbstractExpression>();
        ArrayDeque<AbstractExpression> out = new ArrayDeque<AbstractExpression>();
        in.add(where);

        AbstractExpression inExpr = null;
        while ((inExpr = in.poll()) != null) {
            if (inExpr.getExpressionType() == ExpressionType.CONJUNCTION_AND) {
                in.add(inExpr.getLeft());
                in.add(inExpr.getRight());
            }
            else {
                out.add(inExpr);
            }
        }

        // the where selection list contains all the clauses
        whereSelectionList.addAll(out);

        // This next bit of code identifies which tables get classified how
        HashSet<Table> tableSet = new HashSet<Table>();
        for (AbstractExpression expr : whereSelectionList) {
            tableSet.clear();
            getTablesForExpression(db, expr, tableSet);
            if (tableSet.size() == 0) {
                noTableSelectionList.add(expr);
            }
            else if (tableSet.size() == 1) {
                Table table = (Table) tableSet.toArray()[0];

                ArrayList<AbstractExpression> exprs;
                if (tableFilterList.containsKey(table)) {
                    exprs = tableFilterList.get(table);
                }
                else {
                    exprs = new ArrayList<AbstractExpression>();
                    tableFilterList.put(table, exprs);
                }
                expr.m_isJoiningClause = false;
                addExprToEquivalenceSets(expr);
                exprs.add(expr);
            }
            else if (tableSet.size() == 2) {
                TablePair pair = new TablePair();
                pair.t1 = (Table) tableSet.toArray()[0];
                pair.t2 = (Table) tableSet.toArray()[1];

                ArrayList<AbstractExpression> exprs;
                if (joinSelectionList.containsKey(pair)) {
                    exprs = joinSelectionList.get(pair);
                }
                else {
                    exprs = new ArrayList<AbstractExpression>();
                    joinSelectionList.put(pair, exprs);
                }
                expr.m_isJoiningClause = true;
                addExprToEquivalenceSets(expr);
                exprs.add(expr);
            }
            else if (tableSet.size() > 2) {
                multiTableSelectionList.add(expr);
            }
        }
    }

    /**
     *
     * @param db
     * @param expr
     * @param tables
     */
    void getTablesForExpression(Database db, AbstractExpression expr, HashSet<Table> tables) {
        List<TupleValueExpression> tves = ExpressionUtil.getTupleValueExpressions(expr);
        for (TupleValueExpression tupleExpr : tves) {
            String tableName = tupleExpr.getTableName();
            Table table = db.getTables().getIgnoreCase(tableName);
            tables.add(table);
        }
    }

    @Override
    public String toString() {
        String retval = "SQL:\n\t" + sql + "\n";

        retval += "PARAMETERS:\n\t";
        for (VoltType param : paramList) {
            retval += param.toString() + " ";
        }

        retval += "\nTABLE SOURCES:\n\t";
        for (Table table : tableList) {
            retval += table.getTypeName() + " ";
        }

        retval += "\nSCAN COLUMNS:\n";
        if (scanColumns != null)
        {
            for (String table : scanColumns.keySet())
            {
                retval += "\tTable: " + table + ":\n";
                for (SchemaColumn col : scanColumns.get(table))
                {
                    retval += "\t\tColumn: " + col.getColumnName() + ": ";
                    retval += col.getExpression().toString() + "\n";
                }
            }
        }
        else
        {
            retval += "\tALL\n";
        }

        if (where != null) {
            retval += "\nWHERE:\n";
            retval += "\t" + where.toString() + "\n";

            retval += "WHERE SELECTION LIST:\n";
            int i = 0;
            for (AbstractExpression expr : whereSelectionList)
                retval += "\t(" + String.valueOf(i++) + ") " + expr.toString() + "\n";

            retval += "NO TABLE SELECTION LIST:\n";
            i = 0;
            for (AbstractExpression expr : noTableSelectionList)
                retval += "\t(" + String.valueOf(i++) + ") " + expr.toString() + "\n";

            retval += "TABLE FILTER LIST:\n";
            for (Entry<Table, ArrayList<AbstractExpression>> pair : tableFilterList.entrySet()) {
                i = 0;
                retval += "\tTABLE: " + pair.getKey().getTypeName() + "\n";
                for (AbstractExpression expr : pair.getValue())
                    retval += "\t\t(" + String.valueOf(i++) + ") " + expr.toString() + "\n";
            }

            retval += "JOIN CLAUSE LIST:\n";
            for (Entry<TablePair, ArrayList<AbstractExpression>> pair : joinSelectionList.entrySet()) {
                i = 0;
                retval += "\tTABLES: " + pair.getKey().t1.getTypeName() + " and " + pair.getKey().t2.getTypeName() + "\n";
                for (AbstractExpression expr : pair.getValue())
                    retval += "\t\t(" + String.valueOf(i++) + ") " + expr.toString() + "\n";
            }
        }
        return retval;
    }

    public int paramIndexById(long paramId) {
        if (paramId == -1) {
            return -1;
        }
        assert(paramsById.containsKey(paramId));
        return paramsById.get(paramId);
    }

    private void addExprToEquivalenceSets(AbstractExpression expr) {
        // Ignore expressions that are not of COMPARE_EQUAL type
        if (expr.getExpressionType() != ExpressionType.COMPARE_EQUAL) {
            return;
        }

        AbstractExpression leftExpr = expr.getLeft();
        AbstractExpression rightExpr = expr.getRight();
        // Can't use an expression based on a column value that is not just a simple column value.
        if ( ( ! (leftExpr instanceof TupleValueExpression)) && leftExpr.hasAnySubexpressionOfClass(TupleValueExpression.class) ) {
            return;
        }
        if ( ( ! (rightExpr instanceof TupleValueExpression)) && rightExpr.hasAnySubexpressionOfClass(TupleValueExpression.class) ) {
            return;
        }

        // Any two asserted-equal expressions need to map to the same equivalence set,
        // which must contain them and must be the only such set that contains them.
        Set<AbstractExpression> eqSet1 = null;
        if (valueEquivalence.containsKey(leftExpr)) {
            eqSet1 = valueEquivalence.get(leftExpr);
        }
        if (valueEquivalence.containsKey(rightExpr)) {
            Set<AbstractExpression> eqSet2 = valueEquivalence.get(rightExpr);
            if (eqSet1 == null) {
                // Add new leftExpr into existing rightExpr's eqSet.
                valueEquivalence.put(leftExpr, eqSet2);
                eqSet2.add(leftExpr);
            } else {
                // Merge eqSets, re-mapping all the rightExpr's equivalents into leftExpr's eqset.
                for (AbstractExpression eqMember : eqSet2) {
                    eqSet1.add(eqMember);
                    valueEquivalence.put(eqMember, eqSet1);
                }
            }
        } else {
            if (eqSet1 == null) {
                // Both leftExpr and rightExpr are new -- add leftExpr to the new eqSet first.
                eqSet1 = new HashSet<AbstractExpression>();
                valueEquivalence.put(leftExpr, eqSet1);
                eqSet1.add(leftExpr);
            }
            // Add new rightExpr into leftExpr's eqSet.
            valueEquivalence.put(rightExpr, eqSet1);
            eqSet1.add(rightExpr);
        }
    }

    /** Parse a where clause. This behavior is common to all kinds of statements.
     *  TODO: It's not clear why ParsedDeleteStmt has its own VERY SIMILAR code to do this in method parseCondition.
     *  There's a minor difference in how "ANDs" are modeled -- are they multiple condition nodes or
     *  single condition nodes with multiple children? That distinction may be due to an arbitrary difference
     *  in the parser's handling of different statements, but even if it's justified, this method could easily
     *  be extended to handle multiple multi-child conditionNodes.
     */
    protected void parseConditions(VoltXMLElement conditionNode, Database db) {
        if (conditionNode.children.size() == 0)
            return;

        VoltXMLElement exprNode = conditionNode.children.get(0);
        assert(where == null); // Should be non-reentrant -- not overwriting any previous value!
        where = parseExpressionTree(exprNode, db);
        assert(where != null);
        ExpressionUtil.finalizeValueTypes(where);
    }

}
