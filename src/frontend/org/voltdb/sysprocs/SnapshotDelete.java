/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.sysprocs;

import java.util.List;
import java.util.Map;

import org.voltdb.DependencyPair;
import org.voltdb.ParameterSet;
import org.voltdb.SystemProcedureExecutionContext;
import org.voltdb.VoltDB;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;

public class SnapshotDelete extends VoltSystemProcedure {

    @Override
    public long[] getPlanFragmentIds() {
        return new long[]{SysProcFragmentId.PF_snapshotDelete, SysProcFragmentId.PF_snapshotDeleteResults};
    }

    @Override
    public DependencyPair
    executePlanFragment(Map<Integer, List<VoltTable>> dependencies, long fragmentId, final ParameterSet params,
                        final SystemProcedureExecutionContext context)
    {
        return null;
    }

    public VoltTable[] run(SystemProcedureExecutionContext ctx,
            String paths[], String nonces[]) throws VoltAbortException
    {
        VoltDB.crashLocalVoltDB("SnapshotDelete sysproc run should never be called", false, null);
        return null;
    }
}
