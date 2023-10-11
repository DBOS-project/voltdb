package breakdown;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;

public class DoInsertUser extends VoltProcedure {
    public final SQLStmt insertUser =
        new SQLStmt("INSERT INTO TestUsers (u_id, username, email, photo) VALUES (?, ?, ?, ?);");

    public long run(int u_id, String name, String email, String photo) throws VoltAbortException
    {
        voltQueueSQL(insertUser, u_id, name, email, photo);
        VoltTable[] result = voltExecuteSQL(true);
        return result[0].fetchRow(0).getLong(0);
    }
}
