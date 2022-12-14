/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
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

package windowing;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.types.TimestampType;

/**
 * <p>Find tuples with timestamps older than or equivalent to newestToDiscard.
 * Delete up to maxRowsToDeletePerProc of them, in oldest to newest order.</p>
 *
 * <p>One important thing to consider when developing procedures like this is
 * that they be deterministic. This procedure may be applied simultaniously on
 * many replica partitions, but since it will deterministically delete the same
 * tuples if the database contents are identical, then it will be fine. Note:
 * this is why VoltDB doesn't allow LIMIT in delete operators. All DML must
 * be deterministic.</p>
 *
 * <p>Note, there is a lot of redundant code/comments among the stored procedures
 * in this example app. That's intentional to make each stand alone and be easier
 * to follow. A production app might offer less choice or just reuse more code.</p>
 */
public class DeleteAfterDate extends VoltProcedure {

    final SQLStmt countMatchingRows = new SQLStmt(
            "SELECT COUNT(*) FROM timedata WHERE update_ts <= ?;");

    final SQLStmt getNthOldestTimestamp = new SQLStmt(
            "SELECT update_ts FROM timedata ORDER BY update_ts ASC OFFSET ? LIMIT 1;");

    final SQLStmt deleteOlderThanDate = new SQLStmt(
            "DELETE FROM timedata WHERE update_ts <= ?;");

    /**
     * Procedure main logic.
     *
     * @param partitionValue Partitioning key for this procedure.
     * @param newestToDiscard Try to remove any tuples as old or older than this value.
     * @param targetMaxRowsToDelete The target upper limit on the number of rows to delete per transaction.
     * @return The number of rows deleted.
     * @throws VoltAbortException on bad input.
     */
    public long run(String partitionValue, TimestampType newestToDiscard, long targetMaxRowsToDelete) {
        if (newestToDiscard == null) {
            throw new VoltAbortException("newestToDiscard shouldn't be null.");
            // It might be Long.MIN_VALUE as a TimestampType though.
        }
        if (targetMaxRowsToDelete <= 0) {
            throw new VoltAbortException("maxRowsToDeletePerProc must be > 0.");
        }

        // Get the total number of rows older than the given timestamp.
        voltQueueSQL(countMatchingRows, EXPECT_SCALAR_LONG, newestToDiscard);
        long agedOutCount = voltExecuteSQL()[0].asScalarLong();

        if (agedOutCount > targetMaxRowsToDelete) {
            // Find the timestamp of the row at position N in the sorted order, where N is the chunk size
            voltQueueSQL(getNthOldestTimestamp, EXPECT_SCALAR, targetMaxRowsToDelete);
            newestToDiscard = voltExecuteSQL()[0].fetchRow(0).getTimestampAsTimestamp(0);
        }

        // Delete all rows >= the timestamp found in the previous statement.
        // This will delete AT LEAST N rows, but since timestamps may be non-unique,
        //  it might delete more than N. In the worst case, it could delete all rows
        //  if every row has an identical timestamp value. It is guaranteed to make
        //  progress. If we used strictly less than, it might not make progress.
        // This is why the max rows to delete number is a target, not always a perfect max.
        voltQueueSQL(deleteOlderThanDate, EXPECT_SCALAR_LONG, newestToDiscard);
        long deletedCount = voltExecuteSQL(true)[0].asScalarLong();

        // Return the number of deleted rows.
        return deletedCount;
    }
}
