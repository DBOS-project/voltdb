#!/usr/bin/env python

# This file is part of VoltDB.
# Copyright (C) 2008-2022 Volt Active Data Inc.
#
# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
# OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
# ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.


# This file contains the "analytic-timestamp" test suite, which is a subset
# of the "analytic" tests, which in turn are a subset of those run by the
# "postgis" sqlcoverage Jenkins job.

# These tests only work when run against PostgreSQL, not HSQL. Hence, to run
# them on the command line, use something like the following:
#     ant -Dpostgresql=1 -Dsql_coverage_suite=postgis-analytic-timestamp-config.py sqlcoverage
{
    # Tests of SQL Analytic Windowed Functions (e.g. RANK, DENSE_RANK)
    "analytic-timestamp": {"schema": "timestamp-schema.py",
                           "ddl": "DDL.sql",
                           "template": "analytic-timestamp.sql",
                           "normalizer": "normalizer.py"},
}
