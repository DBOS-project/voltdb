package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class Post extends VoltProcedure {
    public final SQLStmt insertPost =
        new SQLStmt("INSERT INTO RetwisPosts (post_id, u_id, post) VALUES (?, ?, ?);");

    public long run(int u_id, int postID, String post) throws VoltAbortException
    {
        voltQueueSQL(insertPost, u_id, postID, post);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}