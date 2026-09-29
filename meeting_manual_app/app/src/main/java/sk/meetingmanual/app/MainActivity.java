package sk.meetingmanual.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText title;
    TextView timer, status, transcript;
    Button record, pause, stop, archive, transcribe, manual, export, settings;
    long started;
    long pausedAt;
    long pausedTotal;
    Handler h = new Handler(Looper.getMainLooper());
    StringBuilder text = new StringBuilder();
    File lastAudio;
    String lastManual = "";
    boolean recording=false, paused=false;

    Runnable tick = new Runnable() {
        public void run() {
            if (recording) {
                long now=System.currentTimeMillis();
                long elapsed=(now-started-pausedTotal-(paused?now-pausedAt:0))/1000;
                timer.setText(String.format(Locale.US,"%02d:%02d:%02d",elapsed/3600,(elapsed%3600)/60,elapsed%60));
                h.postDelayed(this,500);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        title=findViewById(R.id.title); timer=findViewById(R.id.timer); status=findViewById(R.id.status); transcript=findViewById(R.id.transcript);
        record=findViewById(R.id.record); pause=findViewById(R.id.pause); stop=findViewById(R.id.stop); archive=findViewById(R.id.archive);
        transcribe=findViewById(R.id.transcribe); manual=findViewById(R.id.manual); export=findViewById(R.id.export); settings=findViewById(R.id.settings);
        record.setOnClickListener(v->startRec()); pause.setOnClickListener(v->togglePause()); stop.setOnClickListener(v->stopRec()); archive.setOnClickListener(v->startActivity(new Intent(this,ArchiveActivity.class)));
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        transcribe.setOnClickListener(v->startLocalTranscription()); manual.setOnClickListener(v->makeManual()); export.setOnClickListener(v->doExport());
        stop.setEnabled(false); pause.setEnabled(false); transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        status.setText("Pripravené. Nahrávky zostávajú v telefóne.");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    @Override protected void onResume(){
        super.onResume();
        boolean keep=getSharedPreferences("settings",0).getBoolean("keep_screen",false);
        if(keep && recording) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return; }
        Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_START); i.putExtra(RecordingService.EXTRA_TITLE,title.getText().toString()); startForegroundService(i);
        started=System.currentTimeMillis(); pausedTotal=0; pausedAt=0; recording=true; paused=false;
        if(getSharedPreferences("settings",0).getBoolean("keep_screen",false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        h.post(tick);
        record.setEnabled(false); pause.setEnabled(true); stop.setEnabled(true); archive.setEnabled(false); transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        status.setText("🔴 Nahrávanie prebieha. Môžeš ho pozastaviť.");
    }

    void togglePause(){
        if(!recording)return;
        if(!paused){
            Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_PAUSE); startService(i);
            paused=true; pausedAt=System.currentTimeMillis(); pause.setText("▶  POKRAČOVAŤ"); status.setText("⏸ Nahrávanie je pozastavené.");
        }else{
            Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_RESUME); startService(i);
            pausedTotal += System.currentTimeMillis()-pausedAt; pausedAt=0; paused=false; pause.setText("⏸  POZASTAVIŤ"); status.setText("🔴 Nahrávanie pokračuje.");
        }
    }

    void stopRec() {
        Intent i=new Intent(this,RecordingService.class); i.setAction(RecordingService.ACTION_STOP); startService(i);
        long now=System.currentTimeMillis(); if(paused){pausedTotal+=now-pausedAt;paused=false;}
        long duration=(now-started-pausedTotal)/1000; started=0; recording=false; h.removeCallbacks(tick);
        lastAudio=RecordingService.getLastRecording(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        record.setEnabled(true); pause.setEnabled(false); pause.setText("⏸  POZASTAVIŤ"); stop.setEnabled(false); archive.setEnabled(true); transcribe.setEnabled(true);
        status.setText("Nahrávanie ukončené ("+duration/60+" min). Teraz vytvor prepis.");
    }

    void startLocalTranscription() {
        if(lastAudio==null||!lastAudio.exists()) lastAudio=RecordingService.getLastRecording(this);
        if(lastAudio==null||!lastAudio.exists()){status.setText("Nenašla sa nahrávka.");return;}
        transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false); status.setText("Prepisujem lokálne. Pri dlhom zázname to môže trvať.");
        new Thread(()->{
            String fallback="[LOKÁLNY PREPIS – Whisper model ešte nie je pribalený]\n\nNahrávka: "+lastAudio.getName()+"\n\nĎalší krok: pridať lokálny Whisper model pre presný slovenský prepis.";
            runOnUiThread(()->{text.setLength(0);text.append(fallback);transcript.setText(text.toString());manual.setEnabled(true);status.setText("Prepis pripravený. Môžeš vytvoriť pracovný manuál.");});
        }).start();
    }

    void makeManual(){lastManual=ManualBuilder.build(title.getText().toString(),text.toString());transcript.setText(lastManual);export.setEnabled(true);status.setText("Manuál vytvorený lokálne. Skontroluj ho pred exportom.");}

    void doExport(){
        try{
            String source=lastManual.isEmpty()?text.toString():lastManual;
            File dir=new File(getExternalFilesDir(null),"exports"); File f=DocxExporter.export(dir,title.getText().toString(),source,lastAudio==null?"":lastAudio.getName());
            Intent s=new Intent(Intent.ACTION_SEND);s.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".provider",f);s.putExtra(Intent.EXTRA_STREAM,uri);s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(s,"Exportovať Word"));status.setText("Word dokument pripravený.");
        }catch(Exception e){status.setText("Export sa nepodaril: "+e.getMessage());}
    }
}
