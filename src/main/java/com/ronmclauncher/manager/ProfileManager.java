package com.ronmclauncher.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ronmclauncher.os.OSdetection;

import java.nio.file.Files;
import java.nio.file.Path;

public class ProfileManager {
    
    private static final Path PROFILE_PATH = Path.of(OSdetection.getDefaultGameDir(), "ron_launcher_profile.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Profile {
        public String username;
        public String version;
    }

    public static Profile loadProfile() {
        if (Files.exists(PROFILE_PATH)) {
            try {
                String json = Files.readString(PROFILE_PATH);
                return GSON.fromJson(json, Profile.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public static void saveProfile(String username, String version) {
        try {
            Profile p = new Profile();
            p.username = username;
            p.version = version;
            Files.createDirectories(PROFILE_PATH.getParent());
            Files.writeString(PROFILE_PATH, GSON.toJson(p));
        } catch (Exception e) {
            System.err.println("Erro ao salvar perfil: " + e.getMessage());
        }
    }
}