package de.eldoria.updatebutler.config;

import com.google.gson.annotations.Expose;
import lombok.Data;
import lombok.Getter;

@Data
public class DBSettings implements Cloneable {
    @Expose
    private String address = "";
    @Expose
    private String port = "";
    @Expose
    private String database = "";
    @Expose
    private String user = "";
    @Expose
    private String password = "";
    @Expose
    private int minConnections = 1;
    @Expose
    private int maxConnections = 25;

    public DBSettings(String address, String port, String database, String user, String password, int minConnections, int maxConnections) {
        this.address = address;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
        this.minConnections = minConnections;
        this.maxConnections = maxConnections;
    }

    public DBSettings() {
    }

    @Override
    public DBSettings clone() {
        return new DBSettings(address, port, database, user, password, minConnections, maxConnections);
    }
}
