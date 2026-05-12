package com.ronmclauncher.classpath;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ronmclauncher.os.OSdetection;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClassPathBuilder {

    public static String buildClasspath(JsonObject versionData, Path librariesDir, Path jarPath) {
        String sep = OSdetection.currentOs().equals("windows") ? ";" : ":";
        List<String> entries = new ArrayList<>();

        JsonArray libraries = versionData.getAsJsonArray("libraries");
        for (JsonElement libEl : libraries) {
            JsonObject lib = libEl.getAsJsonObject();

            if (lib.has("rules") && !OSdetection.matchesRules(lib.getAsJsonArray("rules"))) continue;

            if (lib.has("downloads") && lib.getAsJsonObject("downloads").has("artifact")) {
                String path = lib.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
                entries.add(librariesDir.resolve(path).toString());
            } else if (lib.has("name")) {
                String[] parts = lib.get("name").getAsString().split(":");
                if (parts.length >= 3) {
                    String pkg = parts[0].replace('.', '/');
                    String art = parts[1];
                    String ver = parts[2];
                    String ext = "jar";
                    if (ver.contains("@")) {
                        String[] vparts = ver.split("@");
                        ver = vparts[0];
                        ext = vparts[1];
                    }
                    String path = pkg + "/" + art + "/" + ver + "/" + art + "-" + ver + "." + ext;
                    entries.add(librariesDir.resolve(path).toString());
                }
            }
        }

        // JAR principal do Minecraft por último
        entries.add(jarPath.toString());
        return String.join(sep, entries);
    }
}
