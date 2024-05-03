DROP PROCEDURE CreateUser       IF EXISTS;
DROP PROCEDURE Follow           IF EXISTS;
DROP PROCEDURE GetFollowers     IF EXISTS;
DROP PROCEDURE GetPosts         IF EXISTS;
DROP PROCEDURE GetTimeline      IF EXISTS;
DROP PROCEDURE Post             IF EXISTS;
DROP TABLE RetwisUsers          IF EXISTS;
DROP TABLE RetwisPosts          IF EXISTS;
DROP TABLE RetwisFollowers      IF EXISTS;

CREATE TABLE RetwisUsers (
    u_id INTEGER NOT NULL,
    username VARCHAR(32) NOT NULL,
    CONSTRAINT PK_RetwisUsers PRIMARY KEY (u_id)
);
PARTITION TABLE RetwisUsers ON COLUMN u_id;

CREATE TABLE RetwisPosts (
    u_id INTEGER NOT NULL,
    post_id INTEGER NOT NULL,
    post VARCHAR(128) NOT NULL,
    posted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_RetwisPosts PRIMARY KEY (u_id, post_id)
);
PARTITION TABLE RetwisPosts ON COLUMN u_id;
CREATE INDEX RetwisPostsIndex ON RetwisPosts (u_id);

CREATE TABLE RetwisFollowers (
    u_id INTEGER NOT NULL,
    follower_u_id INTEGER NOT NULL,
    CONSTRAINT PK_RetwisFollowers PRIMARY KEY (u_id, follower_u_id)
);
PARTITION TABLE RetwisFollowers ON COLUMN u_id;
CREATE INDEX RetwisFollowersIndex ON RetwisFollowers (u_id);

LOAD CLASSES retwis-procs.jar;

CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.Follow;
CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.CreateUser;
CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.GetFollowers;
CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.GetPosts;
CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.GetTimeline;
CREATE PROCEDURE PARTITION ON TABLE RetwisUsers COLUMN u_id FROM CLASS retwis.Post;