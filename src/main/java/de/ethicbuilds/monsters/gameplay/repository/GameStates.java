package de.ethicbuilds.monsters.gameplay.repository;

import de.ethicbuilds.monsters.gameplay.model.GamePhase;
import lombok.Getter;
import lombok.Setter;

public class GameStates {
    @Getter @Setter
    private GamePhase currentPhase = GamePhase.PRE_GAME;
}
