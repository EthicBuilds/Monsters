package de.ethicbuilds.monsters.map.decoration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/***
 * How Nexo shows a piece of furniture (display transform, translation, scale). Nexo only sends this by packets,
 * the world does not keep it, so the Monsters servers read it from the Nexo items of the build server.
 */
public final class FurnitureLook {
    private record Look(ItemDisplay.ItemDisplayTransform transform, Vector3f translation, Vector3f scale) {}

    private final Map<String, Look> looks = new HashMap<>();

    public FurnitureLook(File itemsFolder, Logger logger) {
        File[] files = itemsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            logger.warning("Nexo items not found in " + itemsFolder + ", furniture keeps its stored look");
            return;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection properties = yaml.getConfigurationSection(id + ".Mechanics.furniture.properties");
                if (properties == null) continue;
                ItemDisplay.ItemDisplayTransform transform = null;
                try {
                    if (properties.isString("display_transform")) {
                        transform = ItemDisplay.ItemDisplayTransform.valueOf(properties.getString("display_transform").toUpperCase());
                    }
                } catch (IllegalArgumentException ignored) {
                }
                looks.put(id, new Look(transform, vector(properties.getString("translation")), vector(properties.getString("scale"))));
            }
        }
        logger.info("Furniture looks from Nexo: " + looks.size());
    }

    /** Only what Nexo sets is changed, everything else stays as stored */
    public void apply(ItemDisplay display, String id) {
        Look look = looks.get(id);
        if (look == null) return;
        if (look.transform() != null) display.setItemDisplayTransform(look.transform());
        if (look.translation() == null && look.scale() == null) return;
        Transformation now = display.getTransformation();
        display.setTransformation(new Transformation(look.translation() != null ? look.translation() : now.getTranslation(),
                now.getLeftRotation(), look.scale() != null ? look.scale() : now.getScale(), now.getRightRotation()));
    }

    private static Vector3f vector(String text) {
        if (text == null) return null;
        String[] parts = text.split(",");
        if (parts.length != 3) return null;
        try {
            return new Vector3f(Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()), Float.parseFloat(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
