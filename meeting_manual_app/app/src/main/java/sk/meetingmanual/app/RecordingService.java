package sk.meetingmanual.app;

import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;

public class RecordingService extends Service {
    public static final String ACTION_START="START";
    public static final String ACTION_PAUSE="PAUSE";
    public static final String ACTION_RESUME="RESUME";
    public static final String ACTION_STOP="STOP";
    public static final String EXTRA_TITLE="TITLE";
    private static final int NOTIFICATION_ID=1001;
    private static final String CHANNEL_ID="recording";
    private MediaRecorder recorder;
    private File out;
    private boolean paused=false;

    public static File getLastRecording(Context c){
        String p=c.getSharedPreferences("meeting",0).getString("last_audio",null);
        return p==null?null:new File(p);
    }
    public static File getMeetingsDir(Context c){
        File dir=new File(c.getExternalFilesDir(null),"meetings");
        dir.mkdirs();
        return dir;
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null){
            String action=i.getAction();
            if(ACTION_START.equals(action)) startRecording(i.getStringExtra(EXTRA_TITLE));
            else if(ACTION_PAUSE.equals(action)) pauseRecording();
            else if(ACTION_RESUME.equals(action)) resumeRecording();
            else if(ACTION_STOP.equals(action)) stopRecording();
        }
        return START_STICKY;
    }
    private void startRecording(String title){
        if(recorder!=null) return;
        try{
            createNotificationChannel();
            File dir=getMeetingsDir(this);
            String safeTitle=(title==null||title.trim().isEmpty())?"stretnutie":title.trim().replaceAll("[^\\p{L}\\p{N}._-]","_");
            String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",java.util.Locale.US).format(new java.util.Date());
            out=new File(dir,safeTitle+"_"+stamp+".m4a");
            recorder=new MediaRecorder(this);
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(out.getAbsolutePath());
            recorder.prepare();
            startForeground(NOTIFICATION_ID,buildNotification("Nahrávanie stretnutia prebieha",false));
            recorder.start();
            paused=false;
            getSharedPreferences("meeting",0).edit().putString("last_audio",out.getAbsolutePath()).putString("last_title",title==null?"":title).apply();
        }catch(Exception e){
            releaseRecorder();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    private void pauseRecording(){
        try{ if(recorder!=null && !paused && Build.VERSION.SDK_INT>=24){ recorder.pause(); paused=true; updateNotification(); } }catch(Exception ignored){}
    }
    private void resumeRecording(){
        try{ if(recorder!=null && paused && Build.VERSION.SDK_INT>=24){ recorder.resume(); paused=false; updateNotification(); } }catch(Exception ignored){}
    }
    private void stopRecording(){
        try{ if(recorder!=null){ recorder.stop(); recorder.release(); recorder=null; } }catch(Exception ignored){ releaseRecorder(); }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void releaseRecorder(){ try{ if(recorder!=null){recorder.reset();recorder.release();} }catch(Exception ignored){} recorder=null; }
    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Nahrávanie",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Stav nahrávania Meeting Manual");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }
    private PendingIntent actionIntent(String action){
        Intent i=new Intent(this,RecordingService.class); i.setAction(action);
        return PendingIntent.getService(this,action.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private PendingIntent openAppIntent(){
        Intent i=new Intent(this,MainActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification buildNotification(String text,boolean ignored){
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Meeting Manual")
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        if(paused) b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_play,"Pokračovať",actionIntent(ACTION_RESUME)).build());
        else b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause,"Pozastaviť",actionIntent(ACTION_PAUSE)).build());
        b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Ukončiť",actionIntent(ACTION_STOP)).build());
        return b.build();
    }
    private void updateNotification(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null) nm.notify(NOTIFICATION_ID,buildNotification(paused?"Nahrávanie je pozastavené":"Nahrávanie stretnutia prebieha",false));
    }
    @Override public IBinder onBind(Intent i){return null;}
}
