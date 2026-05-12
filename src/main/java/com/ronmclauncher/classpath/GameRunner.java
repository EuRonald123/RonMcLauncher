package com.ronmclauncher.classpath;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameRunner {

    public static void launch(JsonObject versionData, Path librariesDir, Path jarPath, Path nativesDir, String mcVersion, String username, String gameDir, String javaBin) throws Exception {
        String classpath = ClassPathBuilder.buildClasspath(versionData, librariesDir, jarPath);
        String mainClass = versionData.get("mainClass").getAsString();
        String assetIndex = versionData.getAsJsonObject("assetIndex").get("id").getAsString();
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString();

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);

        // Flags da JVM
        cmd.add("-Xmx2G");
        cmd.add("-Xms512M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-Djava.library.path=" + nativesDir);
        cmd.add("-Dminecraft.launcher.brand=ron-mclauncher");
        cmd.add("-Dminecraft.launcher.version=1.0.0");

        // Classpath
        cmd.add("-cp");
        cmd.add(classpath);

        // Main class
        cmd.add(mainClass);

        // Argumentos do Minecraft
        cmd.add("--username");    cmd.add(username);
        cmd.add("--version");     cmd.add(mcVersion);
        cmd.add("--gameDir");     cmd.add(gameDir);
        cmd.add("--assetsDir");   cmd.add(Path.of(gameDir, "assets").toString());
        cmd.add("--assetIndex");  cmd.add(assetIndex);
        cmd.add("--uuid");        cmd.add(uuid.replace("-", ""));
        cmd.add("--accessToken"); cmd.add("0");
        cmd.add("--userType");    cmd.add("mojang");
        cmd.add("--versionType"); cmd.add("release");

        System.out.println("[6/6] Iniciando Minecraft " + mcVersion + " e fechando o launcher...\n");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(gameDir));
        // Remove inheritIO para silenciar a saída do processo
        // Remove waitFor para não travar o launcher
        pb.start();
    }
}