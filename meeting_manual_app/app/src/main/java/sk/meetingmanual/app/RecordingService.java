package sk.meetingmanual.app;

import android.app.*;
import android.content.*;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;

/** Long-running PCM/WAV recorder. Designed to continue while the screen is locked. */
public class RecordingService extends Service {
    public static final String ACTION_START="START";
    public static final String ACTION_PAUSE="PAUSE";
    public static final String ACTION_RESUME="RESUME";
    public static final String ACTION_STOP="STOP";
    public static final String EXTRA_TITLE="TITLE";
    private static final int NOTIFICATION_ID=1001;
    private static final String CHANNEL_ID="recording";
    private static final int SAMPLE_RATE=16000;
    private AudioRecord recorder;
    private File out;
    private FileOutputStream output;
    private volatile boolean recording=false;
    private volatile boolean paused=false;
    private Thread writerThread;
    private long audioBytes=0;

    public static File getLastRecording(Context c){
        String p=c.getSharedPreferences("meeting",0).getString("last_audio",null);
        return p==null?null:new File(p);
    }
    public static File getMeetingsDir(Context c){
        File dir=new File(c.getExternalFilesDir(null),"meetings"); dir.mkdirs(); return dir;
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
        if(recording) return;
        try{
            createNotificationChannel();
            File dir=getMeetingsDir(this);
            String safeTitle=(title==null||title.trim().isEmpty())?"stretnutie":title.trim().replaceAll("[^\\p{L}\\p{N}._-]","_");
            String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",java.util.Locale.US).format(new java.util.Date());
            out=new File(dir,safeTitle+"_"+stamp+".wav");
            int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            int buffer=Math.max(min*2, SAMPLE_RATE/2);
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
            output=new FileOutputStream(out);
            writeWavHeader(output,0);
            audioBytes=0;
            startForeground(NOTIFICATION_ID,buildNotification("Nahrávanie stretnutia prebieha"));
            recorder.startRecording();
            recording=true; paused=false;
            getSharedPreferences("meeting",0).edit().putString("last_audio",out.getAbsolutePath()).putString("last_title",title==null?"":title).apply();
            writerThread=new Thread(()->captureLoop(buffer),"MeetingAudioRecorder"); writerThread.start();
        }catch(Exception e){ releaseRecorder(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }

    private void captureLoop(int bufferSize){
        byte[] data=new byte[bufferSize];
        try{
            while(recording){
                int n=recorder.read(data,0,data.length);
                if(n>0 && !paused){ output.write(data,0,n); audioBytes+=n; }
            }
        }catch(Exception ignored){}
    }

    private void pauseRecording(){ if(recording){ paused=true; updateNotification(); } }
    private void resumeRecording(){ if(recording){ paused=false; updateNotification(); } }

    private void stopRecording(){
        recording=false;
        try{ if(writerThread!=null) writerThread.join(1500); }catch(Exception ignored){}
        try{ if(recorder!=null){ recorder.stop(); recorder.release(); } }catch(Exception ignored){}
        recorder=null;
        try{ if(output!=null){ output.flush(); output.close(); patchWavHeader(out,audioBytes); } }catch(Exception ignored){}
        output=null; writerThread=null;
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    private void releaseRecorder(){
        recording=false;
        try{ if(recorder!=null){recorder.release();} }catch(Exception ignored){}
        recorder=null;
        try{if(output!=null)output.close();}catch(Exception ignored){}
        output=null;
    }

    private static void writeWavHeader(OutputStream out,long dataLength)throws IOException{
        byte[] h=new byte[44];
        System.arraycopy(new byte[]{'R','I','F','F'},0,h,0,4); writeLE(h,4,36+dataLength,4);
        System.arraycopy(new byte[]{'W','A','V','E','f','m','t',' '},0,h,8,8); writeLE(h,16,16,4); writeLE(h,20,1,2); writeLE(h,22,1,2);
        writeLE(h,24,SAMPLE_RATE,4); writeLE(h,28,SAMPLE_RATE*2,4); writeLE(h,32,2,2); writeLE(h,34,16,2);
        System.arraycopy(new byte[]{'d','a','t','a'},0,h,36,4); writeLE(h,40,dataLength,4); out.write(h);
    }
    private static void patchWavHeader(File f,long dataLength)throws IOException{
        try(RandomAccessFile raf=new RandomAccessFile(f,"rw")){ raf.seek(4); raf.write(intBytesLE((int)(36+dataLength))); raf.seek(40); raf.write(intBytesLE((int)dataLength)); }
    }
    private static void writeLE(byte[] b,int off,long v,int n){for(int i=0;i<n;i++)b[off+i]=(byte)((v>>(8*i))&255);}
    private static byte[] intBytesLE(int v){return new byte[]{(byte)v,(byte)(v>>8),(byte)(v>>16),(byte)(v>>24)};}

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){ NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Nahrávanie",NotificationManager.IMPORTANCE_LOW); ch.setDescription("Stav nahrávania Meeting Manual"); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch); }
    }
    private PendingIntent actionIntent(String action){ Intent i=new Intent(this,RecordingService.class); i.setAction(action); return PendingIntent.getService(this,action.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    private PendingIntent openAppIntent(){ Intent i=new Intent(this,MainActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP); return PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    private Notification buildNotification(String text){
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Meeting Manual").setContentText(text).setContentIntent(openAppIntent()).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_LOW);
        if(paused)b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_play,"Pokračovať",actionIntent(ACTION_RESUME)).build());
        else b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause,"Pozastaviť",actionIntent(ACTION_PAUSE)).build());
        b.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Ukončiť",actionIntent(ACTION_STOP)).build());
        return b.build();
    }
    private void updateNotification(){ NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(paused?"Nahrávanie je pozastavené":"Nahrávanie stretnutia prebieha")); }
    @Override public IBinder onBind(Intent i){return null;}
}
