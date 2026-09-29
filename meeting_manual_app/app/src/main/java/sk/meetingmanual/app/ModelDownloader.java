package sk.meetingmanual.app;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/** Downloads the free Parakeet TDT v3 INT8 model from the sherpa-onnx GitHub release and extracts it locally. */
public final class ModelDownloader {
    private static final String MODEL_DIR_NAME = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8";
    private static final String ARCHIVE_NAME = "parakeet-tdt-v3-int8.tar.bz2";
    private static final String URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onProgress(int percent, long downloaded, long total);
        void onSuccess(File modelDir);
        void onError(String error);
    }

    private ModelDownloader() {}

    public static File modelDir(Context context) {
        return new File(context.getExternalFilesDir("models"), MODEL_DIR_NAME);
    }

    public static boolean isInstalled(Context context) {
        File dir = modelDir(context);
        return new File(dir, "encoder.int8.onnx").length() > 500_000_000L
                && new File(dir, "decoder.int8.onnx").length() > 1_000_000L
                && new File(dir, "joiner.int8.onnx").length() > 1_000_000L
                && new File(dir, "tokens.txt").length() > 1_000L;
    }

    public static void download(Context context, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                File base = new File(context.getExternalFilesDir("models"), "parakeet");
                if (!base.exists() && !base.mkdirs()) throw new IOException("Nepodarilo sa vytvoriť priečinok modelu.");
                File archive = new File(base, ARCHIVE_NAME);
                downloadResumable(archive, callback);
                extractModel(archive, base, callback);
                if (!isInstalled(context)) throw new IOException("Model sa nepodarilo overiť po rozbalení.");
                // The archive is no longer needed after successful extraction.
                if (!archive.delete()) archive.deleteOnExit();
                callback.onSuccess(modelDir(context));
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        });
    }

    private static void downloadResumable(File target, Callback callback) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= 6; attempt++) {
            try {
                long existing = target.exists() ? target.length() : 0L;
                URL url = new URL(URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(120000);
                conn.setInstanceFollowRedirects(true);
                if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
                int code = conn.getResponseCode();
                boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
                if (!append && existing > 0) {
                    existing = 0;
                    if (target.exists() && !target.delete()) throw new IOException("Nepodarilo sa obnoviť sťahovanie.");
                }
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP " + code);
                }
                long total = conn.getContentLengthLong();
                if (total > 0) total += existing;
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     RandomAccessFile out = new RandomAccessFile(target, "rw")) {
                    out.seek(existing);
                    byte[] buf = new byte[1024 * 1024];
                    long downloaded = existing;
                    int lastPercent = -1;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        downloaded += n;
                        int percent = total > 0 ? (int)((downloaded * 100L) / total) : -1;
                        if (percent != lastPercent || downloaded % (10L * 1024L * 1024L) < n) {
                            lastPercent = percent;
                            callback.onProgress(percent, downloaded, total);
                        }
                    }
                } finally { conn.disconnect(); }
                if (target.length() < 400_000_000L) throw new IOException("Stiahnutý súbor je príliš malý: " + target.length());
                return;
            } catch (Exception e) {
                lastError = e;
                try { Thread.sleep(1500L * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        throw lastError == null ? new IOException("Sťahovanie zlyhalo.") : lastError;
    }

    private static void extractModel(File archive, File base, Callback callback) throws Exception {
        File targetDir = new File(base.getParentFile(), MODEL_DIR_NAME);
        File tempDir = new File(base, MODEL_DIR_NAME + ".partial");
        deleteRecursively(tempDir);
        if (!tempDir.mkdirs()) throw new IOException("Nepodarilo sa vytvoriť dočasný priečinok modelu.");
        callback.onProgress(-1, 0, 0);
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(archive));
             BZip2CompressorInputStream bz = new BZip2CompressorInputStream(fileIn, true);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                String prefix = MODEL_DIR_NAME + "/";
                if (!name.startsWith(prefix)) continue;
                String relative = name.substring(prefix.length());
                if (relative.length() == 0 || entry.isDirectory()) continue;
                if (!(relative.equals("encoder.int8.onnx") || relative.equals("decoder.int8.onnx") || relative.equals("joiner.int8.onnx") || relative.equals("tokens.txt"))) continue;
                File out = new File(tempDir, relative);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Nepodarilo sa vytvoriť priečinok.");
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buf = new byte[1024 * 1024]; int n;
                    while ((n = tar.read(buf)) != -1) os.write(buf, 0, n);
                }
            }
        }
        if (!new File(tempDir, "encoder.int8.onnx").exists()) throw new IOException("V archíve chýba encoder.int8.onnx.");
        deleteRecursively(targetDir);
        if (!tempDir.renameTo(targetDir)) throw new IOException("Nepodarilo sa dokončiť inštaláciu modelu.");
    }

    private static File modelDir(File modelsRoot) {
        return new File(modelsRoot, MODEL_DIR_NAME);
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
