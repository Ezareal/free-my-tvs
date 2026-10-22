package io.freemytv;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Explicit physical-TV test. Snapshots only budget/progress fields, never credentials. */
public class ConsoleCycleProbe extends Instrumentation {
    private String input, action;
    private UiAutomation ui;
    private final StringBuilder report=new StringBuilder();
    private static final String[] FIELDS={"limits","usage","extra","extraTotal","used","rewards","bonusMinutes","practiceDays","lastPractice","challengeKey"};
    @Override public void onCreate(Bundle args){super.onCreate(args);input=args.getString("input");action=args.getString("action","test");start();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private String text(AccessibilityNodeInfo n){
        if(n==null)return "";StringBuilder s=new StringBuilder();if(n.getText()!=null)s.append(n.getText()).append('\n');
        for(int i=0;i<n.getChildCount();i++)s.append(text(n.getChild(i)));return s.toString();
    }
    private String waitText(String part,int seconds)throws Exception{
        long end=SystemClock.elapsedRealtime()+seconds*1000;
        while(SystemClock.elapsedRealtime()<end){String s=text(ui.getRootInActiveWindow());if(s.contains(part))return s;Thread.sleep(250);}
        throw new AssertionError("Missing UI: "+part);
    }
    private boolean click(AccessibilityNodeInfo n,String part){
        if(n==null)return false;
        if(n.isClickable()&&n.getText()!=null&&n.getText().toString().contains(part))return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for(int i=0;i<n.getChildCount();i++)if(click(n.getChild(i),part))return true;return false;
    }
    private void click(String label)throws Exception{check(click(ui.getRootInActiveWindow(),label),"Cannot click "+label);Thread.sleep(350);}
    private void number(int value){for(char d:String.valueOf(value).toCharArray()){
        int code=KeyEvent.KEYCODE_0+d-'0';long t=SystemClock.uptimeMillis();
        ui.injectInputEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,code,0),true);
        ui.injectInputEvent(new KeyEvent(t,t,KeyEvent.ACTION_UP,code,0),true);
    }}
    private String shell(String command)throws Exception {
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))) {
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;
            while((n=in.read(b))>=0)out.write(b,0,n);return out.toString("UTF-8").trim();
        }
    }
    private void reconnectGuard()throws Exception {
        String services=shell("settings get secure enabled_accessibility_services");
        check(services.contains("EducationGuard"),"Guard must already be enabled");
        StringBuilder other=new StringBuilder();for(String service:services.split(":"))if(!service.startsWith("io.freemytv/")){
            if(other.length()>0)other.append(':');other.append(service);
        }
        shell(other.length()==0?"settings delete secure enabled_accessibility_services":"settings put secure enabled_accessibility_services "+other);
        Thread.sleep(500);shell("settings put secure enabled_accessibility_services "+services);
        long end=SystemClock.elapsedRealtime()+10000;
        while(!EducationStore.guardReady(EducationStore.read(getTargetContext()))&&SystemClock.elapsedRealtime()<end)Thread.sleep(250);
    }
    private File backup(){return new File(getTargetContext().getFilesDir(),"console-cycle-budget-backup.json");}
    private void restore()throws Exception{
        if(!backup().exists())return;
        JSONObject saved=new JSONObject(new String(Files.readAllBytes(backup().toPath()),StandardCharsets.UTF_8));
        JSONObject restored=EducationStore.access(getTargetContext(),s->{
            for(String key:FIELDS){s.remove(key);if(saved.has(key))s.put(key,saved.get(key));}
            s.remove("gaming");s.put("watching",false).put("watchingKey","");
        });
        check(!restored.optBoolean("error"),"Restore failed; backup retained");
        check(backup().delete(),"Could not remove restored backup");
        report.append("Restored original limits, usage, rewards and maths progress.\n");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();boolean passed=false;
        try{
            ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if(action.equals("restore")){restore();passed=true;}
            else {
                reconnectGuard();
                check(!backup().exists(),"Previous backup exists: run restore first");
                check(input!=null,"input required");
                EducationStore.open(getTargetContext());waitText("What would you like",10);Thread.sleep(1500);
                JSONObject state=EducationStore.read(getTargetContext());
                check(state.optBoolean("kids")&&EducationInputs.approved(state,input),"Existing approved Kids console required");
                check(EducationStore.guardReady(state),"Guard must be running");
                check(EducationStore.remaining(state)>120000,"Need at least two minutes overall allowance");
                JSONObject saved=new JSONObject();for(String key:FIELDS)if(state.has(key))saved.put(key,state.get(key));
                Files.write(backup().toPath(),saved.toString().getBytes(StandardCharsets.UTF_8));
                final String source=EducationBudgets.game(input);
                JSONObject changed=EducationStore.access(getTargetContext(),s->{
                    JSONObject limits=s.optJSONObject("limits");if(limits==null)limits=new JSONObject();limits.put(source,1);s.put("limits",limits);
                    JSONObject usage=s.optJSONObject("usage");if(usage==null)usage=new JSONObject();usage.put(source,50000);s.put("usage",usage);
                    JSONObject extra=s.optJSONObject("extra");if(extra!=null)extra.remove(source);
                    JSONObject bonus=s.optJSONObject("bonusMinutes");if(bonus==null)bonus=new JSONObject();bonus.put(source,1);s.put("bonusMinutes",bonus);s.remove("challengeKey");
                });
                check(!changed.optBoolean("error"),"Temporary budget failed");
                EducationStore.open(getTargetContext());waitText("What would you like",10);click("PlayStation");
                waitText("Time for a maths adventure!",25);
                state=EducationStore.read(getTargetContext());
                check(EducationBudgets.remaining(state,source)==0,"Console budget did not expire");
                check(!state.has("gaming"),"HDMI session not released");
                report.append("PASS: real console budget expired and HDMI released into maths.\n");
                for(int i=1;i<=5;i++){
                    String question=waitText("Question "+i+" of 5",10);
                    Matcher m=Pattern.compile("(\\d+) ([+−×]) (\\d+) =").matcher(question);
                    check(m.find(),"Maths equation missing");int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(3));
                    number(m.group(2).equals("+")?a+b:m.group(2).equals("−")?a-b:a*b);click("OK");
                }
                waitText("Extra time is ready",10);
                check(EducationBudgets.remaining(EducationStore.read(getTargetContext()),source)>55000,"Maths did not award console time");
                click("Back to my shows");waitText("What would you like",10);click("PlayStation");Thread.sleep(15000);
                state=EducationStore.read(getTargetContext());
                check(EducationInputs.active(state,"io.freemytv","io.freemytv",SystemClock.elapsedRealtime()),"Console did not stay active after reward");
                long left=EducationBudgets.remaining(state,source);check(left>0&&left<55000,"Resumed console timer did not decrease");
                report.append("PASS: five answers earned console time; console resumed and counted down for 15 seconds.\n");passed=true;
            }
        }catch(Throwable e){report.append("FAIL: ").append(e).append('\n');}
        finally{
            try{EducationStore.open(getTargetContext());Thread.sleep(1200);restore();EducationStore.open(getTargetContext());}
            catch(Throwable e){passed=false;report.append("RESTORE ERROR: ").append(e).append('\n');}
            result.putString("stream",report.toString());finish(passed?-1:0,result);
        }
    }
}
