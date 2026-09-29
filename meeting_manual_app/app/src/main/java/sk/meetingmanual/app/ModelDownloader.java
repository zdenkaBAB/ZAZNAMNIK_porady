package sk.meetingmanual.app;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads the multilingual Whisper small quantized model once; audio is never uploaded. */
public final class ModelDownloader {
    public static final String MODEL_NAME = "ggml-small-q5_1.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin?download=true";
    private ModelDownloader() {}

    public interface Callback {
        void onProgress(int percent, long downloaded, long total);
        void onSuccess(File file);
        void onError(String error);
    }

    public static File modelFile(android.content.Context c) {
        File dir = new File(c.getExternalFilesDir("models"), "whisper");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, MODEL_NAME);
    }

    public static boolean isInstalled(android.content.Context c) {
        File f = modelFile(c);
        return f.exists() && f.length() > 150_000_000L;
    }

    public static void download(android.content.Context c, Callback callback) {
        new Thread(() -> {
            File target = modelFile(c);
            File temp = new File(target.getParentFile(), MODEL_NAME + ".part");
            HttpURLConnection conn = null;
            try {
                URL url = new URL(MODEL_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "MeetingManual/0.7");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                long total = conn.getContentLengthLong();
                long done = 0;
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                    byte[] buf = new byte[1024 * 1024];
                    int n;
                    int last = -1;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        done += n;
                        int p = total > 0 ? (int)Math.min(100, done * 100 / total) : -1;
                        if (p != last) {
                            last = p;
                            final int fp = p;
                            final long fd = done;
                            final long ft = total;
                            new Handler(Looper.getMainLooper()).post(() -> callback.onProgress(fp, fd, ft));
                        }
                    }
                }
                if (target.exists()) target.delete();
                if (!temp.renameTo(target)) throw new IOException("Nepodarilo sa uložiť model.");
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(target));
            } catch (Exception e) {
                temp.delete();
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage() == null ? "Sťahovanie zlyhalo." : e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "WhisperModelDownload").start();
    }
}
