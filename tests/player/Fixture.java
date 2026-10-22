package com.google.android.youtube.tv;
public class Fixture extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.widget.TextView label=new android.widget.TextView(this);
        label.setText("Education test fixture — not YouTube\nSimulates an approved foreground app for timer tests.");
        label.setTextSize(30);label.setGravity(android.view.Gravity.CENTER);setContentView(label);
    }
}
