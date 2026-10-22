package io.freemytv;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Intent;

/** Explicit 10-second HDMI diagnostic. Does not set a PIN, approve ports or change the allowance. */
public class HdmiProbe extends Instrumentation {
    private String input;
    @Override public void onCreate(Bundle args){super.onCreate(args);input=args.getString("input");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(input==null)throw new IllegalArgumentException("input is required");
            Thread.sleep(3000);
            getTargetContext().startActivity(new Intent(getTargetContext(),EducationGameActivity.class)
                .putExtra("input",input).putExtra("test",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(13000);
            result.putString("stream","HDMI diagnostic completed; check FreeMyTVGame logs for video and return.\n");
            finish(-1,result);
        }catch(Exception e){result.putString("stream","FAIL: "+e);finish(0,result);}
    }
}
