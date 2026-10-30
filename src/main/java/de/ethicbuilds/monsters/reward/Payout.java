package de.ethicbuilds.monsters.reward;

/***
 * One reward part in EthicCoins: before the rank bonus and what was paid with it
 */
public record Payout(long base, long paid) {
    public static final Payout NONE = new Payout(0, 0);

    public long bonus() {
        return paid - base;
    }
}
