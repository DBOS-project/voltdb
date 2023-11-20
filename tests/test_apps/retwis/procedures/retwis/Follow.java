package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;
import org.voltdb.VoltVMProcedure;

public class Follow extends VoltVMProcedure {
    public final SQLStmt insertFollow =
        new SQLStmt("INSERT INTO RetwisFollowers (u_id, follower_u_id) VALUES (?, ?);");

    public long run(int u_id, int follower_id) throws VoltAbortException
    {
        voltQueueSQL(insertFollow, u_id, follower_id);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}
