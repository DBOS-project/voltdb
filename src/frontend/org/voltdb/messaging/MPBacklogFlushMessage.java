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

public class MPBacklogFlushMessage extends VoltMessage {

    public MPBacklogFlushMessage()
    {
        super();
    }

    @Override
    public int getSerializedSize()
    {
        int msgsize = super.getSerializedSize();
        return msgsize;
    }

    @Override
    public void flattenToBuffer(ByteBuffer buf) throws IOException
    {
        buf.put(VoltDbMessageFactory.FLUSH_RO_TXN_MESSAGE_ID);

        assert(buf.capacity() == buf.position());
        buf.limit(buf.position());
    }

    @Override
    public void initFromBuffer(ByteBuffer buf) throws IOException {
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("MPI_FAILOVER (FROM ")
          .append(CoreUtils.hsIdToString(m_sourceHSId))
          .append(")");
        return sb.toString();
    }

}
