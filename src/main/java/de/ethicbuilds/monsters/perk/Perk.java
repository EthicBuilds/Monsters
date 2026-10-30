package de.ethicbuilds.monsters.perk;

import de.ethicbuilds.monsters.lang.Lang;
import lombok.Getter;
import org.bukkit.entity.Player;

/***
 * Perks from the machines in the map (Nexo furniture with the same id), once per round, gone when going down
 */
@Getter
public enum Perk {
    VITALITY("monsters_perk_leben", "Leben", "Vitality", 2500, "#FF5C5C", "30 statt 20 Leben", "30 instead of 20 health"),
    QUICK_RELOAD("monsters_perk_nachladen", "Nachladen", "Quick Reload", 3000, "#F9A11E", "40 % schneller nachladen", "Reload 40% faster"),
    QUICK_REVIVE("monsters_perk_wiederbeleben", "Wiederbeleben", "Quick Revive", 2000, "#3CDC6E", "Mitspieler doppelt so schnell aufheben", "Revive teammates twice as fast"),
    STAMINA("monsters_perk_tempo", "Tempo", "Stamina", 2000, "#49A2E8", "12 % schneller laufen", "Walk 12% faster");

    private final String furniture;
    private final String nameDe, nameEn;
    private final int price;
    private final String color;
    private final String effectDe, effectEn;

    Perk(String furniture, String nameDe, String nameEn, int price, String color, String effectDe, String effectEn) {
        this.furniture = furniture;
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.price = price;
        this.color = color;
        this.effectDe = effectDe;
        this.effectEn = effectEn;
    }

    public String displayName(Player player) {
        return Lang.t(player, nameDe, nameEn);
    }

    /** Perk of a furniture id or station, null for anything else */
    public static Perk byFurniture(String id) {
        for (Perk perk : values()) {
            if (perk.furniture.equals(id)) return perk;
        }
        return null;
    }
}
