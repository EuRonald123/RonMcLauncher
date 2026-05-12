package com.ronmclauncher.os;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class OSdetection {

    public static String currentOs() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac")) return "osx";
        return "linux";
    }

    public static String getDefaultGameDir() {
        String os = currentOs();
        String userHome = System.getProperty("user.home");
        if (os.equals("windows")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                return appdata + "/.minecraft";
            }
            return userHome + "/AppData/Roaming/.minecraft";
        } else if (os.equals("osx")) {
            return userHome + "/Library/Application Support/minecraft";
        } else {
            return userHome + "/.minecraft";
        }
    }

    public static boolean matchesRules(JsonArray rules) {
        if (rules == null || rules.size() == 0) {
            return true;
        }
        boolean allow = false;
        String os = currentOs();
        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean match = true;
            if (rule.has("os")) {
                String ruleOs = rule.getAsJsonObject("os").get("name").getAsString();
                match = ruleOs.equals(os);
            }
            if (match) {
                allow = action.equals("allow");
            }
        }
        return allow;
    }
}
