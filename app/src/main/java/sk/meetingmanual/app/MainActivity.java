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
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends Activity {
    EditText title;
    TextView timer, status, transcript;
    Button record, pause, stop, archive, transcribe, manual, export, settings;
    Spinner sourceLanguage, outputLanguage;
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
        sourceLanguage=findViewById(R.id.sourceLanguage); outputLanguage=findViewById(R.id.outputLanguage);
        setupLanguageSelectors();
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

    void setupLanguageSelectors() {
        String[] sources={"Slovenčina", "English"};
        String[] outputs={"Slovenčina", "English", "Ponechať pôvodný jazyk"};
        ArrayAdapter<String> sa=new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sources);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sourceLanguage.setAdapter(sa);
        ArrayAdapter<String> oa=new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, outputs);
        oa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); outputLanguage.setAdapter(oa);
        sourceLanguage.setSelection(getSharedPreferences("settings",0).getInt("source_lang",0));
        outputLanguage.setSelection(getSharedPreferences("settings",0).getInt("output_lang",0));
        sourceLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p, View v,int pos,long id){getSharedPreferences("settings",0).edit().putInt("source_lang",pos).apply();} public void onNothingSelected(android.widget.AdapterView<?> p){}});
        outputLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p, View v,int pos,long id){getSharedPreferences("settings",0).edit().putInt("output_lang",pos).apply();} public void onNothingSelected(android.widget.AdapterView<?> p){}});
    }

    void startLocalTranscription() {
        if(lastAudio==null||!lastAudio.exists()) lastAudio=RecordingService.getLastRecording(this);
        if(lastAudio==null||!lastAudio.exists()){status.setText("Nenašla sa nahrávka.");return;}
        transcribe.setEnabled(false); manual.setEnabled(false); export.setEnabled(false);
        String sourceCode=sourceLanguage.getSelectedItemPosition()==1?"en":"sk";
        String sourceName=sourceCode.equals("en")?"English":"Slovenčina";
        int outputPos=outputLanguage.getSelectedItemPosition();
        String targetCode=outputPos==0?"sk":outputPos==1?"en":sourceCode;
        String targetName=outputPos==0?"Slovenčina":outputPos==1?"English":"Pôvodný jazyk";
        status.setText("Pripravujem lokálny Parakeet („"+sourceName+" → "+targetName+")…");
        ensureParakeetModelAndTranscribe(lastAudio, sourceCode, targetCode, targetName);
    }

    private void ensureParakeetModelAndTranscribe(File audio, String sourceCode, String targetCode, String targetName){
        if(ModelDownloader.isInstalled(this)){
            runParakeet(audio, sourceCode, targetCode, targetName);
            return;
        }
        status.setText("Prvýkrát sa sťahuje bezplatný Parakeet model (~640 MB). Audio sa pritom nikam neposiela.");
        ModelDownloader.download(this, new ModelDownloader.Callback(){
            public void onProgress(int percent,long downloaded,long total){
                if(percent>=0) status.setText("Sťahujem lokálny Parakeet model: "+percent+" %");
                else status.setText("Sťahujem lokálny Parakeet model… "+(downloaded/1024/1024)+" MB");
            }
            public void onSuccess(File file){ status.setText("Parakeet model je pripravený. Spúšťam lokálny prepis…"); runParakeet(audio,sourceCode,targetCode,targetName); }
            public void onError(String error){ transcribe.setEnabled(true); status.setText("Parakeet model sa nepodarilo stiahnuť: "+error); }
        });
    }

    private void runParakeet(File audio, String sourceCode, String targetCode, String targetName){
        SherpaTranscriber.transcribe(this, audio, ModelDownloader.modelDir(this), sourceCode, new SherpaTranscriber.Callback(){
            public void onStatus(String message){ status.setText(message); }
            public void onSuccess(String result){
                text.setLength(0); text.append(result); transcript.setText(result);
                if(!sourceCode.equals(targetCode)){
                    status.setText("Prepis hotový. Sťahujem/prebúdzam lokálny prekladový model…");
                    translateTranscriptOnDevice(sourceCode,targetCode,()->finishTranscription(targetName));
                } else finishTranscription(targetName);
            }
            public void onError(String error){ transcribe.setEnabled(true); status.setText("Parakeet prepis sa nepodaril: "+error); }
        });
    }

    private void finishTranscription(String targetName){
        manual.setEnabled(true); status.setText("Prepis dokončený lokálne. Výstup: "+targetName+". Skontroluj text a vytvor manuál.");
    }

    /** Translate a real Parakeet transcript on-device when source and target differ. */
    void translateTranscriptOnDevice(String sourceCode, String targetCode, Runnable onDone) {
        if (text.length() == 0 || sourceCode.equals(targetCode)) { onDone.run(); return; }
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode).setTargetLanguage(targetCode).build();
        Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(v -> translateChunks(translator, text.toString(), translated -> {
                    text.setLength(0); text.append(translated); transcript.setText(translated); translator.close(); onDone.run();
                }))
                .addOnFailureListener(e -> { translator.close(); status.setText("Prekladový model sa nepodarilo pripraviť: "+e.getMessage()); onDone.run(); });
    }

    void translateChunks(Translator translator, String input, java.util.function.Consumer<String> done) {
        ArrayList<String> chunks = new ArrayList<>();
        String[] paragraphs = input.split("\\n\\n"); StringBuilder cur = new StringBuilder();
        for (String para : paragraphs) {
            if (cur.length() + para.length() + 2 > 3500 && cur.length() > 0) { chunks.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append("\n\n"); cur.append(para);
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        if (chunks.isEmpty()) { done.accept(input); return; }
        StringBuilder out = new StringBuilder();
        translateChunkAt(translator, chunks, 0, out, done);
    }

    void translateChunkAt(Translator translator, ArrayList<String> chunks, int idx, StringBuilder out, java.util.function.Consumer<String> done) {
        translator.translate(chunks.get(idx)).addOnSuccessListener(r -> {
            if (out.length() > 0) out.append("\n\n"); out.append(r);
            if (idx + 1 < chunks.size()) translateChunkAt(translator, chunks, idx + 1, out, done); else done.accept(out.toString());
        }).addOnFailureListener(e -> { if (out.length() > 0) out.append("\n\n"); out.append(chunks.get(idx)); if (idx + 1 < chunks.size()) translateChunkAt(translator, chunks, idx + 1, out, done); else done.accept(out.toString()); });
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
