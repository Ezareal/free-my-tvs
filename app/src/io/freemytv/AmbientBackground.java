package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.BitmapShader;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Gradient shaders baked once into 128px half-float textures: 384 KiB total, no per-frame shader math. */
final class AmbientBackground {
    static final long FRAME_MS = 100; // 10 fps; independent of foreground focus animation.
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] washes = new Bitmap[3];
    private final RectF bounds = new RectF(-1,-1,1,1);
    private final boolean kids;
    private static final Shader GRAIN = grain();
    // Static, native-pixel dither breaks 8-bit display bands without animated noise or blur.
    private static Shader grain() {
        int size=128;int[] pixels=new int[size*size];java.util.Random random=new java.util.Random(731);
        for(int i=0;i<pixels.length;i++) pixels[i]=random.nextBoolean()?0x03ffffff:0x03000000;
        Bitmap image=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
        return new BitmapShader(image,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
    }
    private float x, y, targetX, targetY;
    private int palette = -1;
    private long paletteAt;
    AmbientBackground(boolean kids) { this.kids = kids; prepare(); }
    void key(int code) {
        if(code==KeyEvent.KEYCODE_DPAD_LEFT) targetX=-1;
        if(code==KeyEvent.KEYCODE_DPAD_RIGHT) targetX=1;
        if(code==KeyEvent.KEYCODE_DPAD_UP) targetY=-1;
        if(code==KeyEvent.KEYCODE_DPAD_DOWN) targetY=1;
    }
    void prepare() {
        long now=SystemClock.uptimeMillis();
        if(now>=paletteAt) {
            java.util.Calendar clock=java.util.Calendar.getInstance();
            int hour=clock.get(java.util.Calendar.HOUR_OF_DAY);
            int next=(hour>=6&&hour<11)?0:(hour>=17&&hour<21)?2:1;
            if(next!=palette) {
                palette=next;
                int[] colours=kids?new int[]{0xff39cba7,0xffa890ee,0xfff4bd76}
                    :new int[]{0xff41aab8,0xff758fce,next==0?0xffdfbd96:next==2?0xffbe8baf:0xff70b8a7};
                for(int i=0;i<3;i++) {
                    if(washes[i]==null) washes[i]=Bitmap.createBitmap(128,128,Bitmap.Config.RGBA_F16);
                    washes[i].eraseColor(0);
                    Canvas bake=new Canvas(washes[i]);
                    paint.setAlpha(255);
                    paint.setShader(new RadialGradient(64,64,64,
                        new int[]{colours[i],colours[i]&0x00ffffff},null,Shader.TileMode.CLAMP));
                    bake.drawRect(0,0,128,128,paint);
                    paint.setShader(null);
                }
            }
            paletteAt=now+60000;
        }
    }
    void draw(Canvas c, float w, float h, float night) {
        long now=SystemClock.uptimeMillis();
        float t=(now%3600000)/1000f;
        x+=(targetX-x)*.075f; y+=(targetY-y)*.075f;
        targetX*=.985f;targetY*=.985f;
        paint.setAlpha((int)(kids?72+night*6:48+night*9));
        for(int i=0;i<3;i++) {
            float px=(.12f+i*.38f+(float)Math.sin(t*.035f+i*2)*.065f+x*.012f)*w;
            float py=(.48f+(float)Math.cos(t*.028f+i*2.6)*.24f+y*.012f)*h;
            c.save();c.translate(px,py);
            c.scale(w*(kids?.30f:.47f),h*(kids?.48f:.70f));
            c.drawBitmap(washes[i],null,bounds,paint);c.restore();
        }
        paint.setAlpha(255);paint.setShader(GRAIN);
        c.drawRect(0,0,w,h,paint);
        paint.setShader(null);
    }
}
