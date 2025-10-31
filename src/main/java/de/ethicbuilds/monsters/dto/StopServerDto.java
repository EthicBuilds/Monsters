package de.ethicbuilds.monsters.dto;

public class StopServerDto {
    private String serverName;
    /***
     * Empty Constructor for Gson
     */
    public StopServerDto() {
    }

    public StopServerDto(String serverName) {
        this.serverName = serverName;
    }
}
