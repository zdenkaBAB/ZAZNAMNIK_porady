package sk.meetingmanual.app;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/** Robust resumable downloader for the multilingual Whisper Small Q5 model. Audio is never uploaded. */
public final class ModelDownloader {
    public static final String MODEL_NAME = "ggml-small-q5_1.bin";
    private static final String MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin";
    private static final long MIN_MODEL_BYTES = 150_000_000L;
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
        return f.exists() && f.length() > MIN_MODEL_BYTES;
    }

    public static void download(android.content.Context c, Callback callback) {
        new Thread(() -> {
            File target = modelFile(c);
            File temp = new File(target.getParentFile(), MODEL_NAME + ".part");
            Handler main = new Handler(Looper.getMainLooper());
            Exception last = null;

            for (int attempt = 1; attempt <= 5; attempt++) {
                HttpURLConnection conn = null;
                try {
                    long existing = temp.exists() ? temp.length() : 0L;
                    URL url = new URL(MODEL_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(180000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "MeetingManual/0.7.1");
                    conn.setRequestProperty("Accept", "application/octet-stream");
                    if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");

                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_PARTIAL) {
                        // Resume the existing partial file.
                    } else if (code == HttpURLConnection.HTTP_OK) {
                        // Server ignored Range: restart cleanly.
                        existing = 0L;
                        if (temp.exists()) temp.delete();
                    } else if (code == 416) {
                        temp.delete();
                        throw new IOException("Neplatný rozsah sťahovania; skúšam od začiatku.");
                    } else {
                        throw new IOException("HTTP " + code);
                    }

                    long remoteLength = conn.getContentLengthLong();
                    long total = remoteLength > 0 ? existing + remoteLength : -1L;
                    long done = existing;
                    boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;

                    try (InputStream in = new BufferedInputStream(conn.getInputStream());
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(temp, append))) {
                        byte[] buf = new byte[1024 * 1024];
                        int n;
                        int last = -1;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                            done += n;
                            int percent = total > 0 ? (int)Math.min(100, done * 100 / total) : -1;
                            if (percent != last) {
                                last = percent;
                                final int fp = percent;
                                final long fd = done;
                                final long ft = total;
                                main.post(() -> callback.onProgress(fp, fd, ft));
                            }
                        }
                    }

                    if (!temp.exists() || temp.length() <= MIN_MODEL_BYTES) {
                        throw new IOException("Stiahnutý súbor je neúplný (" + (temp.exists() ? temp.length() : 0) + " B).");
                    }
                    if (target.exists()) target.delete();
                    if (!temp.renameTo(target)) throw new IOException("Nepodarilo sa uložiť model.");
                    final File ready = target;
                    main.post(() -> callback.onSuccess(ready));
                    return;
                } catch (Exception e) {
                    last = e;
                    final int a = attempt;
                    final String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    main.post(() -> callback.onProgress(-1, 0, 0));
                    try { Thread.sleep(1500L * a); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            String msg = last == null || last.getMessage() == null ? "Sťahovanie zlyhalo." : last.getMessage();
            main.post(() -> callback.onError(msg + " Skús to ešte raz; sťahovanie sa dá obnoviť."));
        }, "WhisperModelDownload").start();
    }
}
