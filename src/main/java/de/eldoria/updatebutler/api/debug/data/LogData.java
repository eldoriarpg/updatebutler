package de.eldoria.updatebutler.api.debug.data;

import lombok.Data;

@Data
public class LogData {
    private String log;
    private String[] internalExceptions;
    private String[] exceptions;

    public LogData(String log, String[] internalExceptions, String[] exceptions) {
        this.log = log;
        this.internalExceptions = internalExceptions;
        this.exceptions = exceptions;
    }
}
