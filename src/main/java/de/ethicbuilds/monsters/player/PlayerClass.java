package de.ethicbuilds.monsters.player;

import lombok.Getter;

/***
 * Class chosen in the waiting time (user 31.10.), each with one clear strength. The icon is the Nexo model of the
 * item that fits it, so no new symbols are needed.
 */
@Getter
public enum PlayerClass {
    SANITAETER("Sanitäter", "Medic", "erstehilfe_1", "#3CDC6E",
            "Belebt 40 % schneller wieder", "Revives 40% faster", "1 Medkit zum Start", "1 medkit at the start"),
    TECHNIKER("Techniker", "Engineer", "brechstange", "#F9A11E",
            "Bricht 30 % schneller auf", "Breaks open 30% faster", "Türen, Kisten und Wracks", "Doors, crates and wrecks"),
    SCHUETZE("Schütze", "Marksman", "sniper_3", "#FF6B6B",
            "Kopfschüsse +30 % statt +20 %", "Headshots +30% instead of +20%", "Für ruhige Hände", "For steady hands"),
    STURMLAEUFER("Sturmläufer", "Stormrunner", "messer_v2", "#49A2E8",
            "Messer doppelt so stark", "Knife twice as strong", "8 % schneller, 2 Herzen mehr", "8% faster, 2 more hearts");

    private final String nameDe, nameEn, model, color, mainDe, mainEn, extraDe, extraEn;

    PlayerClass(String nameDe, String nameEn, String model, String color, String mainDe, String mainEn, String extraDe, String extraEn) {
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.model = model;
        this.color = color;
        this.mainDe = mainDe;
        this.mainEn = mainEn;
        this.extraDe = extraDe;
        this.extraEn = extraEn;
    }
}
