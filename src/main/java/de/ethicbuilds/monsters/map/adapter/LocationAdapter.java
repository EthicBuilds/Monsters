package de.ethicbuilds.monsters.map.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.IOException;

public class LocationAdapter extends TypeAdapter<Location> {
    @Override
    public void write(JsonWriter out, Location loc) throws IOException {
        out.beginObject();
        out.name("world").value(loc.getWorld().getName());
        out.name("x").value(loc.getX());
        out.name("y").value(loc.getY());
        out.name("z").value(loc.getZ());
        out.endObject();
    }

    @Override
    public Location read(JsonReader in) throws IOException {
        String worldName = null;
        double x = 0, y = 0, z = 0;

        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "world" -> worldName = in.nextString();
                case "x" -> x = in.nextDouble();
                case "y" -> y = in.nextDouble();
                case "z" -> z = in.nextDouble();
            }
        }
        in.endObject();
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }
}
