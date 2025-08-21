
/******************************************************************************************
 *
 * NOTA BENE: This file is automagically generated from the source class named
 *                org.voltdb.planner.eegentests.GenerateEETests.
 *            Please do not edit it unless you abandon all hope of regenerating it.
 *
 ******************************************************************************************/
#include "harness.h"

#include "catalog/cluster.h"
#include "catalog/table.h"
#include "plannodes/abstractplannode.h"
#include "storage/persistenttable.h"
#include "storage/temptable.h"
#include "storage/tableutil.h"
#include "test_utils/plan_testing_config.h"
#include "test_utils/LoadTableFrom.hpp"
#include "test_utils/plan_testing_baseclass.h"


namespace {
extern TestConfig allTests[];
};

class TestGeneratedPlans : public PlanTestingBaseClass<EngineTestTopend> {
public:
    /*
     * This constructor lets us set the global random seed for the
     * random number generator.  It would be better to have a seed
     * just for this test.  But that is not easily done.
     */
    TestGeneratedPlans(uint32_t randomSeed = (unsigned int)time(NULL)) {
        initialize(m_testDB, randomSeed);
    }

    ~TestGeneratedPlans() { }
protected:
    static DBConfig         m_testDB;
};

/*
 * All the test cases are here.
 */
TEST_F(TestGeneratedPlans, test_order_by) {
    static int testIndex = 0;
    executeTest(allTests[testIndex]);
}
TEST_F(TestGeneratedPlans, test_join) {
    static int testIndex = 1;
    executeTest(allTests[testIndex]);
}
TEST_F(TestGeneratedPlans, test_cache) {
    static int testIndex = 2;
    executeTest(allTests[testIndex]);
}


namespace {
/*
 * These are the names of all the columns.
 */
const char *AAA_ColumnNames[] = {
    "A",
    "B",
    "C",
};
const char *BBB_ColumnNames[] = {
    "A",
    "B",
    "C",
};
const char *CCC_ColumnNames[] = {
    "ID",
    "NAME",
    "DATA",
};
const char *XXX_ColumnNames[] = {
    "ID",
    "NAME",
    "DATA",
};
const char *TEST_ORDER_BY_ColumnNames[] = {
    "A",
    "B",
};
const char *TEST_JOIN_ColumnNames[] = {
    "A",
    "B",
    "C",
};


/*
 * These are the types of all the columns.
 */
const voltdb::ValueType AAA_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
};
const voltdb::ValueType BBB_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
};
const voltdb::ValueType CCC_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tVARCHAR,
    voltdb::ValueType::tVARCHAR,
};
const voltdb::ValueType XXX_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tVARCHAR,
    voltdb::ValueType::tVARCHAR,
};
const voltdb::ValueType TEST_ORDER_BY_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
};
const voltdb::ValueType TEST_JOIN_Types[] = {
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
    voltdb::ValueType::tINTEGER,
};


/*
 * These are the sizes of all the column data.
 */
const int32_t AAA_Sizes[] = {
    4,
    4,
    4,
};
const int32_t BBB_Sizes[] = {
    4,
    4,
    4,
};
const int32_t CCC_Sizes[] = {
    4,
    32,
    1024,
};
const int32_t XXX_Sizes[] = {
    4,
    32,
    1024,
};
const int32_t TEST_ORDER_BY_Sizes[] = {
    4,
    4,
};
const int32_t TEST_JOIN_Sizes[] = {
    4,
    4,
    4,
};


/*
 * These are the strings in each populated columns.
 * The data will either be integers or indices into this table.
 */
int32_t num_AAA_strings = 0;
const char *AAA_Strings[] = {
};
int32_t num_BBB_strings = 0;
const char *BBB_Strings[] = {
};
int32_t num_CCC_strings = 0;
const char *CCC_Strings[] = {
};
int32_t num_XXX_strings = 4;
const char *XXX_Strings[] = {
    "alpha",
    "beta",
    "gamma",
    "delta",
};
int32_t num_TEST_ORDER_BY_strings = 0;
const char *TEST_ORDER_BY_Strings[] = {
};
int32_t num_TEST_JOIN_strings = 0;
const char *TEST_JOIN_Strings[] = {
};


/*
 * This is the data in all columns.
 */
const int NUM_TABLE_ROWS_AAA = 15;
const int NUM_TABLE_COLS_AAA = 3;
const int AAAData[NUM_TABLE_ROWS_AAA * NUM_TABLE_COLS_AAA] = {
      1, 10,101,
      1, 10,102,
      1, 20,201,
      1, 20,202,
      1, 30,301,
      2, 10,101,
      2, 10,102,
      2, 20,201,
      2, 20,202,
      2, 30,301,
      3, 10,101,
      3, 10,102,
      3, 20,201,
      3, 20,202,
      3, 30,301,
};

const int NUM_TABLE_ROWS_BBB = 15;
const int NUM_TABLE_COLS_BBB = 3;
const int BBBData[NUM_TABLE_ROWS_BBB * NUM_TABLE_COLS_BBB] = {
      1, 10,101,
      1, 10,102,
      1, 20,201,
      1, 20,202,
      1, 30,301,
      2, 10,101,
      2, 10,102,
      2, 20,201,
      2, 20,202,
      2, 30,301,
      3, 10,101,
      3, 10,102,
      3, 20,201,
      3, 20,202,
      3, 30,301,
};

const int NUM_TABLE_ROWS_CCC = 10000;
const int NUM_TABLE_COLS_CCC = 3;
;
const int NUM_TABLE_ROWS_XXX = 2;
const int NUM_TABLE_COLS_XXX = 3;
const int XXXData[NUM_TABLE_ROWS_XXX * NUM_TABLE_COLS_XXX] = {
      1,  0,  1,
      2,  2,  3,
};

const int NUM_TABLE_ROWS_TEST_ORDER_BY = 15;
const int NUM_TABLE_COLS_TEST_ORDER_BY = 2;
const int TEST_ORDER_BYData[NUM_TABLE_ROWS_TEST_ORDER_BY * NUM_TABLE_COLS_TEST_ORDER_BY] = {
      1, 10,
      1, 10,
      1, 20,
      1, 20,
      1, 30,
      2, 10,
      2, 10,
      2, 20,
      2, 20,
      2, 30,
      3, 10,
      3, 10,
      3, 20,
      3, 20,
      3, 30,
};

