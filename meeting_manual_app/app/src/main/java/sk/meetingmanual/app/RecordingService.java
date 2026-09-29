package sk.meetingmanual.app;

import android.app.*;import android.content.*;import android.media.MediaRecorder;import android.os.*;import java.io.*;

public class RecordingService extends Service {
 public static final String ACTION_START="START", ACTION_STOP="STOP"; private MediaRecorder recorder; private File out;
 public static File getLastRecording(Context c){String p=c.getSharedPreferences("meeting",0).getString("last_audio",null);return p==null?null:new File(p);}
 @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&ACTION_START.equals(i.getAction()))startRecording();else if(i!=null&&ACTION_STOP.equals(i.getAction()))stopRecording();return START_NOT_STICKY;}
 private void startRecording(){try{
  File dir=new File(getExternalFilesDir(null),"meetings");dir.mkdirs();out=new File(dir,"meeting_"+System.currentTimeMillis()+".m4a");
  recorder=new MediaRecorder(this);recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioEncodingBitRate(128000);recorder.setAudioSamplingRate(44100);recorder.setOutputFile(out.getAbsolutePath());recorder.prepare();recorder.start();
  getSharedPreferences("meeting",0).edit().putString("last_audio",out.getAbsolutePath()).apply();
  NotificationChannel ch=new NotificationChannel("recording","Nahrávanie",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
  Notification n=new Notification.Builder(this,"recording").setContentTitle("Meeting Manual").setContentText("Nahrávanie stretnutia prebieha").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();startForeground(1001,n);
 }catch(Exception e){stopSelf();}}
 private void stopRecording(){try{if(recorder!=null){recorder.stop();recorder.release();recorder=null;}}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 @Override public IBinder onBind(Intent i){return null;}
}
