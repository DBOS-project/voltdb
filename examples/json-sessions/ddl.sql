CREATE TABLE user_session_table (
    username           int        UNIQUE NOT NULL
);
PARTITION TABLE user_session_table ON COLUMN username;

CREATE UNIQUE INDEX username_idx ON user_session_table (username);

-- Update classes from jar to that server will know about classes but not procedures yet.
LOAD CLASSES json-procs.jar;

CREATE PROCEDURE PARTITION ON TABLE user_session_table COLUMN username FROM CLASS jsonsessions.Login1;
CREATE PROCEDURE PARTITION ON TABLE user_session_table COLUMN username FROM CLASS jsonsessions.Login5;
CREATE PROCEDURE PARTITION ON TABLE user_session_table COLUMN username FROM CLASS jsonsessions.Login10;
