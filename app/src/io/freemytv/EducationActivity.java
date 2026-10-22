package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-first family home: all navigation also works with touch and accessibility focus. */
public class EducationActivity extends Activity {
    private static final int INK = 0xff1c1c1e, MINT = 0xfff5f5f7, PEACH = 0xfff5f5f7, BLUE = 0xfff5f5f7;
    private final Handler handler = new Handler();
    private final ExecutorService crypto = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private LinearLayout body;
    private TextView time, input, feedback;
    private String page = "", digits = "", firstPin = "";
    private boolean authenticated, pinBusy, changePin, challenge;
    private int correct, difficulty;
    private int pinRequest;
    private EducationRules.Problem problem;
    private String challengeKey=EducationBudgets.TOTAL;
    private String editingBudget=EducationBudgets.TOTAL;
    private boolean editingBonus;
    private boolean dark;
    private EducationSky educationSky;
    private LinearLayout content;
    private android.media.tv.TvInputManager tvInputs;
    private final android.media.tv.TvInputManager.TvInputCallback inputChanges = new android.media.tv.TvInputManager.TvInputCallback() {
        private void refresh(){if(page.equals("kids"))kids();else if(page.equals("games")&&authenticated)games();}
        @Override public void onInputStateChanged(String id,int state){refresh();}
        @Override public void onInputAdded(String id){refresh();}
        @Override public void onInputRemoved(String id){refresh();}
    };
    @Override protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(Ui.locale());
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (page.equals("kids")) {
                JSONObject s = EducationStore.access(EducationActivity.this, EducationStore::day);
                if (time != null) time.setText(remainingText(s));
                if (EducationStore.remaining(s) == 0) maths(true);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        tvInputs=(android.media.tv.TvInputManager)getSystemService(TV_INPUT_SERVICE);
        enter(getIntent());
    }
    @Override protected void onResume() {
        super.onResume(); handler.post(ticker);
        if(tvInputs!=null)tvInputs.registerCallback(inputChanges,handler);
        // A parent session never survives leaving the activity.
        if ((page.equals("parent") || page.equals("games") || page.equals("limit")) && !authenticated) pin(false);
        else if(page.equals("kids"))kids();
    }
    @Override protected void onPause() {
        authenticated = false; firstPin = "";
        pinRequest++;
        pinBusy = false;
        handler.removeCallbacks(ticker); super.onPause();
        if(tvInputs!=null)tvInputs.unregisterCallback(inputChanges);
    }
    @Override protected void onDestroy() { crypto.shutdown(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i); setIntent(i); authenticated = false; firstPin = "";
        enter(i);
    }
    private boolean ready(JSONObject s) {
        return s.has("pin") && EducationStore.guardReady(s) && (!s.optString("youtube").isEmpty()
                || !s.optString("abc").isEmpty() || EducationInputs.hasApproved(s));
    }
    private void enter(Intent intent) {
        JSONObject s=EducationStore.read(this);
        if(EducationStore.kids(this)){
            String key=s.optString("challengeKey");
            if(!key.isEmpty()&&EducationBudgets.remaining(s,key)==0){maths(true);return;}
            kids();return;
        }
        if(intent.getBooleanExtra("start_kids",false)){startKids();return;}
        welcome();
    }
    private void startKids() {
        if(!ready(EducationStore.read(this))){pin(false);return;}
        EducationStore.access(this,s->{EducationStore.day(s);s.put("kids",true);});
        authenticated=false;kids();
    }
    private String t(int id) { return getString(id); }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int colour) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size>=34?28:size>=28?24:size>=20?18:size>=16?14:12); v.setTextColor(dark?colour:INK);
        v.setTypeface(size >= 28 ? Ui.displaySemibold : Ui.regular);
        v.setPadding(0, dp(3), 0, dp(3)); return v;
    }
    private GradientDrawable background(int colour, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(colour); d.setCornerRadius(dp(20));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private void screen(String name, String title, String subtitle) {
        page = name; digits = ""; time = null; input = null; feedback = null;
        dark=HomeData.get(this).isNight(System.currentTimeMillis());
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(40), dp(18), dp(40), dp(14));
        boolean fixed=name.equals("maths")||name.equals("pin")||name.equals("setup")||name.equals("confirm")||name.equals("limit");
        if(!fixed)scroll.addView(body);
        TextView brand = text("Free My TV  /  Education", 14, 0x99ffffff); body.addView(brand);
        TextView heading = text(title, 34, Color.WHITE); body.addView(heading);
        TextView description=text(subtitle,17,0xb3ffffff);
        if(!subtitle.isEmpty())body.addView(description);
        FrameLayout layers = new FrameLayout(this);
        educationSky=new EducationSky();
        layers.addView(educationSky,new FrameLayout.LayoutParams(-1,-1));
        if(name.equals("parent")||name.equals("games")){
            body.removeView(brand);body.removeView(heading);body.removeView(description);
            body.setPadding(dp(12),dp(20),dp(8),dp(16));
            LinearLayout columns=new LinearLayout(this);columns.setGravity(Gravity.CENTER_VERTICAL);columns.setPadding(dp(40),dp(24),dp(40),dp(24));
            LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setPadding(0,0,dp(32),0);
            intro.addView(brand);
            View emblem=new View(this){@Override protected void onDraw(Canvas c){Symbols.draw(EducationActivity.this,c,name.equals("games")?"gamepad":"sparkles",dp(38),dp(44),dp(56),dark?Color.WHITE:INK);}};
            intro.addView(emblem,new LinearLayout.LayoutParams(dp(88),dp(100)));intro.addView(heading);intro.addView(description);
            columns.addView(intro,new LinearLayout.LayoutParams(0,-2,.42f));columns.addView(scroll,new LinearLayout.LayoutParams(0,-1,.58f));
            layers.addView(columns,new FrameLayout.LayoutParams(-1,-1));
        }else layers.addView(fixed?body:scroll,new FrameLayout.LayoutParams(-1,-1));
        setContentView(layers);
        content=body;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2)); return r;
    }
    private Button button(LinearLayout parent, String label, int colour, int height, Runnable click) {
        boolean tile=height>=140;
        height=tile?100:height==46?36:Math.min(height,48);
        Button b = new Button(this); b.setText(label); b.setTextSize(tile?20:15);
        b.setTypeface(Ui.medium); b.setFocusable(true); b.setFocusableInTouchMode(true);
        // A TV button is focusable in touch mode too; a tap should activate it, not only move focus.
        b.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){v.requestFocus();v.setPressed(true);return true;}
            if(event.getAction()==android.view.MotionEvent.ACTION_UP){v.setPressed(false);v.performClick();return true;}
            if(event.getAction()==android.view.MotionEvent.ACTION_CANCEL){v.setPressed(false);return true;}
            return true;
        });
        b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{INK,INK,dark?0xf2ffffff:INK}));
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(dp(12), dp(4), dp(12), dp(4));
        if(!label.matches("[0-9]+|⌫")&&!label.equals(t(R.string.edu_ok))){
            String symbol=label.contains("YouTube")||label.contains("ABC")?"play":label.equals(t(R.string.edu_games))||label.contains("PlayStation")||label.contains("Xbox")||label.contains("Nintendo")||label.contains("HDMI")?"gamepad":
                label.equals(t(R.string.edu_manage))?"apps":label.equals(t(R.string.edu_start_kids))||label.equals(t(R.string.edu_practice))?"sparkles":
                label.equals(t(R.string.edu_adult))||label.equals(t(R.string.edu_home_return))?"home":"settings";
            android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable(){
                @Override public void draw(Canvas c){android.graphics.Rect r=getBounds();Symbols.draw(EducationActivity.this,c,symbol,r.centerX(),r.centerY(),dp(22),b.isFocused()||b.isPressed()?INK:dark?Color.WHITE:INK);}
                @Override public void setAlpha(int a){} @Override public void setColorFilter(android.graphics.ColorFilter f){}
                @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
            };
            icon.setBounds(0,0,dp(26),dp(26));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(10));
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, background(0xfff5f5f7, 0xffffffff));
        states.addState(new int[]{android.R.attr.state_pressed}, background(0xfff5f5f7, 0xffffffff));
        states.addState(new int[]{}, background(dark?0x19ffffff:0x55ffffff, dark?0x22ffffff:0x55999999)); b.setBackground(states);
        b.setElevation(dp(3));
        b.setOnFocusChangeListener((v,focused)->v.animate().scaleX(focused?1.015f:1f).scaleY(focused?1.015f:1f).setDuration(140).start());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(parent.getOrientation() == LinearLayout.HORIZONTAL ? 0 : -1, dp(height));
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) p.weight = 1;
        p.setMargins(dp(4), dp(4), dp(4), dp(4)); parent.addView(b, p);
        b.setOnClickListener(v -> click.run()); return b;
    }
    private void focusFirst() {
        body.post(() -> { View first = body.focusSearch(View.FOCUS_DOWN); if (first != null) first.requestFocus(); });
    }
    /** Shares the home palette; only the visible, focused window animates. */
    private final class EducationSky extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint();
        private android.graphics.LinearGradient gradient;
        private final AmbientBackground ambient=new AmbientBackground(
            page.equals("kids")||page.equals("maths")||page.equals("success")||page.equals("welcome"));
        private final Runnable frame=()->{ambient.prepare();invalidate();};
        EducationSky() { super(EducationActivity.this); }
        @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            gradient=new android.graphics.LinearGradient(0,0,0,h,dark?0xff1c1c1e:0xfff5f5f7,dark?0xff0b0b0d:0xffe2e2e7,android.graphics.Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas) {
            paint.setShader(gradient);canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
            ambient.draw(canvas,getWidth(),getHeight(),dark?1:0);
            if(hasWindowFocus()&&getWindowVisibility()==VISIBLE){removeCallbacks(frame);postDelayed(frame,AmbientBackground.FRAME_MS);}
        }
        @Override public void onWindowFocusChanged(boolean focus) {super.onWindowFocusChanged(focus);removeCallbacks(frame);if(focus){ambient.prepare();invalidate();}}
    }
    private void welcome() {
        boolean configured=ready(EducationStore.read(this));
        screen("welcome", t(R.string.edu_kids_mode), ruleText());
        button(body, t(configured ? R.string.edu_start_kids : R.string.edu_next), MINT, 80, this::startKids).requestFocus();
        if(EducationStore.read(this).has("pin"))button(body,t(R.string.edu_parent_settings),BLUE,56,()->pin(false));
        button(body, t(R.string.edu_home_return), BLUE, 56, this::adultHome);
    }
    private void kids() {
        JSONObject s = EducationStore.access(this, EducationStore::day);
        if (EducationStore.remaining(s) == 0) { EducationStore.access(this,v->v.put("challengeKey",EducationBudgets.TOTAL));maths(true); return; }
        screen("kids", t(R.string.edu_watch), "");
        time = text(remainingText(s), 22, MINT); body.addView(time);
        LinearLayout apps = row(body);
        Button first = null;
        if (!s.optString("youtube").isEmpty()) first = button(apps, "YouTube Kids\n"+sourceTime(s,"youtube"), PEACH, 150, () -> launch("youtube"));
        if (!s.optString("abc").isEmpty()) {
            Button abc = button(apps, "ABC Kids\n"+sourceTime(s,"abc"), BLUE, 150, () -> launch("abc"));
            if (first == null) first = abc;
        }
        if (first != null) first.requestFocus();
        for(EducationInputs.Input device:EducationInputs.list(this)) {
            if(!EducationInputs.approved(s,device.id))continue;
            Button game=button(body,device.label+" · "+sourceTime(s,EducationBudgets.game(device.id))+(device.connected?"":" · "+t(R.string.edu_game_off)),BLUE,64,()->playGame(device.id,false));
            // Some TVs report standby until tuned; keep an approved port selectable to wake/connect it.
            game.setAlpha(device.connected?1f:.65f);
            if(first==null){first=game;first.requestFocus();}
        }
        if(first==null&&!EducationInputs.hasApproved(s))body.addView(text(t(R.string.edu_ask_parent), 20, Color.WHITE));
        LinearLayout actions = row(body);
        button(actions, t(R.string.edu_practice), MINT, 64, () -> maths(false));
        button(actions, t(R.string.edu_parent), 0xffd4dce5, 64, () -> pin(false));
    }
    private String ruleText(){return getString(R.string.edu_rule_limits,EducationBudgets.limit(EducationStore.read(this),EducationBudgets.TOTAL)/60000);}
    private String remainingText(JSONObject s) {
        long minutes = (EducationStore.remaining(s) + 59999) / 60000;
        return getString(R.string.edu_time, minutes / 60, minutes % 60);
    }
    private String sourceTime(JSONObject s,String key){return getString(R.string.edu_minutes_left,(EducationBudgets.remaining(s,key)+59999)/60000);}
    private void launch(String key) {
        JSONObject s = EducationStore.read(this);
        if (!EducationStore.guardReady(s)) { message(t(R.string.edu_guard_missing)); return; }
        if (EducationBudgets.remaining(s,key) == 0) { EducationStore.access(this,v->v.put("challengeKey",key));maths(true); return; }
        String pkg = s.optString(key);
        Intent launch = launchIntent(pkg);
        if (launch == null) { message(t(R.string.edu_ask_parent)); return; }
        try { startActivity(launch); } catch (Exception e) { message(t(R.string.edu_ask_parent)); }
    }
    private Intent launchIntent(String pkg) {
        if (pkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : getPackageManager().getLaunchIntentForPackage(pkg);
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private void pin(boolean changing) {
        changePin = changing; firstPin = ""; pinBusy = false;
        JSONObject s = EducationStore.read(this);
        if (s.optBoolean("error")) {
            screen("error", t(R.string.edu_parent), t(R.string.edu_storage_error)); return;
        }
        pinScreen(!s.has("pin") || changing, false);
    }
    private void pinScreen(boolean setup, boolean confirm) {
        screen(setup ? (confirm ? "confirm" : "setup") : "pin", t(R.string.edu_parent),
                t(setup ? (confirm ? R.string.edu_confirm_pin : R.string.edu_create_pin) : R.string.edu_enter_pin));
        input = text("○ ○ ○ ○", 28, MINT); input.setGravity(Gravity.CENTER); body.addView(input);
        feedback = text(" ", 16, PEACH); feedback.setGravity(Gravity.CENTER); body.addView(feedback);
        keypad(() -> submitPin(setup, confirm));
    }
    private void keypad(Runnable submit) {
        LinearLayout pad = new LinearLayout(this); pad.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(280), -2); p.gravity = Gravity.CENTER_HORIZONTAL; content.addView(pad, p);
        for (int y=0; y<3; y++) {
            LinearLayout r = row(pad);
            for (int x=1; x<=3; x++) { final String digit = ""+(y*3+x); Button b=button(r,digit,0xffe9f0f5,46,()->digit(digit)); if(y==0&&x==1)b.requestFocus(); }
        }
        LinearLayout last = row(pad);
        button(last,"⌫",PEACH,46,()->{ if(!pinBusy && !digits.isEmpty()){digits=digits.substring(0,digits.length()-1); updateInput();} });
        button(last,"0",0xffe9f0f5,46,()->digit("0"));
        button(last,t(R.string.edu_ok),MINT,46,submit);
    }
    private void digit(String value) {
        if (pinBusy || digits.length() >= (page.equals("maths")||page.equals("limit") ? 3 : 4)) return;
        digits += value; updateInput();
    }
    private void updateInput() {
        if (input == null) return;
        if (page.equals("maths")||page.equals("limit")) input.setText(digits.isEmpty() ? "?" : digits);
        else { StringBuilder b=new StringBuilder(); for(int i=0;i<4;i++)b.append(i<digits.length()?"● ":"○ ");input.setText(b.toString()); }
    }
    private void submitPin(boolean setup, boolean confirm) {
        if(pinBusy) return;
        if(setup && EducationStore.read(this).has("pin") && !authenticated) {pin(false);return;}
        if(digits.length()!=4){feedback.setText(t(R.string.edu_four_digits));return;}
        if(setup && !confirm){ firstPin=digits; pinScreen(true,true); return; }
        if(setup && !digits.equals(firstPin)){ firstPin="";pinScreen(true,false);feedback.setText(t(R.string.edu_mismatch));return; }
        final String value=digits; final int request=++pinRequest;
        pinBusy=true; feedback.setText(t(R.string.edu_checking));
        crypto.execute(()->{
            boolean ok=false;
            try { ok=setup?EducationStore.savePin(this,value):EducationStore.checkPin(this,value); } catch(Exception ignored){}
            final boolean success=ok;
            handler.post(()->{
                if(isFinishing()||isDestroyed()||request!=pinRequest)return;
                pinBusy=false; digits=""; firstPin="";
                if(success){authenticated=true;parent();}
                else {updateInput();feedback.setText(t(R.string.edu_wrong_pin));}
            });
        });
    }
    private void parent() {
        if(!authenticated){pin(false);return;}
        if(!ready(EducationStore.read(this))){configure();return;}
        screen("parent",t(R.string.edu_parent_settings),ruleText());
        LinearLayout modes=row(body);
        button(modes,t(R.string.edu_start_kids),MINT,80,this::startKids).requestFocus();
        button(modes,t(R.string.edu_adult),BLUE,80,()->{EducationStore.access(this,s->s.put("kids",false));authenticated=false;adultHome();});
        button(body,t(R.string.edu_manage),BLUE,60,this::configure);
        button(body,t(R.string.edu_limits_title),BLUE,56,this::limits);
        button(body,t(R.string.edu_more),BLUE,56,this::moreSettings);
    }
    private void configure() {
        if(!authenticated){pin(false);return;}
        JSONObject s=EducationStore.read(this);
        boolean controls = EducationStore.guardReady(s);
        boolean appsReady = !s.optString("youtube").isEmpty() || !s.optString("abc").isEmpty() || EducationInputs.hasApproved(s);
        boolean ready = controls && appsReady;
        screen("parent",t(R.string.edu_manage),t(ready ? R.string.edu_ready_hint : R.string.edu_setup_hint));
        if (controls) body.addView(text("✓  " + t(R.string.edu_controls_ready), 18, Color.WHITE));
        else {
            button(body,t(R.string.edu_controls_needed),MINT,56,this::setupControls).requestFocus();
        }
        body.addView(text(t(R.string.edu_apps_step),18,Color.WHITE));
        LinearLayout apps=row(body);
        button(apps,"YouTube Kids · " + t(s.optString("youtube").isEmpty()?R.string.edu_set_up:R.string.edu_ready),PEACH,60,()->approve("youtube"));
        button(apps,"ABC Kids · " + t(s.optString("abc").isEmpty()?R.string.edu_set_up:R.string.edu_ready),BLUE,60,()->approve("abc"));
        button(body,t(R.string.edu_games),BLUE,48,this::games);
        body.addView(text(ruleText(),16,0xb3ffffff));
        LinearLayout modes=row(body);
        Button start = button(modes,t(R.string.edu_start_kids),MINT,64,()->{
            JSONObject now=EducationStore.read(this);
            if(!EducationStore.guardReady(now)){setupControls();return;}
            if(now.optString("youtube").isEmpty()&&now.optString("abc").isEmpty()&&!EducationInputs.hasApproved(now)){approve("youtube");return;}
            EducationStore.access(this,v->{EducationStore.day(v);v.put("kids",true);}); authenticated=false;kids();
        });
        start.setEnabled(ready); start.setAlpha(ready ? 1f : .35f);
        if (ready) start.requestFocus();
        else if (controls) apps.getChildAt(0).requestFocus();
        button(modes,t(R.string.edu_adult),BLUE,64,()->{EducationStore.access(this,v->v.put("kids",false));authenticated=false;adultHome();});
        button(body,t(R.string.edu_more),BLUE,48,this::moreSettings);
    }
    private void setupControls() {
        if(EducationStore.kids(this)){message(t(R.string.edu_adult_first));return;}
        new AlertDialog.Builder(this).setTitle(R.string.edu_controls_needed)
            .setMessage(R.string.edu_controls_help).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.edu_ok,(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private void moreSettings() {
        if(!authenticated){pin(false);return;}
        screen("parent",t(R.string.edu_more),getString(R.string.edu_progress,EducationStore.read(this).optInt("practiceDays")));
        LinearLayout settings=row(body);
        button(settings,t(R.string.edu_change_pin),0xffe9f0f5,56,()->pin(true));
        button(settings,t(R.string.edu_reset_maths),0xffe9f0f5,56,()->new AlertDialog.Builder(this)
                .setMessage(t(R.string.edu_reset_confirm)).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.edu_ok,(d,w)->{EducationStore.access(this,v->v.put("practiceDays",0));parent();}).show());
        body.addView(text(t(R.string.edu_limits),14,0xffc4d8e1));
        button(body,t(R.string.edu_back_parent),MINT,56,this::parent).requestFocus();
    }
    private void games() {
        if(!authenticated){pin(false);return;}
        screen("games",t(R.string.edu_games),t(R.string.edu_games_help));
        JSONObject s=EducationStore.read(this);
        java.util.List<EducationInputs.Input> devices=EducationInputs.list(this);
        if(devices.isEmpty())body.addView(text(t(R.string.edu_games_empty),20,Color.WHITE));
        for(EducationInputs.Input device:devices){
            boolean enabled=EducationInputs.approved(s,device.id);
            button(body,device.label+" · "+t(enabled?R.string.edu_ready:R.string.edu_set_up),BLUE,56,()->{
                new AlertDialog.Builder(this).setTitle(device.label).setMessage(R.string.edu_game_approval)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setNeutralButton(R.string.edu_game_test,(d,w)->playGame(device.id,true))
                    .setPositiveButton(enabled?R.string.edu_remove:R.string.edu_game_allow,(d,w)->{
                        EducationStore.access(this,v->{JSONObject approved=v.optJSONObject("games");if(approved==null)approved=new JSONObject();
                            if(enabled)approved.remove(device.id);else approved.put(device.id,device.label);v.put("games",approved);});games();
                    }).show();
            });
        }
        button(body,t(R.string.edu_back_parent),MINT,56,this::parent).requestFocus();
    }
    private void playGame(String id,boolean test){
        if(test&&!authenticated){pin(false);return;}
        if(!test&&!EducationStore.guardReady(EducationStore.read(this))){message(t(R.string.edu_guard_missing));return;}
        String key=EducationBudgets.game(id);
        if(!test&&EducationBudgets.remaining(EducationStore.read(this),key)==0){EducationStore.access(this,s->s.put("challengeKey",key));maths(true);return;}
        startActivity(new Intent(this,EducationGameActivity.class).putExtra("input",id).putExtra("test",test));
    }
    private String budgetLabel(String key){
        if(key.equals(EducationBudgets.TOTAL))return t(R.string.edu_total_limit);
        if(key.equals("youtube"))return "YouTube Kids";
        if(key.equals("abc"))return "ABC Kids";
        JSONObject games=EducationStore.read(this).optJSONObject("games");
        return games==null?"HDMI":games.optString(key.substring(5),"HDMI");
    }
    private void limits(){
        if(!authenticated){pin(false);return;}
        screen("parent",t(R.string.edu_limits_title),t(R.string.edu_limits_help));
        java.util.List<String> keys=new java.util.ArrayList<>();keys.add(EducationBudgets.TOTAL);keys.add("youtube");keys.add("abc");
        JSONObject s=EducationStore.read(this),games=s.optJSONObject("games");
        if(games!=null){java.util.Iterator<String> ids=games.keys();while(ids.hasNext())keys.add(EducationBudgets.game(ids.next()));}
        for(String key:keys)button(body,budgetLabel(key)+" · "+getString(R.string.edu_daily_minutes,EducationBudgets.limit(s,key)/60000),BLUE,48,()->{
            new AlertDialog.Builder(this).setTitle(budgetLabel(key))
                .setItems(new String[]{t(R.string.edu_daily_limit),t(R.string.edu_extra_minutes)},(d,which)->editLimit(key,which==1)).show();
        });
        button(body,t(R.string.edu_back_parent),MINT,48,this::parent).requestFocus();
    }
    private void editLimit(String key,boolean bonus){
        if(!authenticated){pin(false);return;}
        editingBudget=key;editingBonus=bonus;
        screen("limit",budgetLabel(key),t(bonus?R.string.edu_extra_minutes:R.string.edu_daily_limit));
        input=text("?",28,MINT);input.setGravity(Gravity.CENTER);body.addView(input);
        feedback=text(getString(R.string.edu_enter_minutes,bonus?120:720),16,Color.WHITE);feedback.setGravity(Gravity.CENTER);body.addView(feedback);
        keypad(()->{
            if(!authenticated){pin(false);return;}
            int value=digits.isEmpty()?0:Integer.parseInt(digits);
            if(value<1||value>(editingBonus?120:720)){feedback.setText(getString(R.string.edu_enter_minutes,editingBonus?120:720));return;}
            EducationStore.access(this,s->{String field=editingBonus?"bonusMinutes":"limits";JSONObject values=s.optJSONObject(field);if(values==null)values=new JSONObject();values.put(editingBudget,value);s.put(field,values);});limits();
        });
    }
    private void approve(String key) {
        String[] candidates=key.equals("youtube")?new String[]{"com.google.android.youtube.tv","com.google.android.youtube.tvkids","com.google.android.apps.youtube.kids"}
                :new String[]{"au.net.abc.iview","au.net.abc.kids.iview"};
        String found="";
        for(String candidate:candidates)if(launchIntent(candidate)!=null){found=candidate;break;}
        if(found.isEmpty()){message(t(R.string.edu_missing_app));return;}
        final String pkg=found;
        new AlertDialog.Builder(this).setTitle(key.equals("youtube")?"YouTube Kids":"ABC Kids")
                .setMessage(t(key.equals("youtube")?R.string.edu_youtube_setup:R.string.edu_abc_setup))
                .setNegativeButton(android.R.string.cancel,null)
                .setNeutralButton(R.string.edu_remove,(d,w)->{EducationStore.access(this,v->v.remove(key));parent();})
                .setPositiveButton(R.string.edu_confirm_profile,(d,w)->{EducationStore.access(this,v->v.put(key,pkg));parent();}).show();
    }
    private void adultHome() {
        if(EducationStore.kids(this)){pin(false);return;}
        startActivity(new Intent(this,HomeActivity.class)); finish();
    }
    private void maths(boolean required) {
        challenge=required;correct=0;
        JSONObject s=EducationStore.read(this);
        challengeKey=s.optString("challengeKey",EducationBudgets.TOTAL);
        difficulty=Math.max(0,s.optInt("practiceDays")-(EducationStore.today().equals(s.optString("lastPractice"))?1:0));
        nextProblem();
    }
    private void nextProblem() {
        EducationRules.Problem old=problem;
        do {problem=EducationRules.problem(random,difficulty);}while(old!=null&&problem.text().equals(old.text()));
        screen("maths",challenge?t(R.string.edu_math_time):t(R.string.edu_practice),getString(R.string.edu_question,correct+1,EducationRules.QUESTIONS));
        LinearLayout columns=row(body);
        LinearLayout question=new LinearLayout(this);question.setOrientation(LinearLayout.VERTICAL);question.setGravity(Gravity.CENTER);
        columns.addView(question,new LinearLayout.LayoutParams(0,dp(220),1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        columns.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        // Keep the answer keypad reachable in a single row/column traversal.
        input=text("?",28,MINT);input.setGravity(Gravity.CENTER);
        TextView equation=text(problem.text(),38,Color.WHITE);equation.setGravity(Gravity.CENTER);question.addView(equation);question.addView(input);
        feedback=text(t(R.string.edu_math_keypad_hint),16,PEACH);feedback.setGravity(Gravity.CENTER);question.addView(feedback);
        keypad(this::answer);
    }
    private void answer() {
        if(digits.isEmpty())return;
        if(Integer.parseInt(digits)!=problem.answer){digits="";updateInput();feedback.setText(t(R.string.edu_try_again));return;}
        correct++;
        if(correct<EducationRules.QUESTIONS){nextProblem();return;}
        EducationStore.access(this,s->{
            EducationStore.day(s);
            if(challenge&&EducationBudgets.remaining(s,challengeKey)==0)EducationBudgets.reward(s,challengeKey);
            // All five answers must be right; practice contributes at most one small step each day.
            if(!EducationStore.today().equals(s.optString("lastPractice")))
                s.put("practiceDays",s.optInt("practiceDays")+1).put("lastPractice",EducationStore.today());
        });
        screen("success",t(R.string.edu_well_done),t(challenge?R.string.edu_time_earned:R.string.edu_practised));
        button(body,t(R.string.edu_continue),MINT,80,this::kids).requestFocus();
        button(body,t(R.string.edu_parent),BLUE,64,()->pin(false));
    }
    @Override public void onBackPressed() {
        if(pinBusy)return;
        authenticated=false;firstPin="";
        if(EducationStore.kids(this)) {
            if(page.equals("maths")&&challenge&&EducationStore.remaining(EducationStore.read(this))==0) pin(false); else kids();
        } else if(page.equals("welcome")) finish(); else welcome();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(educationSky!=null&&e.getAction()==KeyEvent.ACTION_DOWN)educationSky.ambient.key(e.getKeyCode());
        if(input!=null&&e.getKeyCode()>=KeyEvent.KEYCODE_0&&e.getKeyCode()<=KeyEvent.KEYCODE_9){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)digit(""+(e.getKeyCode()-KeyEvent.KEYCODE_0));return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
