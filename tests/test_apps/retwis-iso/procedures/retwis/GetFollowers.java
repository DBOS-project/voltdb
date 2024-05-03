package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;
import org.voltdb.VoltVMProcedure;

public class GetFollowers extends VoltVMProcedure {
    public final SQLStmt getFollowers =
        new SQLStmt("SELECT follower_u_id " + 
                    "FROM RetwisFollowers " + 
                    "WHERE u_id = ? LIMIT 20");

    public VoltTable[] run(int u_id) throws VoltAbortException
    {
        voltQueueSQL(getFollowers, u_id);
        return voltExecuteSQL(true);
    }
}