const int NUM_TABLE_ROWS_TEST_JOIN = 45;
const int NUM_TABLE_COLS_TEST_JOIN = 3;
const int TEST_JOINData[NUM_TABLE_ROWS_TEST_JOIN * NUM_TABLE_COLS_TEST_JOIN] = {
      1, 10,101,
      1, 10,101,
      1, 10,101,
      1, 10,102,
      1, 10,102,
      1, 10,102,
      1, 20,201,
      1, 20,201,
      1, 20,201,
      1, 20,202,
      1, 20,202,
      1, 20,202,
      1, 30,301,
      1, 30,301,
      1, 30,301,
      2, 10,101,
      2, 10,101,
      2, 10,101,
      2, 10,102,
      2, 10,102,
      2, 10,102,
      2, 20,201,
      2, 20,201,
      2, 20,201,
      2, 20,202,
      2, 20,202,
      2, 20,202,
      2, 30,301,
      2, 30,301,
      2, 30,301,
      3, 10,101,
      3, 10,101,
      3, 10,101,
      3, 10,102,
      3, 10,102,
      3, 10,102,
      3, 20,201,
      3, 20,201,
      3, 20,201,
      3, 20,202,
      3, 20,202,
      3, 20,202,
      3, 30,301,
      3, 30,301,
      3, 30,301,
};



/*
 * These are the names of all the columns.
 */
/*
 * These knit together all the bits of data which form a table.
 */
const TableConfig AAAConfig = {
    "AAA",
    AAA_ColumnNames,
    AAA_Types,
    AAA_Sizes,
    NUM_TABLE_ROWS_AAA,
    NUM_TABLE_COLS_AAA,
    AAAData,
    AAA_Strings,
    num_AAA_strings
};
const TableConfig BBBConfig = {
    "BBB",
    BBB_ColumnNames,
    BBB_Types,
    BBB_Sizes,
    NUM_TABLE_ROWS_BBB,
    NUM_TABLE_COLS_BBB,
    BBBData,
    BBB_Strings,
    num_BBB_strings
};
const TableConfig CCCConfig = {
    "CCC",
    CCC_ColumnNames,
    CCC_Types,
    CCC_Sizes,
    NUM_TABLE_ROWS_CCC,
    NUM_TABLE_COLS_CCC,
    NULL,
    CCC_Strings,
    num_CCC_strings
};
const TableConfig XXXConfig = {
    "XXX",
    XXX_ColumnNames,
    XXX_Types,
    XXX_Sizes,
    NUM_TABLE_ROWS_XXX,
    NUM_TABLE_COLS_XXX,
    XXXData,
    XXX_Strings,
    num_XXX_strings
};
const TableConfig TEST_ORDER_BYConfig = {
    "TEST_ORDER_BY",
    TEST_ORDER_BY_ColumnNames,
    TEST_ORDER_BY_Types,
    TEST_ORDER_BY_Sizes,
    NUM_TABLE_ROWS_TEST_ORDER_BY,
    NUM_TABLE_COLS_TEST_ORDER_BY,
    TEST_ORDER_BYData,
    TEST_ORDER_BY_Strings,
    num_TEST_ORDER_BY_strings
};
const TableConfig TEST_JOINConfig = {
    "TEST_JOIN",
    TEST_JOIN_ColumnNames,
    TEST_JOIN_Types,
    TEST_JOIN_Sizes,
    NUM_TABLE_ROWS_TEST_JOIN,
    NUM_TABLE_COLS_TEST_JOIN,
    TEST_JOINData,
    TEST_JOIN_Strings,
    num_TEST_JOIN_strings
};


/*
 * This holds all the persistent tables.
 */
const TableConfig *allTables[] = {
    &AAAConfig,
    &BBBConfig,
    &CCCConfig,
    &XXXConfig,
    &TEST_ORDER_BYConfig,
    &TEST_JOINConfig,
};


