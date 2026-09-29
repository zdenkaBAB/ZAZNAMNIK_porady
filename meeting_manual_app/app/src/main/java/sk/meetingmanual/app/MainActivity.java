package sk.meetingmanual.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText title;
    TextView timer, status, transcript;
    Button record, stop, transcribe, manual, export;
    long started;
    Handler h = new Handler(Looper.getMainLooper());
    StringBuilder text = new StringBuilder();
    boolean transcribing = false;
    File lastAudio;
    String lastManual = "";

    Runnable tick = new Runnable() {
        public void run() {
            if (started > 0) {
                long s = (System.currentTimeMillis() - started) / 1000;
                timer.setText(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
                h.postDelayed(this, 500);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        title=findViewById(R.id.title); timer=findViewById(R.id.timer); status=findViewById(R.id.status);
        transcript=findViewById(R.id.transcript); record=findViewById(R.id.record); stop=findViewById(R.id.stop);
        transcribe=findViewById(R.id.transcribe); manual=findViewById(R.id.manual); export=findViewById(R.id.export);
        record.setOnClickListener(v->startRec()); stop.setOnClickListener(v->stopRec());
        transcribe.setOnClickListener(v->startLocalTranscription()); manual.setOnClickListener(v->makeManual()); export.setOnClickListener(v->doExport());
        stop.setEnabled(false); transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        status.setText("Pripravené. Nahrávka aj spracovanie sú navrhnuté lokálne.");
    }

    void startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return;
        }
        Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_START); startForegroundService(i);
        started=System.currentTimeMillis(); h.post(tick); record.setEnabled(false); stop.setEnabled(true);
        transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        status.setText("🔴 Nahrávanie prebieha. Telefón môže byť uzamknutý.");
    }

    void stopRec() {
        Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_STOP); startService(i);
        long duration=(System.currentTimeMillis()-started)/1000; started=0;
        lastAudio = RecordingService.getLastRecording(this);
        record.setEnabled(true); stop.setEnabled(false); transcribe.setEnabled(true);
        status.setText("Nahrávanie ukončené ("+duration/60+" min). Teraz vytvor prepis.");
    }

    void startLocalTranscription() {
        if (lastAudio == null || !lastAudio.exists()) {
            lastAudio = RecordingService.getLastRecording(this);
        }
        if (lastAudio == null || !lastAudio.exists()) { status.setText("Nenašla sa nahrávka."); return; }
        transcribing=true; transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        status.setText("Prepisujem lokálne. Pri niekoľkohodinovej nahrávke to môže trvať dlhšie.");
        // The first prototype keeps a deterministic offline fallback. The Whisper adapter is prepared separately.
        // When the bundled whisper.cpp model is installed, WhisperTranscriber can replace this fallback.
        new Thread(() -> {
            String fallback = "[LOKÁLNY PREPIS – Whisper model ešte nie je pribalený]\n\nNahrávka: " + lastAudio.getName() +
                    "\n\nĎalší krok: v nastaveniach aplikácie vyber/načítaj lokálny Whisper model a spusti presný prepis.";
            runOnUiThread(() -> { text.setLength(0); text.append(fallback); transcript.setText(text.toString()); transcribing=false; manual.setEnabled(true); status.setText("Prepis pripravený. Môžeš vytvoriť pracovný manuál."); });
        }).start();
    }

    void makeManual() {
        lastManual = ManualBuilder.build(title.getText().toString(), text.toString());
        transcript.setText(lastManual);
        export.setEnabled(true);
        status.setText("Manuál vytvorený lokálne. Skontroluj ho pred exportom.");
    }

    void doExport() {
        try {
            String source = lastManual.isEmpty() ? text.toString() : lastManual;
            File dir=new File(getExternalFilesDir(null),"exports");
            File f=DocxExporter.export(dir,title.getText().toString(),source,lastAudio==null?"":lastAudio.getName());
            Intent s=new Intent(Intent.ACTION_SEND); s.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".provider",f);
            s.putExtra(Intent.EXTRA_STREAM,uri); s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(s,"Exportovať Word"));
            status.setText("Word dokument pripravený.");
        } catch(Exception e){ status.setText("Export sa nepodaril: "+e.getMessage()); }
    }
}
