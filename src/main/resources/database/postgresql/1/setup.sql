CREATE TABLE IF NOT EXISTS debugs
(
    id        SERIAL,
    hash      TEXT                    NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW() NOT NULL,
    CONSTRAINT debugs_pk
        PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS debugs_hash_uindex
    ON debug (read_hash);

CREATE TABLE IF NOT EXISTS debug_data
(
    id              INT  NOT NULL PRIMARY KEY,
    plugin_meta     json NOT NULL,
    server_meta     json NOT NULL,
    additional_data json,
    log_meta        json
);



CREATE TABLE IF NOT EXISTS debug_configs
(
    id       SERIAL
        PRIMARY KEY,
    debug_id INT  NOT NULL,
    path     TEXT NOT NULL,
    config   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS debug_configs_id_index
    ON debug_configs (debug_id);