TestConfig allTests[3] = {
    {
        // SQL Statement
        "select A, B from AAA order by A, B;",
        // Failure is expected
        false,
        // Plan String
        "{\n"
        "    \"EXECUTE_LIST\": [\n"
        "        3,\n"
        "        2,\n"
        "        1\n"
        "    ],\n"
        "    \"IS_LARGE_QUERY\": false,\n"
        "    \"PLAN_NODES\": [\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [2],\n"
        "            \"ID\": 1,\n"
        "            \"PLAN_NODE_TYPE\": \"SEND\"\n"
        "        },\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [3],\n"
        "            \"ID\": 2,\n"
        "            \"PLAN_NODE_TYPE\": \"ORDERBY\",\n"
        "            \"SORT_COLUMNS\": [\n"
        "                {\n"
        "                    \"SORT_DIRECTION\": \"ASC\",\n"
        "                    \"SORT_EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 0,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"SORT_DIRECTION\": \"ASC\",\n"
        "                    \"SORT_EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 1,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                }\n"
        "            ]\n"
        "        },\n"
        "        {\n"
        "            \"ID\": 3,\n"
        "            \"INLINE_NODES\": [{\n"
        "                \"ID\": 4,\n"
        "                \"OUTPUT_SCHEMA\": [\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"A\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 0,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    },\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"B\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 1,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    }\n"
        "                ],\n"
        "                \"PLAN_NODE_TYPE\": \"PROJECTION\"\n"
        "            }],\n"
        "            \"PLAN_NODE_TYPE\": \"SEQSCAN\",\n"
        "            \"TARGET_TABLE_ALIAS\": \"AAA\",\n"
        "            \"TARGET_TABLE_NAME\": \"AAA\"\n"
        "        }\n"
        "    ]\n"
        "}",
        &TEST_ORDER_BYConfig
    },
    {
        // SQL Statement
        "select AAA.A, AAA.B, BBB.C from AAA join BBB on AAA.C = BBB.C order by AAA.A, AAA.B, AAA.C;",
        // Failure is expected
        false,
        // Plan String
        "{\n"
        "    \"EXECUTE_LIST\": [\n"
        "        5,\n"
        "        7,\n"
        "        4,\n"
        "        3,\n"
        "        2,\n"
        "        1\n"
        "    ],\n"
        "    \"IS_LARGE_QUERY\": false,\n"
        "    \"PLAN_NODES\": [\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [2],\n"
        "            \"ID\": 1,\n"
        "            \"PLAN_NODE_TYPE\": \"SEND\"\n"
        "        },\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [3],\n"
        "            \"ID\": 2,\n"
        "            \"OUTPUT_SCHEMA\": [\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"A\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 0,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"B\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 1,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"C\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 3,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                }\n"
        "            ],\n"
        "            \"PLAN_NODE_TYPE\": \"PROJECTION\"\n"
        "        },\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [4],\n"
        "            \"ID\": 3,\n"
        "            \"PLAN_NODE_TYPE\": \"ORDERBY\",\n"
        "            \"SORT_COLUMNS\": [\n"
        "                {\n"
        "                    \"SORT_DIRECTION\": \"ASC\",\n"
        "                    \"SORT_EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 0,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"SORT_DIRECTION\": \"ASC\",\n"
        "                    \"SORT_EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 1,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"SORT_DIRECTION\": \"ASC\",\n"
        "                    \"SORT_EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 2,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                }\n"
        "            ]\n"
        "        },\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [\n"
        "                5,\n"
        "                7\n"
        "            ],\n"
        "            \"ID\": 4,\n"
        "            \"JOIN_PREDICATE\": {\n"
        "                \"LEFT\": {\n"
        "                    \"COLUMN_IDX\": 0,\n"
        "                    \"TABLE_IDX\": 1,\n"
        "                    \"TYPE\": 32,\n"
        "                    \"VALUE_TYPE\": 5\n"
        "                },\n"
        "                \"RIGHT\": {\n"
        "                    \"COLUMN_IDX\": 2,\n"
        "                    \"TYPE\": 32,\n"
        "                    \"VALUE_TYPE\": 5\n"
        "                },\n"
        "                \"TYPE\": 10,\n"
        "                \"VALUE_TYPE\": 23\n"
        "            },\n"
        "            \"JOIN_TYPE\": \"INNER\",\n"
        "            \"OUTPUT_SCHEMA\": [\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"A\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 0,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"B\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 1,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"C\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 2,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                },\n"
        "                {\n"
        "                    \"COLUMN_NAME\": \"C\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 3,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                }\n"
        "            ],\n"
        "            \"PLAN_NODE_TYPE\": \"NESTLOOP\",\n"
        "            \"PRE_JOIN_PREDICATE\": null,\n"
        "            \"WHERE_PREDICATE\": null\n"
        "        },\n"
        "        {\n"
        "            \"ID\": 5,\n"
        "            \"INLINE_NODES\": [{\n"
        "                \"ID\": 6,\n"
        "                \"OUTPUT_SCHEMA\": [\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"A\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 0,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    },\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"B\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 1,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    },\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"C\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 2,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    }\n"
        "                ],\n"
        "                \"PLAN_NODE_TYPE\": \"PROJECTION\"\n"
        "            }],\n"
        "            \"PLAN_NODE_TYPE\": \"SEQSCAN\",\n"
        "            \"TARGET_TABLE_ALIAS\": \"AAA\",\n"
        "            \"TARGET_TABLE_NAME\": \"AAA\"\n"
        "        },\n"
        "        {\n"
        "            \"ID\": 7,\n"
        "            \"INLINE_NODES\": [{\n"
        "                \"ID\": 8,\n"
        "                \"OUTPUT_SCHEMA\": [{\n"
        "                    \"COLUMN_NAME\": \"C\",\n"
        "                    \"EXPRESSION\": {\n"
        "                        \"COLUMN_IDX\": 2,\n"
        "                        \"TYPE\": 32,\n"
        "                        \"VALUE_TYPE\": 5\n"
        "                    }\n"
        "                }],\n"
        "                \"PLAN_NODE_TYPE\": \"PROJECTION\"\n"
        "            }],\n"
        "            \"PLAN_NODE_TYPE\": \"SEQSCAN\",\n"
        "            \"TARGET_TABLE_ALIAS\": \"BBB\",\n"
        "            \"TARGET_TABLE_NAME\": \"BBB\"\n"
        "        }\n"
        "    ]\n"
        "}",
        &TEST_JOINConfig
    },
    {
        // SQL Statement
        "select * from CCC;",
        // Failure is expected
        false,
        // Plan String
        "{\n"
        "    \"EXECUTE_LIST\": [\n"
        "        2,\n"
        "        1\n"
        "    ],\n"
        "    \"IS_LARGE_QUERY\": false,\n"
        "    \"PLAN_NODES\": [\n"
        "        {\n"
        "            \"CHILDREN_IDS\": [2],\n"
        "            \"ID\": 1,\n"
        "            \"PLAN_NODE_TYPE\": \"SEND\"\n"
        "        },\n"
        "        {\n"
        "            \"ID\": 2,\n"
        "            \"INLINE_NODES\": [{\n"
        "                \"ID\": 3,\n"
        "                \"OUTPUT_SCHEMA\": [\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"ID\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 0,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_TYPE\": 5\n"
        "                        }\n"
        "                    },\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"NAME\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 1,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_SIZE\": 32,\n"
        "                            \"VALUE_TYPE\": 9\n"
        "                        }\n"
        "                    },\n"
        "                    {\n"
        "                        \"COLUMN_NAME\": \"DATA\",\n"
        "                        \"EXPRESSION\": {\n"
        "                            \"COLUMN_IDX\": 2,\n"
        "                            \"TYPE\": 32,\n"
        "                            \"VALUE_SIZE\": 1024,\n"
        "                            \"VALUE_TYPE\": 9\n"
        "                        }\n"
        "                    }\n"
        "                ],\n"
        "                \"PLAN_NODE_TYPE\": \"PROJECTION\"\n"
        "            }],\n"
        "            \"PLAN_NODE_TYPE\": \"SEQSCAN\",\n"
        "            \"TARGET_TABLE_ALIAS\": \"CCC\",\n"
        "            \"TARGET_TABLE_NAME\": \"CCC\"\n"
        "        }\n"
        "    ]\n"
        "}",
        NULL
    },
};

}

