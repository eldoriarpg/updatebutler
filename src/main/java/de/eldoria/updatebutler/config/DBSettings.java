package de.eldoria.updatebutler.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DBSettings implements Cloneable {
    private String address = "";
    private String port = "";
    private String database = "";
    private String schema = "";
    private String user = "";
    private String password = "";
    private int minConnections = 1;
    private int maxConnections = 25;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public DBSettings(@JsonProperty("adress") String address, @JsonProperty("port") String port, @JsonProperty("database") String database,
                      @JsonProperty("schema") String schema, @JsonProperty("user") String user, @JsonProperty("password") String password,
                      @JsonProperty("minConnectsions") int minConnections, @JsonProperty("maxConnections") int maxConnections) {
        this.address = address;
        this.port = port;
        this.database = database;
        this.schema = schema;
        this.user = user;
        this.password = password;
        this.minConnections = minConnections;
        this.maxConnections = maxConnections;
    }

    public DBSettings() {
    }

    @Override
    public DBSettings clone() {
        return new DBSettings(address, port, database, schema, user, password, minConnections, maxConnections);
    }


    public String address() {
        return this.address;
    }

    public String port() {
        return this.port;
    }

    public String database() {
        return this.database;
    }

    public String schema() {
        return this.schema;
    }

    public String user() {
        return this.user;
    }

    public String password() {
        return this.password;
    }

    public int minConnections() {
        return this.minConnections;
    }

    public int maxConnections() {
        return this.maxConnections;
    }

    public String toString() {
        return "DBSettings(address=" + this.address() + ", port=" + this.port() + ", database=" + this.database() + ", schema=" + this.schema() + ", user=" + this.user() + ", password=" + this.password() + ", minConnections=" + this.minConnections() + ", maxConnections=" + this.maxConnections() + ")";
    }
}
