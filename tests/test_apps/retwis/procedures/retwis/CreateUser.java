package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class CreateUser extends VoltProcedure {
    public final SQLStmt insertUser =
        new SQLStmt("INSERT INTO RetwisUsers (u_id, username) VALUES (?, ?);");

    public long run(int userID, String name) throws VoltAbortException
    {
        voltQueueSQL(insertUser, userID, name);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}
