/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.voltcore.messaging.VoltMessage;
import org.voltcore.utils.CoreUtils;

/**
 * Message from a new leader requesting for repair log entries
 * from all surviving replicas, which doing node promotion.
 */
public class Iv2RepairLogRequestMessage extends VoltMessage
{
    public final static int MPIREQUEST = 1;
    public final static int SPREQUEST = 2;
    private long m_requestId = 0;
    private int m_requestType = SPREQUEST;
    private int m_deadHostId;
    private int m_repairRetryCount = 0;  // ugly way to know how long we wait for all m_deadHostId's ForeignHosts to go away

    /** Empty constructor for de-serialization */
    Iv2RepairLogRequestMessage() {
        super();
    }

    public Iv2RepairLogRequestMessage(long requestId, int deadHostId, int requestType)
    {
        super();
        m_requestId = requestId;
        m_deadHostId = deadHostId;
        m_requestType = requestType;
    }

    public boolean isMPIRequest()
    {
        return m_requestType == MPIREQUEST;
    }

    public int getDeadHostId() {
        m_repairRetryCount++;
        return m_deadHostId;
    }

    public int getRepairRetryCount() {
        return m_repairRetryCount;
    }

    public void disableDeadHostCheck() {
        m_deadHostId = Integer.MAX_VALUE;
    }

    public long getRequestId()
    {
        return m_requestId;
    }

    @Override
    public int getSerializedSize()
    {
        int msgsize = super.getSerializedSize();
        msgsize += 8; // requestId
        msgsize += 4; // requestType
        msgsize += 4; // deadHost
        return msgsize;
    }

    @Override
    public void flattenToBuffer(ByteBuffer buf) throws IOException
    {
        buf.put(VoltDbMessageFactory.IV2_REPAIR_LOG_REQUEST);
        buf.putLong(m_requestId);
        buf.putInt(m_requestType);
        buf.putInt(m_deadHostId);

        assert(buf.capacity() == buf.position());
        buf.limit(buf.position());
    }

    @Override
    public void initFromBuffer(ByteBuffer buf) throws IOException {
        m_requestId = buf.getLong();
        m_requestType = buf.getInt();
        m_deadHostId = buf.getInt();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("IV2 REPAIR_LOG_REQUEST (FROM ");
        sb.append(CoreUtils.hsIdToString(m_sourceHSId));
        sb.append(" REQID: ");
        sb.append(m_requestId);
        sb.append(" REQTYPE: ");
        sb.append(m_requestType);
        sb.append(" DEADHOSTID: ");
        sb.append(m_deadHostId);
        return sb.toString();
    }
}