DBConfig TestGeneratedPlans::m_testDB =

{
    //
    // DDL.
    //
    "DROP TABLE T IF EXISTS;\n"
    "DROP TABLE R1 IF EXISTS;\n"
    "DROP TABLE AAA IF EXISTS;\n"
    "DROP TABLE COUNT_OUTPUT IF EXISTS;\n"
    "DROP TABLE TEST_SUM_OUTPUT IF EXISTS;\n"
    "DROP TABLE BBB IF EXISTS;\n"
    "DROP TABLE TEST_OUTPUT IF EXISTS;\n"
    "DROP TABLE CCC IF EXISTS;\n"
    "DROP TABLE CCCLONGANS IF EXISTS;\n"
    "DROP TABLE CCCSHORTANS IF EXISTS;\n"
    "DROP TABLE TEST_ORDER_BY IF EXISTS;\n"
    "DROP TABLE TEST_JOIN IF EXISTS;\n"
    "DROP TABLE XXX IF EXISTS;\n"
    "DROP TABLE RANK_OUTPUT IF EXISTS;\n"
    "DROP TABLE RANK_DENSE_OUTPUT IF EXISTS;\n"
    "DROP TABLE ROW_NUMBER_OUTPUT IF EXISTS;\n"
    "DROP TABLE ROW_NUMBER_WITHOUT_PARTITION_OUTPUT IF EXISTS;\n"
    "DROP TABLE P1 IF EXISTS;\n"
    "DROP TABLE P2 IF EXISTS;\n"
    "DROP TABLE T1 IF EXISTS;\n"
    "DROP TABLE T2 IF EXISTS;\n"
    "DROP TABLE PWEE_WITH_INDEX IF EXISTS;\n"
    "DROP TABLE IIANSWER IF EXISTS;\n"
    "\n"
    "CREATE TABLE T (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE R1 (\n"
    "  ID INTEGER NOT NULL,\n"
    "  TINY INTEGER NOT NULL,\n"
    "  BIG INTEGER NOT NULL,\n"
    "  PRIMARY KEY (ID)\n"
    ");\n"
    "\n"
    "CREATE TABLE AAA (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER\n"
    " );\n"
    " \n"
    "CREATE TABLE COUNT_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER,\n"
    "  D INTEGER\n"
    " );\n"
    " \n"
    " CREATE TABLE TEST_SUM_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER\n"
    "); \n"
    " CREATE TABLE BBB (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER\n"
    " );\n"
    " \n"
    "CREATE TABLE TEST_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER\n"
    ");\n"
    " \n"
    "CREATE TABLE CCC (\n"
    "  ID INTEGER,\n"
    "  NAME VARCHAR(32),\n"
    "  DATA VARCHAR(1024)\n"
    ");\n"
    "\n"
    "CREATE TABLE CCCLONGANS (\n"
    "  ID INTEGER,\n"
    "  NAME VARCHAR(32),\n"
    "  DATA VARCHAR(1024)\n"
    ");\n"
    "\n"
    "CREATE TABLE CCCSHORTANS (\n"
    "  ID INTEGER,\n"
    "  NAME VARCHAR(32),\n"
    "  DATA VARCHAR(1024)\n"
    ");\n"
    "\n"
    "CREATE TABLE TEST_ORDER_BY (\n"
    "  A INTEGER,\n"
    "  B INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE TEST_JOIN (\n"
    "  A   INTEGER,\n"
    "  B   INTEGER,\n"
    "  C   INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE XXX (\n"
    "  ID INTEGER PRIMARY KEY NOT NULL,\n"
    "  NAME VARCHAR(32),\n"
    "  DATA VARCHAR(1024)\n"
    ");\n"
    "\n"
    "CREATE TABLE RANK_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER,\n"
    "  R INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE RANK_DENSE_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER,\n"
    "  R INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE ROW_NUMBER_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER,\n"
    "  R INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE ROW_NUMBER_WITHOUT_PARTITION_OUTPUT (\n"
    "  A INTEGER,\n"
    "  B INTEGER,\n"
    "  C INTEGER,\n"
    "  R INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE T1 (\n"
    "  ID      BIGINT NOT NULL PRIMARY KEY,\n"
    "  AAA     INTEGER,\n"
    "  BBB     INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE T2 (\n"
    "  ID      BIGINT NOT NULL,\n"
    "  AAA     INTEGER,\n"
    "  BBB     INTEGER\n"
    ");\n"
    "\n"
    "CREATE TABLE P1 (\n"
    "  ID      BIGINT NOT NULL PRIMARY KEY,\n"
    "  AAA     INTEGER NOT NULL,\n"
    "  BBB     INTEGER\n"
    ");\n"
    "PARTITION TABLE P1 ON COLUMN ID;\n"
    "\n"
    "CREATE TABLE P2 (\n"
    "  ID      BIGINT NOT NULL,\n"
    "  AAA     INTEGER NOT NULL,\n"
    "  BBB     INTEGER,\n"
    ");\n"
    "PARTITION TABLE P2 ON COLUMN ID;\n"
    "\n"
    "CREATE TABLE POLYGONS (\n"
    "  ID BIGINT NOT NULL PRIMARY KEY,\n"
    "  POLY GEOGRAPHY\n"
    ");\n"
    "PARTITION TABLE POLYGONS ON COLUMN ID;\n"
    "\n"
    "",
    //
    // Catalog String
    //
    "add / clusters cluster\n"
    "set /clusters#cluster localepoch 0\n"
    "set $PREV securityEnabled false\n"
    "set $PREV httpdportno 0\n"
    "set $PREV jsonapi false\n"
    "set $PREV networkpartition false\n"
    "set $PREV heartbeatTimeout 0\n"
    "set $PREV useddlschema false\n"
    "set $PREV drConsumerEnabled false\n"
    "set $PREV drProducerEnabled false\n"
    "set $PREV drRole \"\"\n"
    "set $PREV drClusterId 0\n"
    "set $PREV drProducerPort 0\n"
    "set $PREV drMasterHost \"\"\n"
    "set $PREV drConsumerSslPropertyFile \"\"\n"
    "set $PREV globalFlushInterval 0\n"
    "set $PREV drFlushInterval 0\n"
    "set $PREV exportFlushInterval 0\n"
    "set $PREV preferredSource 0\n"
    "set $PREV conflictretention \"\"\n"
    "add /clusters#cluster databases database\n"
    "set /clusters#cluster/databases#database schema \"tSdMNDQ1MjRGNTAyMDU0NDE0MjRDNDUBDFgyMDQ5NDYyMDQ1NTg0OTUzNTQ1MzNCClovAAgyMzGyMQAENDEBArYzACgzNEY1NTRFNTQ1RgEKGDU0NTA1NTW6qQAINDQ1BcwYRjUzNTU0ROpLAAQ0MgECssMAGX7iwQAENDMBArJ2AAExODQzNEM0RjRFNDc0MTRFNc5BABw1MzQ4NEY1MkUnvkMAMvoABDI0IYYYMjVGNDI1ObLLAB1HGEE0RjQ5NEW2PwAAOAECtjMAADIhMQQ0QuopAh1DBDQ0QcEIMzQ19k8AGEY1NzVGNEVBwQw0MjQ1JUsARmEMzhIDYf5CTwAUNTc0OTU0JfFhaRBGNTA0MSX7ARYAOUFK7sIAADC6LgQINTAzvmkDvmIAADS+YgAQMDU3NDUhyTErADVFkCHjADW2YwIMNDk0OUFlBDUzAVMANWLqABgzNTI0NTQxYQShoTatBQwyODIwAQIANKlVAEUBJgQ0N0EcBDJDARgIMjA0CVlOGgCJDy4aAAgwMjmh7WZzAKXxFXUEOTSphzJDAAw0RTRGpZ9BqQhDNEMRiQQ1NGG5bfqSMgAUNDI0OTQ3ZbiSMAAANWF0CDk0REW0GDkyMDRCNDUBCAQyOAWqADmC3wDBnSFKFeH+VgExVn5YAWblBv6LADqLAFEVSrgBgqUAQVxuRQf+qwA6qwCCNAHtif53AEJ3AH55ADkINn4F/okAQokAdowD6eAAMhl3To4DMfAQRTQxNEQS4gkUNjQxNTI04dUoMTUyMjgzMzMyMjkVKAA0hWkIMTIwQigAGDEzMDMyMzSORgMOdwhCeQg1Hv6nAP6nADanAEbfCP6pAP6pAEKpADJwAjpFCRWtrqoEquECIm8JLmkAwVmlzA7YCEJtBmoeAEGuwZMAOTbQBXrpAg69CeFcFYNOQgJi/QVejwb+EwOyEwMqDAoysQkV066/AXHURr8HDRoaTAgyNAd+ZAEm8Aoh1QBFTq0K/q0A/q0ALq0AggsL/q0A/q0Aeq0AHj4KTQ+KaQv+0QD+0QAZ0RJlCxWPZY1h1gkEFrQJaeIAMmFlNgwKXqcDTboigQkRTjYqBBEm8fpWJgCC7AIS9QvawQARgxrJEFqDABEm+qkAEjEN2qkA9moBRroB/tMAFdMAMj6zCDFEDqwMDvELKfVqgwB2fAHeuwAB4XZyCSQ1MDRGNEM1OTQ3EgoPwc4efw1lBvb8AhFeBDIw4VQMNEY0N6GVMDUwNDg1OTIwMjkzQgo=\"\n"
    "set $PREV isActiveActiveDRed false\n"
    "set $PREV securityprovider \"\"\n"
    "add /clusters#cluster/databases#database groups administrator\n"
    "set /clusters#cluster/databases#database/groups#administrator admin true\n"
    "set $PREV defaultproc true\n"
    "set $PREV defaultprocread true\n"
    "set $PREV sql true\n"
    "set $PREV sqlread true\n"
    "set $PREV allproc true\n"
    "set $PREV compoundproc true\n"
    "add /clusters#cluster/databases#database groups user\n"
    "set /clusters#cluster/databases#database/groups#user admin false\n"
    "set $PREV defaultproc true\n"
    "set $PREV defaultprocread true\n"
    "set $PREV sql true\n"
    "set $PREV sqlread true\n"
    "set $PREV allproc true\n"
    "set $PREV compoundproc false\n"
    "add /clusters#cluster/databases#database tables AAA\n"
    "set /clusters#cluster/databases#database/tables#AAA isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"AAA|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#AAA columns A\n"
    "set /clusters#cluster/databases#database/tables#AAA/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#AAA columns B\n"
    "set /clusters#cluster/databases#database/tables#AAA/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#AAA columns C\n"
    "set /clusters#cluster/databases#database/tables#AAA/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables BBB\n"
    "set /clusters#cluster/databases#database/tables#BBB isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"BBB|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#BBB columns A\n"
    "set /clusters#cluster/databases#database/tables#BBB/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#BBB columns B\n"
    "set /clusters#cluster/databases#database/tables#BBB/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#BBB columns C\n"
    "set /clusters#cluster/databases#database/tables#BBB/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables CCC\n"
    "set /clusters#cluster/databases#database/tables#CCC isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"CCC|ivv\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#CCC columns DATA\n"
    "set /clusters#cluster/databases#database/tables#CCC/columns#DATA index 2\n"
    "set $PREV type 9\n"
    "set $PREV size 1024\n"
    "set $PREV nullable true\n"
    "set $PREV name \"DATA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCC columns ID\n"
    "set /clusters#cluster/databases#database/tables#CCC/columns#ID index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCC columns NAME\n"
    "set /clusters#cluster/databases#database/tables#CCC/columns#NAME index 1\n"
    "set $PREV type 9\n"
    "set $PREV size 32\n"
    "set $PREV nullable true\n"
    "set $PREV name \"NAME\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables CCCLONGANS\n"
    "set /clusters#cluster/databases#database/tables#CCCLONGANS isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"CCCLONGANS|ivv\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#CCCLONGANS columns DATA\n"
    "set /clusters#cluster/databases#database/tables#CCCLONGANS/columns#DATA index 2\n"
    "set $PREV type 9\n"
    "set $PREV size 1024\n"
    "set $PREV nullable true\n"
    "set $PREV name \"DATA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCCLONGANS columns ID\n"
    "set /clusters#cluster/databases#database/tables#CCCLONGANS/columns#ID index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCCLONGANS columns NAME\n"
    "set /clusters#cluster/databases#database/tables#CCCLONGANS/columns#NAME index 1\n"
    "set $PREV type 9\n"
    "set $PREV size 32\n"
    "set $PREV nullable true\n"
    "set $PREV name \"NAME\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables CCCSHORTANS\n"
    "set /clusters#cluster/databases#database/tables#CCCSHORTANS isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"CCCSHORTANS|ivv\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#CCCSHORTANS columns DATA\n"
    "set /clusters#cluster/databases#database/tables#CCCSHORTANS/columns#DATA index 2\n"
    "set $PREV type 9\n"
    "set $PREV size 1024\n"
    "set $PREV nullable true\n"
    "set $PREV name \"DATA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCCSHORTANS columns ID\n"
    "set /clusters#cluster/databases#database/tables#CCCSHORTANS/columns#ID index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#CCCSHORTANS columns NAME\n"
    "set /clusters#cluster/databases#database/tables#CCCSHORTANS/columns#NAME index 1\n"
    "set $PREV type 9\n"
    "set $PREV size 32\n"
    "set $PREV nullable true\n"
    "set $PREV name \"NAME\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables COUNT_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#COUNT_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"COUNT_OUTPUT|iiii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#COUNT_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#COUNT_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#COUNT_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#COUNT_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#COUNT_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#COUNT_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#COUNT_OUTPUT columns D\n"
    "set /clusters#cluster/databases#database/tables#COUNT_OUTPUT/columns#D index 3\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"D\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables P1\n"
    "set /clusters#cluster/databases#database/tables#P1 isreplicated false\n"
    "set $PREV partitioncolumn /clusters#cluster/databases#database/tables#P1/columns#ID\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"P1|bii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#P1 columns AAA\n"
    "set /clusters#cluster/databases#database/tables#P1/columns#AAA index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"AAA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#P1 columns BBB\n"
    "set /clusters#cluster/databases#database/tables#P1/columns#BBB index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"BBB\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#P1 columns ID\n"
    "set /clusters#cluster/databases#database/tables#P1/columns#ID index 0\n"
    "set $PREV type 6\n"
    "set $PREV size 8\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#P1 indexes VOLTDB_AUTOGEN_IDX_PK_P1_ID\n"
    "set /clusters#cluster/databases#database/tables#P1/indexes#VOLTDB_AUTOGEN_IDX_PK_P1_ID unique true\n"
    "set $PREV assumeUnique false\n"
    "set $PREV migrating false\n"
    "set $PREV countable true\n"
    "set $PREV type 1\n"
    "set $PREV expressionsjson \"\"\n"
    "set $PREV predicatejson \"\"\n"
    "set $PREV isSafeWithNonemptySources true\n"
    "add /clusters#cluster/databases#database/tables#P1/indexes#VOLTDB_AUTOGEN_IDX_PK_P1_ID columns ID\n"
    "set /clusters#cluster/databases#database/tables#P1/indexes#VOLTDB_AUTOGEN_IDX_PK_P1_ID/columns#ID index 0\n"
    "set $PREV column /clusters#cluster/databases#database/tables#P1/columns#ID\n"
    "add /clusters#cluster/databases#database/tables#P1 constraints VOLTDB_AUTOGEN_CT__PK_P1_ID\n"
    "set /clusters#cluster/databases#database/tables#P1/constraints#VOLTDB_AUTOGEN_CT__PK_P1_ID type 4\n"
    "set $PREV oncommit \"\"\n"
    "set $PREV index /clusters#cluster/databases#database/tables#P1/indexes#VOLTDB_AUTOGEN_IDX_PK_P1_ID\n"
    "set $PREV foreignkeytable null\n"
    "add /clusters#cluster/databases#database tables P2\n"
    "set /clusters#cluster/databases#database/tables#P2 isreplicated false\n"
    "set $PREV partitioncolumn /clusters#cluster/databases#database/tables#P2/columns#ID\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"P2|bii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#P2 columns AAA\n"
    "set /clusters#cluster/databases#database/tables#P2/columns#AAA index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"AAA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#P2 columns BBB\n"
    "set /clusters#cluster/databases#database/tables#P2/columns#BBB index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"BBB\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#P2 columns ID\n"
    "set /clusters#cluster/databases#database/tables#P2/columns#ID index 0\n"
    "set $PREV type 6\n"
    "set $PREV size 8\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables POLYGONS\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS isreplicated false\n"
    "set $PREV partitioncolumn /clusters#cluster/databases#database/tables#POLYGONS/columns#ID\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"POLYGONS|bg\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#POLYGONS columns ID\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS/columns#ID index 0\n"
    "set $PREV type 6\n"
    "set $PREV size 8\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#POLYGONS columns POLY\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS/columns#POLY index 1\n"
    "set $PREV type 27\n"
    "set $PREV size 32768\n"
    "set $PREV nullable true\n"
    "set $PREV name \"POLY\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#POLYGONS indexes VOLTDB_AUTOGEN_IDX_PK_POLYGONS_ID\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS/indexes#VOLTDB_AUTOGEN_IDX_PK_POLYGONS_ID unique true\n"
    "set $PREV assumeUnique false\n"
    "set $PREV migrating false\n"
    "set $PREV countable true\n"
    "set $PREV type 1\n"
    "set $PREV expressionsjson \"\"\n"
    "set $PREV predicatejson \"\"\n"
    "set $PREV isSafeWithNonemptySources true\n"
    "add /clusters#cluster/databases#database/tables#POLYGONS/indexes#VOLTDB_AUTOGEN_IDX_PK_POLYGONS_ID columns ID\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS/indexes#VOLTDB_AUTOGEN_IDX_PK_POLYGONS_ID/columns#ID index 0\n"
    "set $PREV column /clusters#cluster/databases#database/tables#POLYGONS/columns#ID\n"
    "add /clusters#cluster/databases#database/tables#POLYGONS constraints VOLTDB_AUTOGEN_CT__PK_POLYGONS_ID\n"
    "set /clusters#cluster/databases#database/tables#POLYGONS/constraints#VOLTDB_AUTOGEN_CT__PK_POLYGONS_ID type 4\n"
    "set $PREV oncommit \"\"\n"
    "set $PREV index /clusters#cluster/databases#database/tables#POLYGONS/indexes#VOLTDB_AUTOGEN_IDX_PK_POLYGONS_ID\n"
    "set $PREV foreignkeytable null\n"
    "add /clusters#cluster/databases#database tables R1\n"
    "set /clusters#cluster/databases#database/tables#R1 isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"R1|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#R1 columns BIG\n"
    "set /clusters#cluster/databases#database/tables#R1/columns#BIG index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"BIG\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#R1 columns ID\n"
    "set /clusters#cluster/databases#database/tables#R1/columns#ID index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#R1 columns TINY\n"
    "set /clusters#cluster/databases#database/tables#R1/columns#TINY index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"TINY\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#R1 indexes VOLTDB_AUTOGEN_IDX_PK_R1_ID\n"
    "set /clusters#cluster/databases#database/tables#R1/indexes#VOLTDB_AUTOGEN_IDX_PK_R1_ID unique true\n"
    "set $PREV assumeUnique false\n"
    "set $PREV migrating false\n"
    "set $PREV countable true\n"
    "set $PREV type 1\n"
    "set $PREV expressionsjson \"\"\n"
    "set $PREV predicatejson \"\"\n"
    "set $PREV isSafeWithNonemptySources true\n"
    "add /clusters#cluster/databases#database/tables#R1/indexes#VOLTDB_AUTOGEN_IDX_PK_R1_ID columns ID\n"
    "set /clusters#cluster/databases#database/tables#R1/indexes#VOLTDB_AUTOGEN_IDX_PK_R1_ID/columns#ID index 0\n"
    "set $PREV column /clusters#cluster/databases#database/tables#R1/columns#ID\n"
    "add /clusters#cluster/databases#database/tables#R1 constraints VOLTDB_AUTOGEN_CT__PK_R1_ID\n"
    "set /clusters#cluster/databases#database/tables#R1/constraints#VOLTDB_AUTOGEN_CT__PK_R1_ID type 4\n"
    "set $PREV oncommit \"\"\n"
    "set $PREV index /clusters#cluster/databases#database/tables#R1/indexes#VOLTDB_AUTOGEN_IDX_PK_R1_ID\n"
    "set $PREV foreignkeytable null\n"
    "add /clusters#cluster/databases#database tables RANK_DENSE_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"RANK_DENSE_OUTPUT|iiii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT columns R\n"
    "set /clusters#cluster/databases#database/tables#RANK_DENSE_OUTPUT/columns#R index 3\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"R\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables RANK_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#RANK_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"RANK_OUTPUT|iiii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#RANK_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#RANK_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#RANK_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#RANK_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#RANK_OUTPUT columns R\n"
    "set /clusters#cluster/databases#database/tables#RANK_OUTPUT/columns#R index 3\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"R\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables ROW_NUMBER_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"ROW_NUMBER_OUTPUT|iiii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT columns R\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_OUTPUT/columns#R index 3\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"R\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables ROW_NUMBER_WITHOUT_PARTITION_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"ROW_NUMBER_WITHOUT_PARTITION_OUTPUT|iiii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT columns R\n"
    "set /clusters#cluster/databases#database/tables#ROW_NUMBER_WITHOUT_PARTITION_OUTPUT/columns#R index 3\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"R\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables T\n"
    "set /clusters#cluster/databases#database/tables#T isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"T|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#T columns A\n"
    "set /clusters#cluster/databases#database/tables#T/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T columns B\n"
    "set /clusters#cluster/databases#database/tables#T/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T columns C\n"
    "set /clusters#cluster/databases#database/tables#T/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables T1\n"
    "set /clusters#cluster/databases#database/tables#T1 isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"T1|bii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#T1 columns AAA\n"
    "set /clusters#cluster/databases#database/tables#T1/columns#AAA index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"AAA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T1 columns BBB\n"
    "set /clusters#cluster/databases#database/tables#T1/columns#BBB index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"BBB\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T1 columns ID\n"
    "set /clusters#cluster/databases#database/tables#T1/columns#ID index 0\n"
    "set $PREV type 6\n"
    "set $PREV size 8\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T1 indexes VOLTDB_AUTOGEN_IDX_PK_T1_ID\n"
    "set /clusters#cluster/databases#database/tables#T1/indexes#VOLTDB_AUTOGEN_IDX_PK_T1_ID unique true\n"
    "set $PREV assumeUnique false\n"
    "set $PREV migrating false\n"
    "set $PREV countable true\n"
    "set $PREV type 1\n"
    "set $PREV expressionsjson \"\"\n"
    "set $PREV predicatejson \"\"\n"
    "set $PREV isSafeWithNonemptySources true\n"
    "add /clusters#cluster/databases#database/tables#T1/indexes#VOLTDB_AUTOGEN_IDX_PK_T1_ID columns ID\n"
    "set /clusters#cluster/databases#database/tables#T1/indexes#VOLTDB_AUTOGEN_IDX_PK_T1_ID/columns#ID index 0\n"
    "set $PREV column /clusters#cluster/databases#database/tables#T1/columns#ID\n"
    "add /clusters#cluster/databases#database/tables#T1 constraints VOLTDB_AUTOGEN_CT__PK_T1_ID\n"
    "set /clusters#cluster/databases#database/tables#T1/constraints#VOLTDB_AUTOGEN_CT__PK_T1_ID type 4\n"
    "set $PREV oncommit \"\"\n"
    "set $PREV index /clusters#cluster/databases#database/tables#T1/indexes#VOLTDB_AUTOGEN_IDX_PK_T1_ID\n"
    "set $PREV foreignkeytable null\n"
    "add /clusters#cluster/databases#database tables T2\n"
    "set /clusters#cluster/databases#database/tables#T2 isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"T2|bii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#T2 columns AAA\n"
    "set /clusters#cluster/databases#database/tables#T2/columns#AAA index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"AAA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T2 columns BBB\n"
    "set /clusters#cluster/databases#database/tables#T2/columns#BBB index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"BBB\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#T2 columns ID\n"
    "set /clusters#cluster/databases#database/tables#T2/columns#ID index 0\n"
    "set $PREV type 6\n"
    "set $PREV size 8\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables TEST_JOIN\n"
    "set /clusters#cluster/databases#database/tables#TEST_JOIN isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"TEST_JOIN|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#TEST_JOIN columns A\n"
    "set /clusters#cluster/databases#database/tables#TEST_JOIN/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_JOIN columns B\n"
    "set /clusters#cluster/databases#database/tables#TEST_JOIN/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_JOIN columns C\n"
    "set /clusters#cluster/databases#database/tables#TEST_JOIN/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables TEST_ORDER_BY\n"
    "set /clusters#cluster/databases#database/tables#TEST_ORDER_BY isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"TEST_ORDER_BY|ii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#TEST_ORDER_BY columns A\n"
    "set /clusters#cluster/databases#database/tables#TEST_ORDER_BY/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_ORDER_BY columns B\n"
    "set /clusters#cluster/databases#database/tables#TEST_ORDER_BY/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables TEST_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#TEST_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"TEST_OUTPUT|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#TEST_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#TEST_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#TEST_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#TEST_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables TEST_SUM_OUTPUT\n"
    "set /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"TEST_SUM_OUTPUT|iii\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT columns A\n"
    "set /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT/columns#A index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"A\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT columns B\n"
    "set /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT/columns#B index 1\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"B\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT columns C\n"
    "set /clusters#cluster/databases#database/tables#TEST_SUM_OUTPUT/columns#C index 2\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable true\n"
    "set $PREV name \"C\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database tables XXX\n"
    "set /clusters#cluster/databases#database/tables#XXX isreplicated true\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV estimatedtuplecount 0\n"
    "set $PREV materializer null\n"
    "set $PREV signature \"XXX|ivv\"\n"
    "set $PREV isDRed false\n"
    "set $PREV tableType 1\n"
    "set $PREV migrationTarget \"\"\n"
    "set $PREV topicName \"\"\n"
    "set $PREV topicKeyColumnNames \"\"\n"
    "set $PREV topicValueColumnNames \"\"\n"
    "add /clusters#cluster/databases#database/tables#XXX columns DATA\n"
    "set /clusters#cluster/databases#database/tables#XXX/columns#DATA index 2\n"
    "set $PREV type 9\n"
    "set $PREV size 1024\n"
    "set $PREV nullable true\n"
    "set $PREV name \"DATA\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#XXX columns ID\n"
    "set /clusters#cluster/databases#database/tables#XXX/columns#ID index 0\n"
    "set $PREV type 5\n"
    "set $PREV size 4\n"
    "set $PREV nullable false\n"
    "set $PREV name \"ID\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#XXX columns NAME\n"
    "set /clusters#cluster/databases#database/tables#XXX/columns#NAME index 1\n"
    "set $PREV type 9\n"
    "set $PREV size 32\n"
    "set $PREV nullable true\n"
    "set $PREV name \"NAME\"\n"
    "set $PREV defaultvalue null\n"
    "set $PREV defaulttype 0\n"
    "set $PREV aggregatetype 0\n"
    "set $PREV matviewsource null\n"
    "set $PREV matview null\n"
    "set $PREV inbytes false\n"
    "add /clusters#cluster/databases#database/tables#XXX indexes VOLTDB_AUTOGEN_IDX_PK_XXX_ID\n"
    "set /clusters#cluster/databases#database/tables#XXX/indexes#VOLTDB_AUTOGEN_IDX_PK_XXX_ID unique true\n"
    "set $PREV assumeUnique false\n"
    "set $PREV migrating false\n"
    "set $PREV countable true\n"
    "set $PREV type 1\n"
    "set $PREV expressionsjson \"\"\n"
    "set $PREV predicatejson \"\"\n"
    "set $PREV isSafeWithNonemptySources true\n"
    "add /clusters#cluster/databases#database/tables#XXX/indexes#VOLTDB_AUTOGEN_IDX_PK_XXX_ID columns ID\n"
    "set /clusters#cluster/databases#database/tables#XXX/indexes#VOLTDB_AUTOGEN_IDX_PK_XXX_ID/columns#ID index 0\n"
    "set $PREV column /clusters#cluster/databases#database/tables#XXX/columns#ID\n"
    "add /clusters#cluster/databases#database/tables#XXX constraints VOLTDB_AUTOGEN_CT__PK_XXX_ID\n"
    "set /clusters#cluster/databases#database/tables#XXX/constraints#VOLTDB_AUTOGEN_CT__PK_XXX_ID type 4\n"
    "set $PREV oncommit \"\"\n"
    "set $PREV index /clusters#cluster/databases#database/tables#XXX/indexes#VOLTDB_AUTOGEN_IDX_PK_XXX_ID\n"
    "set $PREV foreignkeytable null\n"
    "add /clusters#cluster/databases#database procedures testplansgenerator\n"
    "set /clusters#cluster/databases#database/procedures#testplansgenerator classname \"\"\n"
    "set $PREV readonly false\n"
    "set $PREV singlepartition false\n"
    "set $PREV everysite false\n"
    "set $PREV systemproc false\n"
    "set $PREV defaultproc false\n"
    "set $PREV hasjava false\n"
    "set $PREV hasseqscans false\n"
    "set $PREV language \"\"\n"
    "set $PREV partitiontable null\n"
    "set $PREV partitiontable2 null\n"
    "set $PREV partitioncolumn null\n"
    "set $PREV partitioncolumn2 null\n"
    "set $PREV partitionparameter 0\n"
    "set $PREV partitionparameter2 0\n"
    "set $PREV allowedInShutdown false\n"
    "set $PREV transactional false\n"
    "set $PREV restartable false\n"
    "",
    6,
    allTables
};


int main() {
     return TestSuite::globalInstance()->runAll();
}
