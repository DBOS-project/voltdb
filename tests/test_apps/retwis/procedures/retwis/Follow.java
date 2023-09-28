package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class Follow extends VoltProcedure {
    public final SQLStmt insertFollow =
        new SQLStmt("INSERT INTO RetwisFollowers (u_id, follower_u_id) VALUES (?, ?);");

    public long run(int userID, int follower_id) throws VoltAbortException
    {
        voltQueueSQL(insertFollow, userID, follower_id);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}
