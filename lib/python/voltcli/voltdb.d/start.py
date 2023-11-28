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

voltdbroot_help = ('Specifies the root directory for the database. The default '
                   'is voltdbroot under the current working directory.')
server_list_help = ('{hostname-or-ip[,...]}, '
             'Specifies the leader(s) for coordinating cluster startup. ')

@VOLT.Command(
    bundles = VOLT.ServerBundle('probe',
                                safemode_available=True,
                                supports_daemon=True,
                                supports_multiple_daemons=True,
                                check_environment_config=True,
                                supports_paused=True),
    options = (
        VOLT.StringListOption('-H', '--host', 'server_list', server_list_help, default = ''),
        VOLT.IntegerOption('-c', '--count', 'hostcount', 'number of hosts in the cluster'),
        VOLT.PathOption('-D', '--dir', 'directory_spec', voltdbroot_help),
        VOLT.BooleanOption('-r', '--replica', 'replica', None),
        VOLT.BooleanOption('-A', '--add', 'enableadd', 'allows the server to elastically expand the cluster if the cluster is already complete', default = False),
        VOLT.IntegerOption('-m', '--missing', 'missing', 'specifies how many nodes are missing at K-safe cluster startup'),
        VOLT.PathOption('-l', '--license', 'license', 'specify a license file to replace the existing staged copy of the license'),
        VOLT.BooleanOption('-P', '--procedureprocess', 'procedureprocess', 'run as procedure process possibly in a vm'),
        VOLT.StringOption('-V', '--vmisolation', 'vmisolation', 'enable vm isolation'),
        VOLT.BooleanOption('-R', '--vmpvaccel', 'vmpvaccel', 'enable vm pv acceleration'),
        VOLT.PathOption('-Q', '--vmshminputfile', 'vmshminputfile', 'shared memory file path for input'),
        VOLT.PathOption('-W', '--vmshmoutputfile', 'vmshmoutputfile', 'shared memory file path for output'),
        VOLT.PathOption('-T', '--vmisolationtcpport', 'vmisolationtcpport', 'TCP port for this host'),
        VOLT.PathOption('-U', '--vmisolationtcphost', 'vmisolationtcphost', 'TCP port for this host'),
        VOLT.PathOption('-E', '--vmid', 'vmid', 'vm id'),
    ),
    log4j_default = 'log4j.xml',
    description = 'Starts a database, which has been initialized.'
)

def start(runner):
    if runner.opts.replica:
        runner.abort_with_help('The --replica option is no longer allowed.')
    if runner.opts.directory_spec:
        runner.args.extend(['voltdbroot', runner.opts.directory_spec])
    if not runner.opts.server_list:
        runner.abort_with_help('You must specify the --host option.')
    runner.args.extend(['mesh', ','.join(runner.opts.server_list)])
    if runner.opts.hostcount:
        runner.args.extend(['hostcount', runner.opts.hostcount])
    if runner.opts.missing:
        runner.args.extend(['missing', runner.opts.missing])
    if runner.opts.enableadd:
        runner.args.extend(['enableadd'])
    if runner.opts.license:
        runner.args.extend(['license', runner.opts.license])
    if runner.opts.procedureprocess:
        runner.args.extend(['procedureprocess'])
        print("procedureprocess enabled")
    if runner.opts.vmisolation:
        runner.args.extend(['vmisolation', runner.opts.vmisolation])
        print("vmisolation enabled to ", runner.opts.vmisolation)
    if runner.opts.vmpvaccel:
        runner.args.extend(['vmpvaccel'])
        print("vmpvaccel enabled")
    if runner.opts.vmshminputfile:
        runner.args.extend(['vmshminputfile', runner.opts.vmshminputfile])
        print("vmshminputfile ", runner.opts.vmshminputfile)
    if runner.opts.vmshmoutputfile:
        runner.args.extend(['vmshmoutputfile', runner.opts.vmshmoutputfile])
        print("vmshmoutputfile ", runner.opts.vmshmoutputfile)
    if runner.opts.vmisolationtcpport:
        runner.args.extend(['vmisolationtcpport', runner.opts.vmisolationtcpport])
        print("vmisolationtcpport ", runner.opts.vmisolationtcpport)
    if runner.opts.vmisolationtcphost:
        runner.args.extend(['vmisolationtcphost', runner.opts.vmisolationtcphost])
        print("vmisolationtcphost ", runner.opts.vmisolationtcphost)
    if runner.opts.vmid:
        runner.args.extend(['vmid', runner.opts.vmid])
        print("vmid ", runner.opts.vmid)
    print("Runner args: ", runner.args)
    runner.go()
