/* This file is part of VoltDB.
 * Copyright (C) 2022 Volt Active Data Inc.
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

package org.voltdb.sysprocs.saverestore;

/**
 * Enum to describe any schema filtering which is to be performed during a snapshot.
 * <p>
 * Must be in sync with EE enum of the same name
 */
public enum HiddenColumnFilter {
    /** Do not include any hidden columns */
    ALL(0),
    /** Perform no filtering */
    NONE(1),
    /** Exclude the hidden migrate column from the snapshot */
    EXCLUDE_MIGRATE(2);

    private final byte m_id;

    private HiddenColumnFilter(int id) {
        m_id = (byte) id;
    }

    public byte getId() {
        return m_id;
    }
}
