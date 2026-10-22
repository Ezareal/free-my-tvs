package io.freemytv;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

final class EducationInputs {
    static final class Input {
        String id, label;
        boolean connected;
    }
    static List<Input> list(Context c) {
        List<Input> result = new ArrayList<>();
        TvInputManager manager = (TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return result;
        try {
            List<TvInputInfo> inputs = manager.getTvInputList();
            for (TvInputInfo info : inputs) {
                if (info.getType() != TvInputInfo.TYPE_HDMI || info.getParentId() != null) continue;
                Input item = new Input(); item.id = info.getId();
                String port = String.valueOf(info.loadLabel(c));
                CharSequence custom = info.loadCustomLabel(c);
                item.label = custom != null && custom.length() > 0 ? custom.toString() : port;
                for (TvInputInfo child : inputs) {
                    if (item.id.equals(child.getParentId())) {
                        item.label = String.valueOf(child.loadLabel(c)) + " · " + port;
                        break;
                    }
                }
                item.connected = manager.getInputState(item.id) == TvInputManager.INPUT_STATE_CONNECTED;
                result.add(item);
            }
        } catch (RuntimeException ignored) {}
        result.sort((a,b)->a.id.compareTo(b.id));
        return result;
    }
    static boolean approved(JSONObject state, String id) {
        JSONObject games = state.optJSONObject("games");
        return id != null && !id.isEmpty() && games != null && games.has(id);
    }
    static boolean hasApproved(JSONObject state) {
        JSONObject games = state.optJSONObject("games");
        return games != null && games.length() > 0;
    }
    // V620 emits this non-activity window when HDMI HDR/ALLM becomes available.
    // Do not whitelist the TCL TV app itself: its activities still require the guard.
    static boolean statusOverlay(JSONObject state,String foreground,String ownPackage,long now,String pkg,String cls) {
        return "com.tcl.tv".equals(pkg) && "android.widget.RelativeLayout".equals(cls)
            && state.optBoolean("kids") && active(state,foreground,ownPackage,now);
    }
    static boolean active(JSONObject state,String foreground,String ownPackage,long now) {
        long age=now-state.optLong("gamingHeartbeat",-10000);
        return foreground.equals(ownPackage) && approved(state,state.optString("gaming")) && age>=0 && age<2500;
    }
}
