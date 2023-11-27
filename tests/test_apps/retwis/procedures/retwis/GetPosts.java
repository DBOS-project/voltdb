package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltVMProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class GetPosts extends VoltVMProcedure {
    public final SQLStmt getPosts =
        new SQLStmt("SELECT u_id, post, posted_at " + 
                    "FROM RetwisPosts " + 
                    "WHERE u_id = ? LIMIT 30");

    public VoltTable[] run(int u_id) throws VoltAbortException
    {
        voltQueueSQL(getPosts, u_id);
        return voltExecuteSQL(true);
    }
}
