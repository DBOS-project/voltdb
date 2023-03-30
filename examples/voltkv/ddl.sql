CREATE TABLE store
(
  key      varchar(250) not null
, value    varbinary(1048576) not null
, PRIMARY KEY (key)
);

PARTITION TABLE store ON COLUMN key;

CREATE TABLE storeR
(
    key      varchar(250) not null
    , value    varbinary(1048576) not null
    , PRIMARY KEY (key)
);


LOAD CLASSES procedures.jar;
CREATE PROCEDURE selectR AS SELECT key, value from storeR where key=?;
DROP PROCEDURE selectR;


-- create a procedure from a java class
CREATE PROCEDURE PARTITION ON TABLE store COLUMN key FROM CLASS voltkv.VoltKVQuery;
