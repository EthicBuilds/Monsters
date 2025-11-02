package de.ethicbuilds.monsters.statistic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoundStatsDto {
    private UUID uuid;
    private String map;
    private int round;
    private int killedMonsters;
}
