package com.ronmclauncher.manager;

import com.ronmclauncher.os.OSdetection;

import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Gerencia skins do usuário no RonMcLauncher.
 *
 * Funciona para qualquer um nessa bagaça
 * anonimo so envia o png e recebe url usando freeimage.host API.
 */
public class SkinManager {

    // API do freeimage.host — Sem cloudflare para hotlink de imagens PNG
    private static final String UPLOAD_API = "https://freeimage.host/api/1/upload";

    // Pasta local onde as skins ficam armazenadas
    private static final Path SKIN_DIR = Path.of(OSdetection.getDefaultGameDir(), "ron_launcher_skins");

    // -------------------------------------------------------------------------
    // SALVAR SKIN LOCALMENTE
    // -------------------------------------------------------------------------

    public static void saveSkinLocally(String username, byte[] skinBytes, String model) throws Exception {
        if (!isPng(skinBytes)) {
            throw new IllegalArgumentException("O arquivo selecionado não é um PNG válido.");
        }

        Files.createDirectories(SKIN_DIR);

        // Salva o PNG
        Files.write(SKIN_DIR.resolve(username + ".png"), skinBytes);

        // Salva o modelo escolhido (classic / slim)
        Files.writeString(SKIN_DIR.resolve(username + "_model.txt"), model);

        // Invalida a URL antiga pois a skin mudou
        Path oldUrl = SKIN_DIR.resolve(username + "_url.txt");
        Files.deleteIfExists(oldUrl);

        System.err.println("[SKIN] Skin salva localmente para: " + username);
    }

    // -------------------------------------------------------------------------
    // UPLOAD PARA FREEIMAGE.HOST
    // -------------------------------------------------------------------------

    public static String uploadSkin(String username) throws Exception {
        Path skinFile = SKIN_DIR.resolve(username + ".png");
        if (!Files.exists(skinFile)) {
            throw new FileNotFoundException(
                "Nenhuma skin encontrada para '" + username + "'. Escolha uma skin primeiro."
            );
        }

        byte[] skinBytes = Files.readAllBytes(skinFile);
        System.err.println("[SKIN] Enviando skin para hospedagem livre...");

        String boundary = "----RonMCBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, skinBytes, username + ".png");

        HttpURLConnection conn = (HttpURLConnection) URI.create(UPLOAD_API).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int status = conn.getResponseCode();
        InputStream responseStream = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        String jsonResponse = new String(responseStream.readAllBytes()).trim();

        if (status != 200 || !jsonResponse.contains("\"url\"")) {
            throw new Exception("Falha no upload. HTTP " + status + " -> " + jsonResponse);
        }

        // Extrai a URL do JSON usando open-source Gson incluso no launcher
        String skinUrl = com.google.gson.JsonParser.parseString(jsonResponse)
                .getAsJsonObject().getAsJsonObject("image").get("url").getAsString();

        // Salva a URL localmente para não precisar re-enviar
        Files.writeString(SKIN_DIR.resolve(username + "_url.txt"), skinUrl);

        System.err.println("[SKIN] Upload concluído! URL: " + skinUrl);
        return skinUrl;
    }

    // -------------------------------------------------------------------------
    // CONSULTAS
    // -------------------------------------------------------------------------

    public static String getSkinUrl(String username) {
        Path urlFile = SKIN_DIR.resolve(username + "_url.txt");
        if (!Files.exists(urlFile)) return null;
        try {
            return Files.readString(urlFile).trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasSkinLocally(String username) {
        return Files.exists(SKIN_DIR.resolve(username + ".png"));
    }

    public static String getModel(String username) {
        Path modelFile = SKIN_DIR.resolve(username + "_model.txt");
        if (!Files.exists(modelFile)) return "classic";
        try {
            return Files.readString(modelFile).trim();
        } catch (Exception e) {
            return "classic";
        }
    }

    public static Path getSkinPath(String username) {
        Path p = SKIN_DIR.resolve(username + ".png");
        return Files.exists(p) ? p : null;
    }

    // -------------------------------------------------------------------------
    // HELPERS PRIVADOS
    // -------------------------------------------------------------------------

    private static byte[] buildMultipartBody(String boundary, byte[] fileBytes, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String dd   = "--";

        // Campo: key (API FreeImageHost Pública)
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"key\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("6d207e02198a847aa98d0a2a901485a5".getBytes());
        out.write(crlf.getBytes());

        // Campo: action
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"action\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("upload".getBytes());
        out.write(crlf.getBytes());

        // Campo: format
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"format\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("json".getBytes());
        out.write(crlf.getBytes());

        // Campo: source (o arquivo)
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"source\"; filename=\"" + fileName + "\"" + crlf).getBytes());
        out.write(("Content-Type: image/png" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write(fileBytes);
        out.write(crlf.getBytes());

        // Fechamento
        out.write((dd + boundary + dd + crlf).getBytes());

        return out.toByteArray();
    }

    private static boolean isPng(byte[] data) {
        if (data == null || data.length < 8) return false;
        return (data[0] & 0xFF) == 0x89
            && (data[1] & 0xFF) == 0x50  
            && (data[2] & 0xFF) == 0x4E  
            && (data[3] & 0xFF) == 0x47  
            && (data[4] & 0xFF) == 0x0D
            && (data[5] & 0xFF) == 0x0A
            && (data[6] & 0xFF) == 0x1A
            && (data[7] & 0xFF) == 0x0A;
    }
}