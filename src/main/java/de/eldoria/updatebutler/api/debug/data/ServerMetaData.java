package de.eldoria.updatebutler.api.debug.data;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public class ServerMetaData {
    protected String version;
    protected int currentPlayers;
    protected int loadedWorlds;
    protected PluginMetaData[] plugins;

    protected ServerMetaData(String version, int currentPlayers, int loadedWorlds, PluginMetaData[] plugins) {
        this.version = version;
        this.currentPlayers = currentPlayers;
        this.loadedWorlds = loadedWorlds;
        this.plugins = plugins;
    }

    @Override
    public String toString() {
        String plugins = Arrays.stream(this.plugins).map(m -> m.toString(2)).collect(Collectors.joining("\n"));
        return String.format("version: %s%ncurrentPlayers: %d%nloadedWorlds%d%npluginCount: %d%nplugins:%n%s",
                version, currentPlayers, loadedWorlds, this.plugins.length, plugins);
    }
}
