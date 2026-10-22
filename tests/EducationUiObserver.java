package io.freemytv;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;
import java.io.StringWriter;

/** Separate self-targeting instrumentation: do not stop the app or suppress its accessibility guard. */
public class EducationUiObserver extends Instrumentation {
    private String click;
    @Override public void onCreate(Bundle b) {super.onCreate(b);click=b.getString("click");start();}
    private boolean clickNode(AccessibilityNodeInfo n){
        if(n==null)return false;
        if(click.equals(String.valueOf(n.getText())))return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for(int i=0;i<n.getChildCount();i++)if(clickNode(n.getChild(i)))return true;
        return false;
    }
    private void node(XmlSerializer xml,AccessibilityNodeInfo n)throws Exception {
        if(n==null)return;
        Rect r=new Rect();n.getBoundsInScreen(r);
        xml.startTag(null,"node");
        xml.attribute(null,"text",n.getText()==null?"":n.getText().toString());
        xml.attribute(null,"class",n.getClassName()==null?"":n.getClassName().toString());
        xml.attribute(null,"bounds","["+r.left+","+r.top+"]["+r.right+","+r.bottom+"]");
        for(int i=0;i<n.getChildCount();i++)node(xml,n.getChild(i));
        xml.endTag(null,"node");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            UiAutomation ui=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if(click!=null){if(!clickNode(ui.getRootInActiveWindow()))throw new IllegalStateException("Cannot click "+click);result.putString("stream","Clicked "+click);finish(-1,result);return;}
            StringWriter output=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(output);
            xml.startTag(null,"hierarchy");node(xml,ui.getRootInActiveWindow());xml.endTag(null,"hierarchy");xml.flush();
            result.putString("stream","UIXML:"+output.toString()+"\n");finish(-1,result);
        }catch(Exception e){result.putString("stream","FAIL: "+e);finish(0,result);}
    }
}
