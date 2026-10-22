package io.freemytv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import org.json.JSONObject;

/** Emulator-only harness, packaged separately; never included in FreeMyTV.apk. */
public class EducationInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments=args; start(); }
    private void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();
            String action=arguments.getString("action","test");
            if(action.equals("seed")) {
                EducationStore.savePin(c,"4826");
                EducationStore.access(c,s->{EducationStore.day(s);s.put("kids",true).put("used",Long.parseLong(arguments.getString("used","0")))
                    .put("rewards",0).put("extraTotal",0).put("youtube","com.google.android.youtube.tv").put("practiceDays",0).put("lastPractice","");
                    s.remove("extra");s.remove("challengeKey");s.remove("games");s.remove("gaming");s.remove("bonusMinutes");
                    s.put("limits",new JSONObject().put("youtube",Integer.parseInt(arguments.getString("limitMinutes","180"))));
                    s.put("usage",new JSONObject().put("youtube",Long.parseLong(arguments.getString("sourceUsed","0"))));});
                result.putString("stream","Seeded emulator family state\n");
            } else if(action.equals("inspect")) {
                JSONObject s=EducationStore.read(c);
                result.putString("stream","STATE kids="+s.optBoolean("kids")+" used="+s.optLong("used")+" rewards="+s.optInt("rewards")
                    +" practiceDays="+s.optInt("practiceDays")+" guard="+EducationStore.guardReady(s)
                    +" youtubeLeft="+EducationBudgets.remaining(s,"youtube")+" youtubeLimit="+EducationBudgets.limit(s,"youtube")+"\n");
            } else {
                JSONObject budget=new JSONObject().put("limits",new JSONObject().put("youtube",1).put("abc",10).put("total",30));
                EducationBudgets.charge(budget,"youtube",60000);
                check(EducationBudgets.remaining(budget,"youtube")==0,"per-app limit stops before total");
                check(EducationBudgets.remaining(budget,"abc")==600000,"other app retains separate allowance");
                EducationBudgets.reward(budget,"youtube");
                check(EducationBudgets.remaining(budget,"youtube")==1740000,"reward still respects overall remaining allowance");
                check(budget.optInt("rewards")==0,"source reward does not enlarge overall allowance");
                EducationBudgets.charge(budget,EducationBudgets.game("hdmi2"),1740000);
                check(EducationBudgets.remaining(budget,"abc")==0,"console use also exhausts total");
                EducationBudgets.reward(budget,EducationBudgets.TOTAL);
                check(EducationBudgets.remaining(budget,"abc")==600000,"overall reward preserves source cap");
                budget.put("day","2000-01-01");EducationStore.day(budget);
                check(EducationBudgets.remaining(budget,"youtube")==60000,"daily reset preserves custom limit");
                check(!budget.has("usage")&&!budget.has("extra"),"daily reset clears source counters and rewards");
                JSONObject game=new JSONObject().put("games",new JSONObject().put("hdmi2","PS5"))
                    .put("gaming","hdmi2").put("gamingHeartbeat",1000);
                check(EducationInputs.active(game,"io.freemytv","io.freemytv",2000),"approved gaming session counted");
                check(!EducationInputs.active(game,"io.freemytv","io.freemytv",3500),"stale gaming session not counted");
                check(!EducationInputs.active(game,"other","io.freemytv",2000),"background gaming not counted");
                game.put("kids",true);
                check(EducationInputs.statusOverlay(game,"io.freemytv","io.freemytv",2000,"com.tcl.tv","android.widget.RelativeLayout"),"HDMI status overlay preserves session");
                check(!EducationInputs.statusOverlay(game,"io.freemytv","io.freemytv",3500,"com.tcl.tv","android.widget.RelativeLayout"),"stale session cannot exempt overlay");
                check(!EducationInputs.statusOverlay(game,"io.freemytv","io.freemytv",2000,"com.tcl.tv","com.tcl.tv.TVActivity"),"TCL activity still guarded");
                check(!EducationInputs.statusOverlay(game,"other","io.freemytv",2000,"com.tcl.tv","android.widget.RelativeLayout"),"background session cannot exempt overlay");
                check(!EducationInputs.statusOverlay(game,"io.freemytv","io.freemytv",2000,"other","android.widget.RelativeLayout"),"other apps still guarded");
                check(EducationBudgets.source(game,"io.freemytv","io.freemytv",2000).equals("game:hdmi2"),"overlay session remains chargeable");
                game.put("gaming","hdmi3");
                check(!EducationInputs.statusOverlay(game,"io.freemytv","io.freemytv",2000,"com.tcl.tv","android.widget.RelativeLayout"),"unapproved input still guarded");
                check(!EducationInputs.active(game,"io.freemytv","io.freemytv",2000),"unapproved HDMI not counted");
                EducationStore.access(c,s->{s.put("day","2000-01-01").put("used",EducationRules.DAILY_MS).put("rewards",2);EducationStore.day(s);});
                JSONObject s=EducationStore.read(c);
                check(s.optLong("used")==0&&s.optInt("rewards")==0,"new day resets time and rewards");
                EducationStore.access(c,v->{v.put("day","9999-01-01").put("used",100);EducationStore.day(v);});
                check(EducationStore.read(c).optLong("used")==100,"backward clock does not grant time");
                check(EducationStore.savePin(c,"4826"),"PIN saved");
                check(EducationStore.checkPin(c,"4826"),"correct PIN accepted");
                for(int i=0;i<5;i++)check(!EducationStore.checkPin(c,"1111"),"wrong PIN rejected");
                check(!EducationStore.checkPin(c,"4826"),"lockout applies even to correct PIN");
                EducationStore.access(c,v->v.put("retry",0));
                check(EducationStore.checkPin(c,"4826"),"PIN works after lockout");
                check(!EducationStore.read(c).optString("pin").equals("4826"),"no plaintext PIN");
                EducationStore.access(c,v->{v.put("day","").put("kids",false).put("used",0).put("rewards",0);EducationStore.day(v);});
                result.putString("stream","PASS: gaming approval and heartbeat, daily reset, backward clock, hashed PIN, wrong PIN and retry lockout\n");
            }
            finish(ActivityResult.OK,result);
        } catch(Throwable e) { result.putString("stream","FAIL: "+e+"\n");finish(ActivityResult.FAIL,result); }
    }
    private static class ActivityResult {static final int OK=-1,FAIL=0;}
}
