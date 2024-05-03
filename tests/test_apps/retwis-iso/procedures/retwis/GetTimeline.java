package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;
import org.voltdb.VoltVMProcedure;

public class GetTimeline extends VoltVMProcedure {
    public final SQLStmt getTimeline =
        new SQLStmt("SELECT RetwisPosts.u_id, RetwisPosts.post, RetwisPosts.posted_at " + 
                    "FROM RetwisFollowers JOIN RetwisPosts ON RetwisFollowers.u_id = RetwisPosts.u_id " + 
                    "WHERE RetwisFollowers.follower_u_id = ? LIMIT 20");

    public VoltTable[] run(int u_id) throws VoltAbortException
    {
        voltQueueSQL(getTimeline, u_id);
        return voltExecuteSQL(true);
    }
}
