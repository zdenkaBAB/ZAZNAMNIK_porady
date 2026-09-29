package sk.meetingmanual.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;

public class SettingsActivity extends Activity {
    private TextView batteryStatus, whisperStatus;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        android.content.SharedPreferences p=getSharedPreferences("settings",0);
        CheckBox keep=findViewById(R.id.keepScreen);
        keep.setChecked(p.getBoolean("keep_screen",false));
        keep.setOnCheckedChangeListener((button,checked)->p.edit().putBoolean("keep_screen",checked).apply());
        findViewById(R.id.settingsBack).setOnClickListener(v->finish());
        findViewById(R.id.batteryOptimization).setOnClickListener(v->requestBatteryUnrestricted());
        batteryStatus=findViewById(R.id.batteryStatus);
        whisperStatus=findViewById(R.id.whisperStatus);
        findViewById(R.id.whisperDownload).setOnClickListener(v -> downloadWhisper());
        updateBatteryStatus();
        updateWhisperStatus();
        ((TextView)findViewById(R.id.storageInfo)).setText("Nahrávky sa ukladajú lokálne do priečinka aplikácie.\n\nAplikácia neposiela audio automaticky na internet.\n\nPre niekoľkohodinové nahrávanie odporúčame povoliť pre aplikáciu neobmedzené používanie batérie.");
    }
    private boolean isIgnoringBattery(){
        if(Build.VERSION.SDK_INT<23) return true;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        return pm!=null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }
    private void updateBatteryStatus(){
        batteryStatus.setText(isIgnoringBattery()?"✅ Batéria: bez obmedzení – nahrávanie na pozadí má povolenie.":"⚠️ Batéria: môže byť optimalizovaná – pri dlhom nahrávaní odporúčame povoliť bez obmedzení.");
    }
    private void updateWhisperStatus(){
        if(whisperStatus!=null) whisperStatus.setText(ModelDownloader.isInstalled(this) ? "✅ Whisper Small Q5 model je pripravený. Prepis prebieha lokálne." : "⚠️ Model ešte nie je v telefóne. Pri prvom stlačení sa stiahne približne 182 MB.");
    }
    private void downloadWhisper(){
        if(ModelDownloader.isInstalled(this)){ Toast.makeText(this,"Whisper model už je pripravený.",Toast.LENGTH_SHORT).show(); return; }
        whisperStatus.setText("Sťahujem Whisper Small Q5 model…");
        ModelDownloader.download(this,new ModelDownloader.Callback(){
            public void onProgress(int p,long d,long t){ whisperStatus.setText(p>=0?"Sťahujem Whisper Small Q5 model: "+p+" %":"Sťahujem Whisper Small Q5 model…"); }
            public void onSuccess(java.io.File f){ updateWhisperStatus(); Toast.makeText(SettingsActivity.this,"Whisper Small Q5 model je pripravený.",Toast.LENGTH_LONG).show(); }
            public void onError(String e){ whisperStatus.setText("Sťahovanie zlyhalo: "+e); }
        });
    }
    private void requestBatteryUnrestricted(){
        try{
            if(Build.VERSION.SDK_INT>=23){
                Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:"+getPackageName()));
                startActivity(i);
            }
        }catch(Exception e){
            try{ startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }catch(Exception ignored){}
        }
    }
    @Override protected void onResume(){ super.onResume(); if(batteryStatus!=null) updateBatteryStatus();
        if(whisperStatus!=null) updateWhisperStatus(); }
}
