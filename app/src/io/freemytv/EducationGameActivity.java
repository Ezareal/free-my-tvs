package io.freemytv;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Owns the HDMI session, so expiry can release video/audio before returning to maths. */
public class EducationGameActivity extends Activity {
    private final Handler handler = new Handler();
    private TvView tv;
    private TextView status, countdown;
    private String inputId;
    private boolean active, testing, closing;
    private long deadline;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now = SystemClock.elapsedRealtime();
            JSONObject state = EducationStore.access(EducationGameActivity.this, s -> {
                EducationStore.day(s);
                if (!testing) s.put("gaming",inputId).put("gamingHeartbeat",now);
            });
            if ((testing && now >= deadline) || (!testing && (!state.optBoolean("kids")
                    || !EducationInputs.approved(state,inputId) || !EducationStore.guardReady(state)
                    || EducationBudgets.remaining(state,EducationBudgets.game(inputId)) == 0))) {
                if(!testing&&EducationBudgets.remaining(state,EducationBudgets.game(inputId))==0)
                    EducationStore.access(EducationGameActivity.this,s->s.put("challengeKey",EducationBudgets.game(inputId)));
                leave(); return;
            }
            long seconds = testing ? (deadline-now+999)/1000 : (EducationBudgets.remaining(state,EducationBudgets.game(inputId))+999)/1000;
            countdown.setText(getString(testing?R.string.edu_game_test_time:R.string.edu_game_time,seconds/60,seconds%60));
            handler.postDelayed(this,500);
        }
    };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        inputId=getIntent().getStringExtra("input");
        // This activity is non-exported. Only the authenticated parent UI requests a test.
        testing=getIntent().getBooleanExtra("test",false);
        JSONObject s=EducationStore.read(this);
        if (inputId==null || (!testing && (!s.optBoolean("kids") || !EducationInputs.approved(s,inputId)
                || !EducationStore.guardReady(s) || EducationBudgets.remaining(s,EducationBudgets.game(inputId))==0))) { leave(); return; }
        boolean exists=false;
        for(EducationInputs.Input item:EducationInputs.list(this)) if(item.id.equals(inputId))exists=true;
        if(!exists){leave();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout layout=new FrameLayout(this);layout.setBackgroundColor(Color.BLACK);
        tv=new TvView(this);layout.addView(tv,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(this);status.setText(R.string.edu_game_connecting);status.setTextColor(Color.WHITE);status.setTextSize(24);status.setGravity(Gravity.CENTER);
        layout.addView(status,new FrameLayout.LayoutParams(-1,-1));
        countdown=new TextView(this);countdown.setTextColor(Color.WHITE);countdown.setTextSize(16);countdown.setPadding(20,10,20,10);countdown.setBackgroundColor(0xb3000000);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);layout.addView(countdown,p);
        setContentView(layout);
        tv.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String id){status.setVisibility(View.GONE);android.util.Log.i("FreeMyTVGame","HDMI video available");}
            @Override public void onVideoUnavailable(String id,int reason){status.setVisibility(View.VISIBLE);status.setText(R.string.edu_game_no_signal);}
            @Override public void onConnectionFailed(String id){status.setVisibility(View.VISIBLE);status.setText(R.string.edu_game_failed);android.util.Log.w("FreeMyTVGame","HDMI session rejected");}
            @Override public void onDisconnected(String id){leave();}
        });
    }
    @Override protected void onResume(){
        super.onResume();if(tv==null || closing)return;
        active=true;deadline=SystemClock.elapsedRealtime()+10000;
        tick.run();if(closing)return;
        try{tv.tune(inputId,TvContract.buildChannelUriForPassthroughInput(inputId));tv.setStreamVolume(1f);}
        catch(RuntimeException e){status.setText(R.string.edu_game_failed);android.util.Log.w("FreeMyTVGame","HDMI tune failed",e);}
    }
    private void release(){
        active=false;handler.removeCallbacks(tick);
        EducationStore.access(this,s->s.remove("gaming"));
        if(tv!=null){tv.setStreamVolume(0f);tv.reset();}
    }
    private void leave(){
        if(closing)return;closing=true;release();
        android.util.Log.i("FreeMyTVGame","HDMI released; returning to Education");
        EducationStore.open(this);finish();
    }
    @Override protected void onPause(){closing=true;release();super.onPause();finish();}
    @Override public void onBackPressed(){leave();}
}
