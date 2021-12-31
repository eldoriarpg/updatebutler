CREATE TABLE IF NOT EXISTS debug
(
    id            INTEGER   DEFAULT NEXTVAL('updatebutler.debugs_id_seq'::regclass) NOT NULL
        CONSTRAINT debugs_pk
            PRIMARY KEY,
    read_hash     TEXT                                                              NOT NULL,
    timestamp     TIMESTAMP DEFAULT NOW()                                           NOT NULL,
    deletion_hash TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS debugs_hash_uindex
    ON debug (read_hash);

CREATE UNIQUE INDEX IF NOT EXISTS debugs_deletion_hash_uindex
    ON debug (deletion_hash);

CREATE TABLE IF NOT EXISTS debug_data
(
    debug_id    INTEGER NOT NULL
        PRIMARY KEY
        CONSTRAINT debug_data_debugs_id_fk
            REFERENCES debug
            ON DELETE CASCADE,
    plugin      json    NOT NULL,
    server_meta json    NOT NULL,
    additional  json,
    log_meta    json
);

CREATE TABLE IF NOT EXISTS debug_configs
(
    id          SERIAL
        PRIMARY KEY,
    debug_id    INTEGER NOT NULL
        CONSTRAINT debug_configs_debugs_id_fk
            REFERENCES debug
            ON DELETE CASCADE,
    config_path TEXT    NOT NULL,
    content     TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS debug_configs_id_index
    ON debug_configs (debug_id);

CREATE TABLE IF NOT EXISTS tag
(
    guild_id BIGINT NOT NULL,
    tag      TEXT   NOT NULL,
    content  json   NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS table_name_guild_id_tag_uindex
    ON tag (guild_id, LOWER(tag));

CREATE TABLE IF NOT EXISTS application
(
    id          SERIAL
        CONSTRAINT application_pk
            PRIMARY KEY,
    guild_id    BIGINT NOT NULL,
    identifier  TEXT   NOT NULL,
    name        TEXT   NOT NULL,
    channel     BIGINT,
    webhook     TEXT   NOT NULL,
    notify_role BIGINT,
    buyer_role  BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS application_guild_id_name_uindex
    ON application (guild_id, LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS application_id_uindex
    ON application (id);

CREATE TABLE IF NOT EXISTS application_buyer
(
    app_id    INTEGER
        CONSTRAINT application_buyer_application_id_fk
            REFERENCES application
            ON DELETE CASCADE,
    user_id   BIGINT,
    spigot_id INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS application_buyer_app_id_spigot_id_uindex
    ON application_buyer (app_id, spigot_id);

CREATE TABLE IF NOT EXISTS application_owner
(
    app_id INTEGER NOT NULL
        CONSTRAINT application_owner_application_id_fk
            REFERENCES application
            ON DELETE CASCADE,
    id     BIGINT  NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS application_owner_app_id_id_uindex
    ON application_owner (app_id, id);

CREATE INDEX IF NOT EXISTS application_owner_app_id_index
    ON application_owner (app_id);

CREATE TABLE IF NOT EXISTS application_release
(
    app_id     INTEGER
        CONSTRAINT application_release_application_id_fk
            REFERENCES application
            ON DELETE CASCADE,
    version    TEXT                    NOT NULL,
    title      TEXT                    NOT NULL,
    patchnotes TEXT                    NOT NULL,
    dev_build  BOOLEAN   DEFAULT FALSE NOT NULL,
    published  TIMESTAMP DEFAULT NOW() NOT NULL,
    downloads  INTEGER   DEFAULT 0     NOT NULL,
    file       TEXT,
    checksum   INTEGER
);

CREATE UNIQUE INDEX IF NOT EXISTS application_release_version_app_id_uindex
    ON application_release (version, app_id);

CREATE TABLE IF NOT EXISTS access_role
(
    guild_id BIGINT NOT NULL,
    role_id  BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS access_role_guild_id_uindex
    ON access_role (guild_id);

CREATE TABLE IF NOT EXISTS time_channel
(
    guild_id   BIGINT NOT NULL
        CONSTRAINT time_channel_pk
            PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    timezone   TEXT   NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS time_channel_guild_id_uindex
    ON time_channel (guild_id);

CREATE TABLE IF NOT EXISTS application_alias
(
    app_id INTEGER NOT NULL
        CONSTRAINT application_alias_application_id_fk
            REFERENCES application
            ON DELETE CASCADE,
    alias  TEXT    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS application_alias_app_id_alias_uindex
    ON application_alias (app_id, LOWER(alias));

CREATE OR REPLACE VIEW application_view AS
(
SELECT id,
       guild_id,
       identifier,
       name,
       channel,
       webhook,
       notify_role,
       buyer_role,
       alias,
       owner
FROM application
         LEFT JOIN (SELECT app_id, ARRAY_AGG(alias) AS alias FROM application_alias GROUP BY app_id) aa
                   ON application.id = aa.app_id
         LEFT JOIN (SELECT app_id, ARRAY_AGG(id) AS owner FROM application_owner GROUP BY app_id) ao
                   ON application.id = ao.app_id
    );
