package de.eldoria.updatebutler.api.debug.data;

import lombok.Data;

@Data
public class LogData {
    private String log;
    private String pluginLog;
    private String[] internalExceptions;
    private String[] externalExceptions;

    public LogData(String log,String pluginLog, String[] internalExceptions, String[] externalExceptions) {
        this.log = log;
        this.pluginLog = pluginLog;
        this.internalExceptions = internalExceptions;
        this.externalExceptions = externalExceptions;
    }
}
