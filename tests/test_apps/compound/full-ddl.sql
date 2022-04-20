LOAD CLASSES compound-procs.jar;

file -inlinebatch END_OF_DROPS

drop table foo if exists;
drop table bar if exists;
drop table mumble if exists;
drop procedure MySpProc if exists;
drop procedure MyOtherSpProc if exists;
drop procedure MyLastProc if exists;
drop procedure NullCompoundProc if exists;
drop procedure SimpleCompoundProc if exists;

END_OF_DROPS

file -inlinebatch END_OF_BATCH

create table foo (intval integer not null, strval varchar(20), primary key(intval));
partition table foo on column intval;
create table bar (intval integer not null, strval varchar(20), primary key(intval));
partition table bar on column intval;
create table mumble (intval bigint not null, strval1 varchar(20), strval2 varchar(20), primary key(intval));
partition table mumble on column intval;
create procedure MySpProc partition on table foo column intval as select strval from foo where intval = ?;
create procedure MyOtherSpProc partition on table bar column intval as select strval from bar where intval = ?;
create procedure MyLastProc partition on table mumble column intval as insert into mumble values (?, ?, ?);
create compound procedure from class compound.NullCompoundProc;
create compound procedure from class compound.SimpleCompoundProc;

END_OF_BATCH

upsert into foo values (1, 'one thing');
upsert into bar values (2, 'another thing');
