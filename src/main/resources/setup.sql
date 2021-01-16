create table if not exists debugs
(
    id        int auto_increment,
    hash      text                                  not null,
    timestamp timestamp default current_timestamp() not null,
    constraint debugs_pk
        primary key (id)
);

create unique index if not exists debugs_hash_uindex
    on debugs (hash);

create table if not exists debug_data
(
    id              int                          not null
        primary key,
    plugin_meta     longtext collate utf8mb4_bin not null,
    server_meta     longtext collate utf8mb4_bin not null,
    additional_data longtext collate utf8mb4_bin null,
    log_meta        longtext collate utf8mb4_bin null,
    constraint additional_data
        check (json_valid(`additional_data`)),
    constraint log_meta
        check (json_valid(`log_meta`)),
    constraint plugin_meta
        check (json_valid(`plugin_meta`)),
    constraint server_meta
        check (json_valid(`server_meta`))
);



create table if not exists debug_configs
(
    id       int auto_increment
        primary key,
    debug_id int      not null,
    path     text     not null,
    config   longtext not null
);

create index if not exists debug_configs_id_index
    on debug_configs (debug_id);

