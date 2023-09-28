package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class GetPosts extends VoltProcedure {
    public final SQLStmt getPosts =
        new SQLStmt("SELECT u_id, post, posted_at " + 
                    "FROM RetwisPosts " + 
                    "WHERE u_id = ? LIMIT 20");

    public VoltTable[] run(int userID) throws VoltAbortException
    {
        voltQueueSQL(getPosts, userID);
        return voltExecuteSQL(true);
    }
}
