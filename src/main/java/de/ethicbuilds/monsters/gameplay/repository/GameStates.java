package de.ethicbuilds.monsters.gameplay.repository;

import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import lombok.Getter;
import lombok.Setter;

public class GameStates {
    @Getter @Setter
    // Read by the wave thread as well
    private volatile GamePhase currentPhase = GamePhase.PRE_GAME;
}
