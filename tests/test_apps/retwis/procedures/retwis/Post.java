package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class Post extends VoltProcedure {
    public final SQLStmt insertPost =
        new SQLStmt("INSERT INTO RetwisPosts (post_id, u_id, post) VALUES (?, ?, ?);");

    public long run(int userID, int postID, String post) throws VoltAbortException
    {
        voltQueueSQL(insertPost, userID, postID, post);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}