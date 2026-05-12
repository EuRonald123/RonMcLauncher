package com.ronmclauncher;

import com.google.gson.*;
import com.ronmclauncher.classpath.GameRunner;
import com.ronmclauncher.manager.DownloadManager;
import com.ronmclauncher.manager.ProfileManager;

import java.nio.file.*;
import java.util.Scanner;

public class MinecraftLauncher {

    // ============================================================
    // CONFIGURAÇÕES
    // ============================================================
    static final String GAME_DIR   = com.ronmclauncher.os.OSdetection.getDefaultGameDir();

    static final String MANIFEST_URL =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Ron MC Launcher ===");
        Scanner scanner = new Scanner(System.in);

        String mcVersion = null;
        String username = null;

        ProfileManager.Profile profile = ProfileManager.loadProfile();
        boolean createNew = true;

        if (profile != null && profile.username != null && profile.version != null) {
            System.out.println("Perfil encontrado!");
            System.out.println("Usuário: " + profile.username + " | Versão: " + profile.version);
            System.out.print("Deseja usar este perfil? [1] Sim  [2] Criar/Alterar (Novo): ");
            String op = scanner.nextLine();
            if (op.trim().equals("1")) {
                mcVersion = profile.version;
                username = profile.username;
                createNew = false;
            }
        }

        System.out.println("\n[1/6] Buscando informações dos servidores da Mojang...");
        String manifestJson = DownloadManager.downloadString(MANIFEST_URL);

        if (createNew) {
            DownloadManager.printLatestVersions(manifestJson, 15);
            while (true) {
                System.out.print("\nDigite a versão desejada (ex: 1.20.1): ");
                mcVersion = scanner.nextLine().trim();
                if (DownloadManager.isValidVersion(manifestJson, mcVersion)) {
                    break;
                } else {
                    System.out.println("[ERRO] Versão não encontrada! Preste atenção na lista seu porra");
                }
            }

            while (true) {
                System.out.print("Digite o seu nome de usuário: ");
                username = scanner.nextLine().trim();
                if (!username.isEmpty() && username.length() >= 3) {
                    break;
                } else {
                    System.out.println("[ERRO] Nome inválido. TFD");
                }
            }

            // Salva para a próxima vez
            ProfileManager.saveProfile(username, mcVersion);
            System.out.println("Perfil salvo com sucesso!\n");
        }

        System.out.println("Iniciando com:");
        System.out.println("Versão  : " + mcVersion);
        System.out.println("Usuário : " + username);
        System.out.println("Dir     : " + GAME_DIR);
        System.out.println();

        // 2. Acha a URL do JSON da versão escolhida
        String versionUrl = DownloadManager.findVersionUrl(manifestJson, mcVersion);
        if (versionUrl == null) {
            System.err.println("[ERRO] Versão " + mcVersion + " não encontrada!");
            return;
        }

        // 3. Baixa o JSON da versão
        System.out.println("[2/6] Baixando metadata da versão " + mcVersion + "...");
        Path versionDir  = Path.of(GAME_DIR, "versions", mcVersion);
        Path versionJson = versionDir.resolve(mcVersion + ".json");
        Files.createDirectories(versionDir);
        DownloadManager.downloadFile(versionUrl, versionJson);

        System.out.println("OK! JSON da versão salvo em: " + versionJson);

        // 4. Lê o JSON da versão e baixa o JAR
        JsonObject versionData = JsonParser.parseString(
            Files.readString(versionJson)
        ).getAsJsonObject();

        DownloadManager.downloadClientJar(versionData, versionDir, mcVersion);

        // 5. Baixa libraries e extrai natives
        Path librariesDir = Path.of(GAME_DIR, "libraries");
        Path nativesDir   = versionDir.resolve("natives");
        Files.createDirectories(librariesDir);
        Files.createDirectories(nativesDir);
        DownloadManager.downloadLibraries(versionData, librariesDir, nativesDir);

        // 6. Baixa assets
        DownloadManager.downloadAssets(versionData, GAME_DIR);

        // 7. Configura o Java nativo (isolado) da versão
        String javaBin = com.ronmclauncher.manager.JavaManager.getJavaPath(versionData, GAME_DIR);

        // 8. Executa!
        GameRunner.launch(versionData, librariesDir, versionDir.resolve(mcVersion + ".jar"), nativesDir, mcVersion, username, GAME_DIR, javaBin);
        
        scanner.close();
    }
}