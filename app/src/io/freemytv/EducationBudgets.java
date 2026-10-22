package io.freemytv;

import org.json.JSONObject;

/** The overall allowance remains a ceiling; each approved source also has its own budget. */
final class EducationBudgets {
    static final String TOTAL = "total";
    static String game(String id) { return "game:" + id; }
    static long limit(JSONObject s,String key) {
        JSONObject limits=s.optJSONObject("limits");
        return Math.max(1,Math.min(720,limits==null?180:limits.optInt(key,180)))*60000L;
    }
    static long bonus(JSONObject s,String key) {
        JSONObject values=s.optJSONObject("bonusMinutes");
        return Math.max(1,Math.min(120,values==null?30:values.optInt(key,30)))*60000L;
    }
    static long ownRemaining(JSONObject s,String key) {
        if(TOTAL.equals(key))return Math.max(0,limit(s,key)+s.optLong("extraTotal",s.optInt("rewards")*EducationRules.BONUS_MS)-s.optLong("used"));
        JSONObject usage=s.optJSONObject("usage"),extra=s.optJSONObject("extra");
        return Math.max(0,limit(s,key)+(extra==null?0:extra.optLong(key))-(usage==null?0:usage.optLong(key)));
    }
    static long remaining(JSONObject s,String key) {
        return Math.min(ownRemaining(s,TOTAL),ownRemaining(s,key));
    }
    static String source(JSONObject s,String pkg,String own,long now) {
        if(pkg.isEmpty())return "";
        if(pkg.equals(s.optString("youtube")))return "youtube";
        if(pkg.equals(s.optString("abc")))return "abc";
        if(EducationInputs.active(s,pkg,own,now))return game(s.optString("gaming"));
        return "";
    }
    static void charge(JSONObject s,String key,long elapsed)throws Exception {
        if(key.isEmpty()||elapsed<=0)return;
        s.put("used",s.optLong("used")+elapsed);
        JSONObject usage=s.optJSONObject("usage");if(usage==null)usage=new JSONObject();
        usage.put(key,usage.optLong(key)+elapsed);s.put("usage",usage);
    }
    static void reward(JSONObject s,String key)throws Exception {
        if(ownRemaining(s,TOTAL)==0){s.put("extraTotal",s.optLong("extraTotal",s.optInt("rewards")*EducationRules.BONUS_MS)+bonus(s,TOTAL));s.put("rewards",s.optInt("rewards")+1);}
        if(!TOTAL.equals(key)&&ownRemaining(s,key)==0){
            JSONObject extra=s.optJSONObject("extra");if(extra==null)extra=new JSONObject();
            extra.put(key,extra.optLong(key)+bonus(s,key));s.put("extra",extra);
        }
    }
}
