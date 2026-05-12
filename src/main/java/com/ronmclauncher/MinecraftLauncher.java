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
        Color bgDark     = new Color(30, 30, 30);
        Color bgPanel    = new Color(45, 45, 45);
        Color bgField    = new Color(60, 60, 60);
        Color fgText     = new Color(220, 220, 220);
        Color fgLabel    = new Color(160, 160, 160);
        Color btnGreen   = new Color(88, 157, 67);
        Color btnHover   = new Color(108, 177, 87);

        JFrame frame = new JFrame("Ron MC Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setSize(400, 220);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(bgDark);
        frame.setLayout(new BorderLayout(0, 0));

        // Painel central com os campos
        JPanel centerPanel = new JPanel(new GridLayout(2, 2, 8, 12));
        centerPanel.setBackground(bgPanel);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(25, 25, 15, 25));

        JLabel lblUser = new JLabel("Usuário");
        lblUser.setForeground(fgLabel);
        lblUser.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JTextField txtUsername = new JTextField(12);
        txtUsername.setBackground(bgField);
        txtUsername.setForeground(fgText);
        txtUsername.setCaretColor(fgText);
        txtUsername.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        txtUsername.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel lblVersion = new JLabel("Versão");
        lblVersion.setForeground(fgLabel);
        lblVersion.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JComboBox<String> comboVersion = new JComboBox<>(new String[]{"Carregando..."});
        comboVersion.setBackground(bgField);
        comboVersion.setForeground(fgText);
        comboVersion.setFont(new Font("SansSerif", Font.PLAIN, 13));

        centerPanel.add(lblUser);
        centerPanel.add(txtUsername);
        centerPanel.add(lblVersion);
        centerPanel.add(comboVersion);

        // Painel inferior com barra e botão
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setBackground(bgDark);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 25, 20, 25));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Aguardando...");
        progressBar.setVisible(false);
        progressBar.setForeground(btnGreen);
        progressBar.setBackground(bgField);
        progressBar.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JButton btnPlay = new JButton("JOGAR");
        btnPlay.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnPlay.setBackground(btnGreen);
        btnPlay.setForeground(Color.WHITE);
        btnPlay.setFocusPainted(false);
        btnPlay.setBorderPainted(false);
        btnPlay.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPlay.setPreferredSize(new Dimension(0, 38));

        // Efeito hover no botão
        btnPlay.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnPlay.setBackground(btnHover);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnPlay.setBackground(btnGreen);
            }
        });

        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(btnPlay, BorderLayout.CENTER);

        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // --- O resto do código interno permanece igual ao seu ---

        ProfileManager.Profile profile = ProfileManager.loadProfile();
        if (profile != null && profile.username != null) {
            txtUsername.setText(profile.username);
        }

        new Thread(() -> {
            try {
                String manifestJson = DownloadManager.downloadString(MANIFEST_URL);
                JsonObject root = JsonParser.parseString(manifestJson).getAsJsonObject();
                JsonArray versions = root.getAsJsonArray("versions");

                List<String> displayVersions = new ArrayList<>();
                int releasesCount = 0, snapshotCount = 0;

                for (JsonElement elem : versions) {
                    JsonObject v = elem.getAsJsonObject();
                    String id   = v.get("id").getAsString();
                    String type = v.get("type").getAsString();
                    if (type.equals("release") && releasesCount < 3) { displayVersions.add(id); releasesCount++; }
                    else if (type.equals("snapshot") && snapshotCount < 1) { displayVersions.add(id); snapshotCount++; }
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
                SwingUtilities.invokeLater(() -> { comboVersion.removeAllItems(); comboVersion.addItem("1.20.1"); });
            }
        }).start();

        btnPlay.addActionListener(e -> {
            String username = txtUsername.getText().trim();
            String version  = (String) comboVersion.getSelectedItem();

            if (username.length() < 3) {
                JOptionPane.showMessageDialog(frame, "Nome de usuário muito curto!", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

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
                        JOptionPane.showMessageDialog(frame, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
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