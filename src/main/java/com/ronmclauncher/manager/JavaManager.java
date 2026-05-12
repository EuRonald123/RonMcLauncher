package com.ronmclauncher.manager;

import com.google.gson.JsonObject;
import com.ronmclauncher.os.OSdetection;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class JavaManager {

    public static String getJavaPath(JsonObject versionData, String gameDir) throws Exception {
        int majorVersion = 8; // Padrão se não informado
        if (versionData.has("javaVersion")) {
            majorVersion = versionData.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        }

        Path runtimesDir = Path.of(gameDir, "runtimes", "jre-" + majorVersion);
        String os = OSdetection.currentOs();
        String binName = os.equals("windows") ? "java.exe" : "java";
        
        // Verifica se já baixamos no passado
        Path javaBin = findJavaBin(runtimesDir, binName);
        if (javaBin != null) {
            return javaBin.toString();
        }

        System.out.println("\n[JAVA] O Minecraft precisa do Java " + majorVersion + ".");
        System.out.println("[JAVA] Baixando runtime isoladamente sem afetar seu OS...");
        downloadAndExtractJava(majorVersion, runtimesDir, os);

        javaBin = findJavaBin(runtimesDir, binName);
        if (javaBin != null) {
            if (!os.equals("windows")) {
                javaBin.toFile().setExecutable(true); // Dar permissão no Linux/Mac
            }
            return javaBin.toString();
        }

        System.err.println("[JAVA] Algo falhou no download. Tentando usar o Java nativo do sistema...");
        return "java";
    }

    private static Path findJavaBin(Path dir, String binName) throws Exception {
        if (!Files.exists(dir)) return null;
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                         .filter(p -> p.getFileName().toString().equals(binName) && 
                                      p.getParent().getFileName().toString().equals("bin"))
                         .findFirst().orElse(null);
        }
    }

    private static void downloadAndExtractJava(int version, Path destDir, String os) throws Exception {
        String arch = System.getProperty("os.arch").contains("aarch64") ? "aarch64" : "x64";
        String adoptOs = os.equals("osx") ? "mac" : os;
        
        // Tentamos baixar versão GA (Estável), se não existir tenta EA (Early Access - p/ javas do futuro como o 25)
        String baseUrl = "https://api.adoptium.net/v3/binary/latest/" + version + "/%s/" + adoptOs + "/" + arch + "/jdk/hotspot/normal/eclipse";
        
        String downloadUrl = String.format(baseUrl, "ga");
        if (!isUrlValid(downloadUrl)) {
            downloadUrl = String.format(baseUrl, "ea");
        }

        Files.createDirectories(destDir);
        String ext = os.equals("windows") ? ".zip" : ".tar.gz";
        Path archive = destDir.resolve("java_download" + ext);

        DownloadManager.downloadFile(downloadUrl, archive);

        System.out.println("[JAVA] Extraindo executáveis...");
        // O comando 'tar' já vem nativo no Linux, Mac e inclusive em Windows 10 pra extrair zip/targz
        ProcessBuilder pb = os.equals("windows") 
            ? new ProcessBuilder("tar", "-xf", archive.getFileName().toString())
            : new ProcessBuilder("tar", "-xzf", archive.getFileName().toString());

        pb.directory(destDir.toFile());
        pb.inheritIO();
        pb.start().waitFor();
        
        Files.deleteIfExists(archive);
        System.out.println("[JAVA] Java configurado com sucesso!\n");
    }

    private static boolean isUrlValid(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            return code == 200 || code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
        } catch (Exception e) {
            return false;
        }
    }
}