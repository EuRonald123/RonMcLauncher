package com.ronmclauncher.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ronmclauncher.os.OSdetection;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class DownloadManager {

    // Baixa uma URL e retorna o conteúdo como String
    public static String downloadString(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "ron-mclauncher/1.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    // Baixa um arquivo para o disco — pula se já existir (e segue redirects)
    public static void downloadFile(String url, Path dest) throws Exception {
        if (Files.exists(dest) && Files.size(dest) > 0){
            return;
        }

        Files.createDirectories(dest.getParent());
        HttpURLConnection conn = null;
        String currentUrl = url;
        int redirects = 0;
        
        while (redirects < 5) {
            conn = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "ron-mclauncher/1.0");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            
            int status = conn.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                currentUrl = conn.getHeaderField("Location");
                redirects++;
            } else {
                break;
            }
        }

        if (conn == null || conn.getResponseCode() != 200) {
            System.err.println("[AVISO] HTTP " + (conn != null ? conn.getResponseCode() : "null") + " → " + url);
            throw new Exception("Falha no download: HTTP " + (conn != null ? conn.getResponseCode() : "null"));
        }
        try (InputStream is = conn.getInputStream()) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String findVersionUrl(String manifestJson, String version) {
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        JsonArray versions = manifest.getAsJsonArray("versions");

        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (v.get("id").getAsString().equals(version)) {
                return v.get("url").getAsString();
            }
        }
        return null;
    }

    public static void printLatestVersions(String manifestJson, int limit) {
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();
        JsonArray versions = manifest.getAsJsonArray("versions");
        System.out.println("\n--- Últimas Versões Disponíveis ---");
        int count = 0;
        for (JsonElement el : versions) {
            if (count >= limit) break;
            JsonObject v = el.getAsJsonObject();
            String id = v.get("id").getAsString();
            String type = v.get("type").getAsString();
            // Mostrar apenas releases e snapshots recentes fica mais amigável
            if (type.equals("release") || type.equals("snapshot")) {
                System.out.printf("- %s (%s)\n", id, type);
                count++;
            }
        }
        System.out.println("-----------------------------------");
    }

    public static boolean isValidVersion(String manifestJson, String version) {
        return findVersionUrl(manifestJson, version) != null;
    }

    public static void downloadClientJar(JsonObject versionData, Path versionDir, String mcVersion) throws Exception {
        String jarUrl = versionData
            .getAsJsonObject("downloads")
            .getAsJsonObject("client")
            .get("url").getAsString();

        Path jarPath = versionDir.resolve(mcVersion + ".jar");
        System.out.println("[3/6] Baixando client JAR...");
        downloadFile(jarUrl, jarPath);
        System.out.println("OK! JAR salvo em: " + jarPath);
    }

    public static void downloadLibraries(JsonObject versionData, Path librariesDir, Path nativesDir) throws Exception {
        JsonArray libraries = versionData.getAsJsonArray("libraries");
        int total = libraries.size();
        int count = 0;

        for (JsonElement libEl : libraries) {
            JsonObject lib = libEl.getAsJsonObject();
            count++;

            if (lib.has("rules") && !OSdetection.matchesRules(lib.getAsJsonArray("rules"))){
                continue;
            }
            if (!lib.has("downloads")) {
                continue;
            }

            JsonObject downloads = lib.getAsJsonObject("downloads");

            // Library comum
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                String path = artifact.get("path").getAsString();
                String url  = artifact.get("url").getAsString();
                Path   dest = librariesDir.resolve(path);
                downloadFile(url, dest);
            }

            // Natives (arquivos nativos do SO: .so, .dll, .dylib)
            if (downloads.has("classifiers")) {
                String nativeKey = getNativeKey(lib);
                if (nativeKey != null) {
                    JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                    if (classifiers.has(nativeKey)) {
                        JsonObject nat  = classifiers.getAsJsonObject(nativeKey);
                        String     path = nat.get("path").getAsString();
                        String     url  = nat.get("url").getAsString();
                        Path       dest = librariesDir.resolve(path);
                        downloadFile(url, dest);
                        extractNatives(dest, nativesDir);
                    }
                }
            }

            System.out.printf("\r[4/6] Libraries: %d/%d", count, total);
        }
        System.out.println(" OK!");
    }

    public static String getNativeKey(JsonObject lib) {
        if (!lib.has("natives")) return null;
        JsonObject natives = lib.getAsJsonObject("natives");
        String os = OSdetection.currentOs();
        if (!natives.has(os)) return null;
        String key  = natives.get(os).getAsString();
        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
        return key.replace("${arch}", arch);
    }

    public static void extractNatives(Path jarFile, Path nativesDir) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jarFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory()
                        && !name.startsWith("META-INF")
                        && (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib"))) {
                    Path out = nativesDir.resolve(Path.of(name).getFileName());
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    public static void downloadAssets(JsonObject versionData, String gameDir) throws Exception {
        JsonObject assetIndexInfo = versionData.getAsJsonObject("assetIndex");
        String assetIndexId  = assetIndexInfo.get("id").getAsString();
        String assetIndexUrl = assetIndexInfo.get("url").getAsString();

        Path assetsDir  = Path.of(gameDir, "assets");
        Path indexesDir = assetsDir.resolve("indexes");
        Path objectsDir = assetsDir.resolve("objects");
        Path indexFile  = indexesDir.resolve(assetIndexId + ".json");

        Files.createDirectories(indexesDir);
        Files.createDirectories(objectsDir);

        // Baixa o índice de assets
        downloadFile(assetIndexUrl, indexFile);

        // Baixa cada asset pelo hash SHA-1
        JsonObject objects = JsonParser.parseString(Files.readString(indexFile))
                                    .getAsJsonObject()
                                    .getAsJsonObject("objects");

        int total = objects.size();
        int count = 0;

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject obj  = entry.getValue().getAsJsonObject();
            String hash     = obj.get("hash").getAsString();
            String prefix   = hash.substring(0, 2);
            String url      = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            Path   dest     = objectsDir.resolve(prefix).resolve(hash);

            Files.createDirectories(dest.getParent());
            downloadFile(url, dest);
            count++;
            System.out.printf("\r[5/6] Assets: %d/%d", count, total);
        }
        System.out.println(" OK!");
    }

    public static List<String> getLocalVersions(String gameDir) {
        List<String> local = new ArrayList<>();
        Path versionsDir = Path.of(gameDir, "versions");
        if (!Files.exists(versionsDir)) return local;
    
        try (var stream = Files.list(versionsDir)) {
            stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .forEach(local::add);
        } catch (Exception e) {
            // ignora
        }
        return local;
    }
}