package breakdown;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;

public class DoGetUser extends VoltProcedure {
    public final SQLStmt getUser =
        new SQLStmt("SELECT username, email, date_joined, photo " + 
                    "FROM TestUsers " + 
                    "WHERE u_id < 1000");

    public VoltTable run(int u_id) throws VoltAbortException
    {
        voltQueueSQL(getUser);
        VoltTable result = voltExecuteSQL(true)[0];
        // result.advanceRow();
        // System.out.println("Result: "+ result);
        return result;
    }
}
