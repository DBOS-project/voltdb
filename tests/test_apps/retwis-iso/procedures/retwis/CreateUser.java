package retwis;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;
import org.voltdb.VoltVMProcedure;

public class CreateUser extends VoltProcedure {
    public final SQLStmt insertUser =
        new SQLStmt("INSERT INTO RetwisUsers (u_id, username) VALUES (?, ?);");

    public long run(int u_id, String name) throws VoltAbortException
    {
        voltQueueSQL(insertUser, u_id, name);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}
