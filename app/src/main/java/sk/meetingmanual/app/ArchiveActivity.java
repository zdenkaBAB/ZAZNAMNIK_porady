package sk.meetingmanual.app;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ArchiveActivity extends Activity {
    LinearLayout list;
    TextView empty;
    MediaPlayer player;
    File playing;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_archive);
        list=findViewById(R.id.archiveList); empty=findViewById(R.id.archiveEmpty);
        findViewById(R.id.archiveBack).setOnClickListener(v->finish());
        load();
    }

    @Override protected void onResume(){super.onResume(); if(list!=null) load();}

    private void load(){
        list.removeAllViews();
        File dir=RecordingService.getMeetingsDir(this);
        File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if(files==null||files.length==0){empty.setVisibility(View.VISIBLE);return;}
        empty.setVisibility(View.GONE);
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files) addItem(f);
    }

    private void addItem(File f){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(16,16,16,16);
        TextView name=new TextView(this); name.setText(displayName(f)); name.setTextSize(17); name.setTypeface(null,1);
        TextView meta=new TextView(this); meta.setText(formatDate(f.lastModified())+"  •  "+formatSize(f.length())); meta.setTextSize(13);
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button play=new Button(this); play.setText("▶ Prehrať");
        Button share=new Button(this); share.setText("Zdieľať audio");
        Button del=new Button(this); del.setText("Vymazať");
        play.setOnClickListener(v->togglePlay(f,play));
        share.setOnClickListener(v->share(f));
        del.setOnClickListener(v->confirmDelete(f));
        buttons.addView(play,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(share,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(del,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(name); card.addView(meta); card.addView(buttons);
        list.addView(card,new LinearLayout.LayoutParams(-1,-2));
        View sep=new View(this); sep.setBackgroundColor(0xFFE0E0E0); list.addView(sep,new LinearLayout.LayoutParams(-1,1));
    }

    private String displayName(File f){return f.getName().replaceFirst("\\.wav$","").replace('_',' ');}
    private String formatDate(long t){return new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(t));}
    private String formatSize(long bytes){return String.format(Locale.getDefault(),"%.1f MB",bytes/1024d/1024d);}

    private void togglePlay(File f,Button b){
        try{
            if(player!=null){player.release();player=null; if(f.equals(playing)){playing=null;b.setText("▶ Prehrať");return;}}
            player=new MediaPlayer(); player.setDataSource(f.getAbsolutePath()); player.prepare(); player.start(); playing=f; b.setText("⏹ Zastaviť");
            player.setOnCompletionListener(mp->{b.setText("▶ Prehrať");mp.release();player=null;playing=null;});
        }catch(Exception e){Toast.makeText(this,"Prehrávanie sa nepodarilo.",Toast.LENGTH_SHORT).show();}
    }

    private void share(File f){
        Intent s=new Intent(Intent.ACTION_SEND); s.setType("audio/wav");
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".provider",f);
        s.putExtra(Intent.EXTRA_STREAM,uri); s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s,"Zdieľať nahrávku"));
    }

    private void confirmDelete(File f){
        new AlertDialog.Builder(this).setTitle("Vymazať nahrávku?").setMessage(displayName(f)).setNegativeButton("Zrušiť",null).setPositiveButton("Vymazať",(d,w)->{if(player!=null&&f.equals(playing)){player.release();player=null;playing=null;}f.delete();load();}).show();
    }

    @Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
}
