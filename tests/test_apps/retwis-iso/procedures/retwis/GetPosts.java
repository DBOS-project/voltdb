package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltVMProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class GetPosts extends VoltVMProcedure {
    public final SQLStmt getPosts =
        new SQLStmt("SELECT post " + 
                    "FROM RetwisPosts " + 
                    "WHERE u_id = ? ORDER BY posted_at LIMIT 10");

    public VoltTable[] run(int u_id) throws VoltAbortException
    {
        voltQueueSQL(getPosts, u_id);
        return voltExecuteSQL(true);
    }
}
