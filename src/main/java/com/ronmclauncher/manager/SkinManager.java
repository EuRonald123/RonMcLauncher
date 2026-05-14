package com.ronmclauncher.manager;

import com.ronmclauncher.os.OSdetection;

import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Gerencia skins do usuário no RonMcLauncher.
 *
 * Funciona para qualquer um nessa bagaça
 * anonimo so envia o png e recebe url
 */
public class SkinManager {

    // API do catbox.moe — upload anônimo, sem autenticação
    private static final String CATBOX_API = "https://catbox.moe/user/api.php";

    // Pasta local onde as skins ficam armazenadas
    private static final Path SKIN_DIR = Path.of(OSdetection.getDefaultGameDir(), "ron_launcher_skins");

    // -------------------------------------------------------------------------
    // SALVAR SKIN LOCALMENTE
    // -------------------------------------------------------------------------

    /**
     * Valida e salva o arquivo de skin (.png) localmente para o usuário.
     *
     * @param username  Nome do jogador
     * @param skinBytes Bytes do arquivo PNG (deve ser 64x64 pixels)
     * @param model     "classic" (Steve) ou "slim" (Alex)
     */
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
    // UPLOAD PARA CATBOX.MOE
    // -------------------------------------------------------------------------

    /**
     * Envia a skin do usuário para o catbox.moe de forma anônima e retorna a URL.
     * A URL retornada pode ser usada diretamente no SkinsRestorer via /skin url <url>.
     *
     * TOtalmente anonimo
     *
     * @param username Nome do jogador
     * @return URL pública permanente da skin (ex: https://files.catbox.moe/abc123.png)
     */
    public static String uploadToCatbox(String username) throws Exception {
        Path skinFile = SKIN_DIR.resolve(username + ".png");
        if (!Files.exists(skinFile)) {
            throw new FileNotFoundException(
                "Nenhuma skin encontrada para '" + username + "'. Escolha uma skin primeiro."
            );
        }

        byte[] skinBytes = Files.readAllBytes(skinFile);
        System.err.println("[SKIN] Enviando skin para catbox.moe...");

        // Monta o corpo multipart/form-data sem dependências externas
        String boundary = "----RonMCBoundary" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, skinBytes, username + ".png");

        // Faz a requisição POST
        HttpURLConnection conn = (HttpURLConnection) URI.create(CATBOX_API).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "ron-mclauncher/1.0");

        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int status = conn.getResponseCode();
        InputStream responseStream = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        // retorna url ou mensagem de erro
        String skinUrl = new String(responseStream.readAllBytes()).trim();

        if (status != 200 || !skinUrl.startsWith("https://")) {
            throw new Exception("Falha no upload para catbox.moe. HTTP " + status + " → " + skinUrl);
        }

        // Salva a URL localmente para não precisar re-enviar toda vez
        Files.writeString(SKIN_DIR.resolve(username + "_url.txt"), skinUrl);

        System.err.println("[SKIN] Upload concluído! URL: " + skinUrl);
        return skinUrl;
    }

    // -------------------------------------------------------------------------
    // CONSULTAS
    // -------------------------------------------------------------------------

    /**
     * Retorna a URL da skin já enviada anteriormente.
     * Retorna null se o usuário ainda não fez upload.
     */
    public static String getSkinUrl(String username) {
        Path urlFile = SKIN_DIR.resolve(username + "_url.txt");
        if (!Files.exists(urlFile)) return null;
        try {
            return Files.readString(urlFile).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Retorna true se o usuário já tem uma skin salva localmente.
     */
    public static boolean hasSkinLocally(String username) {
        return Files.exists(SKIN_DIR.resolve(username + ".png"));
    }

    /**
     * Retorna o modelo salvo ("classic" ou "slim"). Padrão: "classic".
     */
    public static String getModel(String username) {
        Path modelFile = SKIN_DIR.resolve(username + "_model.txt");
        if (!Files.exists(modelFile)) return "classic";
        try {
            return Files.readString(modelFile).trim();
        } catch (Exception e) {
            return "classic";
        }
    }

    /**
     * Retorna o caminho local do PNG da skin, ou null se não existir.
     */
    public static Path getSkinPath(String username) {
        Path p = SKIN_DIR.resolve(username + ".png");
        return Files.exists(p) ? p : null;
    }

    // -------------------------------------------------------------------------
    // HELPERS PRIVADOS
    // -------------------------------------------------------------------------

    /**
     * Monta o corpo multipart/form-data para a API do catbox.moe.
     *
     * Campos enviados:
     *   reqtype=fileupload  → tipo de operação exigido pela API
     *   userhash=           → vazio = upload anônimo (sem conta)
     *   fileToUpload        → o PNG da skin
     */
    private static byte[] buildMultipartBody(String boundary, byte[] fileBytes, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String dd   = "--";

        // Campo: reqtype
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"reqtype\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("fileupload".getBytes());
        out.write(crlf.getBytes());

        // Campo: userhash (vazio = anônimo)
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"userhash\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("".getBytes());
        out.write(crlf.getBytes());

        // Campo: fileToUpload (o PNG)
        out.write((dd + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"" + crlf).getBytes());
        out.write(("Content-Type: image/png" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write(fileBytes);
        out.write(crlf.getBytes());

        // Fechamento do multipart
        out.write((dd + boundary + dd + crlf).getBytes());

        return out.toByteArray();
    }

    /**
     * Verifica os magic bytes do PNG para garantir que o arquivo é válido
     * antes de salvar ou enviar.
     */
    private static boolean isPng(byte[] data) {
        if (data == null || data.length < 8) return false;
        return (data[0] & 0xFF) == 0x89
            && (data[1] & 0xFF) == 0x50  // P
            && (data[2] & 0xFF) == 0x4E  // N
            && (data[3] & 0xFF) == 0x47  // G
            && (data[4] & 0xFF) == 0x0D
            && (data[5] & 0xFF) == 0x0A
            && (data[6] & 0xFF) == 0x1A
            && (data[7] & 0xFF) == 0x0A;
    }
}