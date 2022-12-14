# This file is part of VoltDB.
# Copyright (C) 2008-2022 Volt Active Data Inc.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.

@VOLT.Command(
    bundles = VOLT.AdminBundle(),
    description = 'Resume a paused VoltDB cluster that is in admin mode.'
)
def resume(runner):
    # Check the STATUS column. runner.call_proc() detects and aborts on errors.
    status = runner.call_proc('@Resume', [], []).table(0).tuple(0).column_integer(0)
    if status == 0:
        runner.info('The cluster has resumed.')
    else:
        runner.abort('The cluster has failed to resume with status: %d' % status)
