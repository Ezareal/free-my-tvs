package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.os.Build;

/**
 * The frosted glass background: the aurora without detail (the same math as `aurora(uv, 0)` in Shaders) in a 48×27
 * image, computed on the CPU at every aurora step (~1,300 pixels, under 1 ms). The glass shader scales it up with
 * linear filtering, which by itself is the blur.
 */
final class Frost {
    static final int W = 48, H = 27;
    private final int[] pixels = new int[W * H];
    private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);

    /** Recomputes and returns a new shader (a BitmapShader keeps a copy of the bitmap as it was when created). */
    BitmapShader render(float t, float speed, float dim, float[] palette, float aspect) {
        float tt = t * 0.035f * speed;
        for (int j = 0; j < H; j++) {
            float v = (j + 0.5f) / H;
            for (int i = 0; i < W; i++) {
                float u = (i + 0.5f) / W;
                pixels[j * W + i] = color(u, v, tt, dim, palette, aspect);
            }
        }
        bitmap.setPixels(pixels, 0, W, 0, 0, W, H);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        return filter(shader, BitmapShader.FILTER_MODE_LINEAR);
    }

    /** BitmapShader.setFilterMode is Android 13+; older ones filter through the Paint. */
    static BitmapShader filter(BitmapShader s, int mode) {
        if (Build.VERSION.SDK_INT >= 33) s.setFilterMode(mode);
        return s;
    }

    private static int color(float u, float v, float tt, float dim, float[] c, float aspect) {
        // The same three drifting lights as `aurora` in Shaders (time scaled as the shader does: t * 0.05, not 0.035).
        float px = u * aspect, py = v, a = tt * (0.05f / 0.035f);
        float w1 = glow(px - aspect * (0.26f + 0.12f * (float) Math.sin(a)), py - (0.55f + 0.12f * (float) Math.cos(a * 0.8f)), 2.0f);
        float w2 = glow(px - aspect * (0.72f + 0.14f * (float) Math.cos(a * 0.7f + 1)), py - (0.45f + 0.14f * (float) Math.sin(a * 0.9f + 2)), 2.4f);
        float w3 = glow(px - aspect * (0.50f + 0.18f * (float) Math.sin(a * 0.5f + 4)), py - (0.32f + 0.10f * (float) Math.cos(a * 0.6f + 1)), 3.0f);
        float band = smoothstep(0.02f, 0.38f, v) * (1 - smoothstep(0.5f, 0.92f, v));
        float m = clamp01(w1 * 0.95f + w3 * 0.6f);
        float k = clamp01(w2 * 0.9f + w3 * 0.25f) * (0.35f + 0.65f * band) * 0.85f;
        float vig = 1 - 0.38f * smoothstep(0.35f, 1, v) - 0.2f * smoothstep(0.45f, 0.95f, Math.abs(u - 0.5f) * 1.6f);
        float scrim = 1 - 0.22f * (1 - smoothstep(0, 0.42f, v));
        float gain = vig * scrim * (1 - dim);
        int out = 0xFF000000;
        for (int ch = 0; ch < 3; ch++) {
            float col = c[ch] + (c[3 + ch] - c[ch]) * m;
            col = col + (c[6 + ch] - col) * k;
            int b = Math.round(Math.max(0, Math.min(1, col * gain)) * 255);
            out |= b << (16 - 8 * ch);
        }
        return out;
    }

    private static float glow(float dx, float dy, float k) {
        return (float) Math.exp(-(dx * dx + dy * dy) * k);
    }

    private static float clamp01(float x) {
        return Math.max(0, Math.min(1, x));
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
