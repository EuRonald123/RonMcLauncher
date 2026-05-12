package com.ronmclauncher;

import com.google.gson.*;
import com.ronmclauncher.classpath.GameRunner;
import com.ronmclauncher.manager.DownloadManager;
import com.ronmclauncher.manager.ProfileManager;

import javax.swing.*;
import java.awt.*;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MinecraftLauncher {

    static final String GAME_DIR = com.ronmclauncher.os.OSdetection.getDefaultGameDir();
    static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    
    // Silencia qualquer System.out para não gastar processamento à toa
    static {
        System.setOut(new PrintStream(new OutputStream() {
            public void write(int b) {}
        }));
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(MinecraftLauncher::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Ron MC Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setSize(350, 190); // Pouco a mais só para a barra caber sem amassar
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        JPanel centerPanel = new JPanel(new GridLayout(2, 2, 5, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        centerPanel.add(new JLabel("Usuário:"));
        JTextField txtUsername = new JTextField(12);
        txtUsername.setBackground(new Color(80, 80, 80)); 
        txtUsername.setForeground(Color.WHITE);
        centerPanel.add(txtUsername);

        centerPanel.add(new JLabel("Versão:"));
        JComboBox<String> comboVersion = new JComboBox<>(new String[]{"Carregando..."});
        comboVersion.setBackground(new Color(80, 80, 80));
        comboVersion.setForeground(Color.WHITE);
        centerPanel.add(comboVersion);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 15, 20));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Aguardando...");
        progressBar.setVisible(false);
        
        JButton btnPlay = new JButton("Jogar");
        btnPlay.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(btnPlay, BorderLayout.CENTER);

        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Carrega último perfil
        ProfileManager.Profile profile = ProfileManager.loadProfile();
        if (profile != null && profile.username != null) {
            txtUsername.setText(profile.username);
        }

        // Thread para buscar as versões recentes e polular a lista
        new Thread(() -> {
            try {
                String manifestJson = DownloadManager.downloadString(MANIFEST_URL);
                JsonObject root = JsonParser.parseString(manifestJson).getAsJsonObject();
                JsonArray versions = root.getAsJsonArray("versions");
                
                List<String> displayVersions = new ArrayList<>();
                int releasesCount = 0;
                int snapshotCount = 0;
                
                for (JsonElement elem : versions) {
                    JsonObject v = elem.getAsJsonObject();
                    String id = v.get("id").getAsString();
                    String type = v.get("type").getAsString();
                    
                    if (type.equals("release") && releasesCount < 3) {
                        displayVersions.add(id);
                        releasesCount++;
                    } else if (type.equals("snapshot") && snapshotCount < 1) {
                        displayVersions.add(id);
                        snapshotCount++;
                    }
                    if (releasesCount >= 3 && snapshotCount >= 1) break;
                }
                
                SwingUtilities.invokeLater(() -> {
                    comboVersion.removeAllItems();
                    for (String dv : displayVersions) comboVersion.addItem(dv);
                    
                    if (profile != null && profile.version != null && displayVersions.contains(profile.version)) {
                        comboVersion.setSelectedItem(profile.version);
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    comboVersion.removeAllItems();
                    comboVersion.addItem("1.20.1"); // fallback fallback
                });
            }
        }).start();

        btnPlay.addActionListener(e -> {
            String username = txtUsername.getText().trim();
            String version = (String) comboVersion.getSelectedItem();

            if (username.length() < 3) {
                JOptionPane.showMessageDialog(frame, "Nome de usuário muito curto!", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Preparar UI para carregamento
            txtUsername.setEnabled(false);
            comboVersion.setEnabled(false);
            btnPlay.setEnabled(false);
            
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            progressBar.setString("Baixando pacotes / Preparando...");

            new Thread(() -> {
                try {
                    launchGame(username, version);
                    
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(100);
                        progressBar.setString("Iniciando o jogo...");
                        try { Thread.sleep(1000); } catch (Exception ignored) {}
                        System.exit(0);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(false);
                        btnPlay.setEnabled(true);
                        txtUsername.setEnabled(true);
                        comboVersion.setEnabled(true);
                        JOptionPane.showMessageDialog(frame, "Erro ao iniciar o jogo: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });

        frame.setVisible(true);
    }

    private static void launchGame(String username, String mcVersion) throws Exception {
        String manifestJson = DownloadManager.downloadString(MANIFEST_URL);
        ProfileManager.saveProfile(username, mcVersion);
        String versionUrl = DownloadManager.findVersionUrl(manifestJson, mcVersion);

        if (versionUrl == null) throw new Exception("Versão não encontrada no servidor da Mojang.");

        Path versionDir  = Path.of(GAME_DIR, "versions", mcVersion);
        Path versionJson = versionDir.resolve(mcVersion + ".json");
        Files.createDirectories(versionDir);
        DownloadManager.downloadFile(versionUrl, versionJson);
        JsonObject versionData = JsonParser.parseString(Files.readString(versionJson)).getAsJsonObject();

        DownloadManager.downloadClientJar(versionData, versionDir, mcVersion);

        Path librariesDir = Path.of(GAME_DIR, "libraries");
        Path nativesDir   = versionDir.resolve("natives");
        Files.createDirectories(librariesDir);
        Files.createDirectories(nativesDir);
        DownloadManager.downloadLibraries(versionData, librariesDir, nativesDir);

        DownloadManager.downloadAssets(versionData, GAME_DIR);

        String javaBin = com.ronmclauncher.manager.JavaManager.getJavaPath(versionData, GAME_DIR);

        GameRunner.launch(versionData, librariesDir, versionDir.resolve(mcVersion + ".jar"), nativesDir, mcVersion, username, GAME_DIR, javaBin);
    }
}