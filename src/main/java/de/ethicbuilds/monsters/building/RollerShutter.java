package de.ethicbuilds.monsters.building;

import de.ethicbuilds.monsters.Main;
import de.ethicbuilds.monsters.bautest.BauTest;
import de.ethicbuilds.monsters.map.decoration.DecorationManager;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/***
 * The look of a roller door (user 31.10., models and sizes by DESIGN): frame and box stay, 14 slats roll up into the box
 * one after the other and vanish there, the bottom rail stays below the box. All parts move together at one speed,
 * each with its own way (client interpolation, no packet per tick); closing runs backwards.
 */
final class RollerShutter {
    private static final String DOOR = "monsters_rolltor_";
    private static final int SLATS = 14;
    // Blocks from the furniture floor: slat height, a slat's offset when hidden in the box, rail way up
    private static final double SLAT = 0.1875, HIDDEN = 2.65625, RAIL = 2.625;
    private static final int OPEN_TICKS = 30;
    private static final double SPEED = HIDDEN / OPEN_TICKS;
    private static final String VARIANTS = "abc";

    private final DecorationManager decorationManager;
    private final Transformation base;
    private final ItemDisplay frame, rail;
    private final ItemDisplay[] slats = new ItemDisplay[SLATS];
    private ItemDisplay brace;
    private int level;
    private int run;

    RollerShutter(DecorationManager decorationManager, Location at, int level) {
        this.decorationManager = decorationManager;
        this.level = level;
        frame = spawn(at, DOOR + level + "_zu");
        // Nexo's scale and turn of the door, every part only moves up from there
        base = frame.getTransformation();
        rail = spawn(at, null);
        for (int i = 0; i < SLATS; i++) slats[i] = spawn(at, null);
        if (level == 3) brace = spawn(at, null);
        dress();
        place(false);
    }

    private ItemDisplay spawn(Location at, String look) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, spawn -> {
            spawn.setPersistent(false);
            spawn.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });
        if (look != null) decorationManager.look(display, look);
        else if (frame != null) display.setTransformation(frame.getTransformation());
        BauTest.limit(display);
        return display;
    }

    // Models of the current level
    private void dress() {
        frame.setItemStack(decorationManager.modelItem(DOOR + level + "_rahmen"));
        rail.setItemStack(decorationManager.modelItem(DOOR + "endleiste"));
        if (brace != null) brace.setItemStack(decorationManager.modelItem(DOOR + "3_verstaerkung"));
    }

    private String slat(int i) {
        if (level == 2 && (i == 4 || i == 10)) return DOOR + "2_lamelle_riegel";
        return DOOR + level + "_lamelle_" + VARIANTS.charAt((i - 1) % 3);
    }

    /** Another level: the frame and the slats of it, the brace only on steel */
    void level(int level, boolean open) {
        this.level = level;
        if (level == 3 && brace == null) brace = spawn(frame.getLocation(), null);
        if (level != 3 && brace != null) {
            brace.remove();
            brace = null;
        }
        dress();
        place(open);
    }

    /** Open or closed right away, without moving */
    void place(boolean open) {
        run++;
        frame.setItemStack(decorationManager.modelItem(DOOR + level + "_rahmen"));
        move(rail, open ? RAIL : 0, 0);
        for (int i = 1; i <= SLATS; i++) {
            ItemDisplay slat = slats[i - 1];
            slat.setItemStack(open ? null : decorationManager.modelItem(slat(i)));
            move(slat, open ? HIDDEN : SLAT * (i - 1), 0);
        }
        if (brace != null) brace.setItemStack(open ? null : decorationManager.modelItem(DOOR + "3_verstaerkung"));
    }

    /** Broken: nothing of it to see but the frame */
    void broken() {
        run++;
        rail.setItemStack(null);
        for (ItemDisplay slat : slats) slat.setItemStack(null);
        if (brace != null) brace.setItemStack(null);
    }

    void roll(boolean open) {
        int roll = ++run;
        Main plugin = Main.getINSTANCE();
        if (open) {
            // Everything starts together, each slat vanishes once it is inside the box
            if (brace != null) brace.setItemStack(null);
            move(rail, RAIL, ticks(RAIL));
            for (int i = 1; i <= SLATS; i++) {
                ItemDisplay slat = slats[i - 1];
                int ticks = ticks(HIDDEN - SLAT * (i - 1));
                move(slat, HIDDEN, ticks);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (run == roll && slat.isValid()) slat.setItemStack(null);
                }, ticks + 1L);
            }
            return;
        }
        // Backwards: the lowest slat comes out first, each one later by the way it has less
        for (int i = 1; i <= SLATS; i++) {
            ItemDisplay slat = slats[i - 1];
            String model = slat(i);
            double way = HIDDEN - SLAT * (i - 1);
            double target = SLAT * (i - 1);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (run != roll || !slat.isValid()) return;
                slat.setItemStack(decorationManager.modelItem(model));
                move(slat, target, ticks(way));
            }, Math.max(1, ticks(HIDDEN - way)));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (run == roll && rail.isValid()) move(rail, 0, ticks(RAIL));
        }, Math.max(1, ticks(HIDDEN - RAIL)));
        if (brace != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (run == roll && brace != null && brace.isValid()) brace.setItemStack(decorationManager.modelItem(DOOR + "3_verstaerkung"));
            }, ticks(HIDDEN) + 2L);
        }
    }

    private static int ticks(double way) {
        return (int) Math.round(way / SPEED);
    }

    private void move(ItemDisplay display, double up, int ticks) {
        if (!display.isValid()) return;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(new Vector3f(base.getTranslation()).add(0, (float) up, 0),
                base.getLeftRotation(), base.getScale(), base.getRightRotation()));
    }

    List<ItemDisplay> parts() {
        List<ItemDisplay> parts = new ArrayList<>(List.of(frame, rail));
        parts.addAll(List.of(slats));
        if (brace != null) parts.add(brace);
        return parts;
    }

    void remove() {
        parts().forEach(ItemDisplay::remove);
    }
}
