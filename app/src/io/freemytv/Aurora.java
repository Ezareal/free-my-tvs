package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * The screensaver's sky: a slow aurora whose colors follow the real day at your location (night, sunrise, day,
 * sunset, blended over an hour around each). Drawn on the CPU into a 160×90 bitmap and scaled up with filtering, so it
 * runs on every TV (the AGSL aurora in Shaders needs Android 13); the shapes are soft, so the low resolution doesn't
 * show. A step costs ~1 ms; the screensaver takes one every 50 ms.
 */
final class Aurora {
    static final int W = 160, H = 90;

    /** Sky top, sky bottom, then the three curtains. */
    private static final int[] NIGHT = {0xFF04060F, 0xFF0B1230, 0xFF1FD6A0, 0xFF7B5CFF, 0xFF1AA6C9};
    private static final int[] DAWN = {0xFF1A2350, 0xFFC9706E, 0xFFFF8FB1, 0xFFFFC46B, 0xFFB98CFF};
    private static final int[] DAY = {0xFF184C9C, 0xFF4F8FD6, 0xFF9FE8FF, 0xFFD9E8FF, 0xFF7FD3F0};
    private static final int[] DUSK = {0xFF22163F, 0xFFC4683A, 0xFFD9539B, 0xFFFF9E4A, 0xFF8A4DD6};
    private static final long HOUR = 3600_000L;

    private final Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[W * H];
    private final float[] center = new float[3 * W], width = new float[3 * W], glow = new float[3 * W];
    private final float[] colors = new float[15];
    private final int[] palette = new int[5];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect dst = new Rect();

    /** The colors for this moment of the day; sunrise and sunset in epoch ms (0 = unknown: 6:00 and 18:00). */
    void palette(long now, long sunrise, long sunset) {
        long t = Shaders.timeOfDay(now, 0);
        long rise = Shaders.timeOfDay(sunrise, 6 * HOUR), set = Shaders.timeOfDay(sunset, 18 * HOUR);
        int[] a, b;
        float f;
        if (t < rise - HOUR || t >= set + HOUR) { a = b = NIGHT; f = 0; }
        else if (t < rise) { a = NIGHT; b = DAWN; f = 1 - (rise - t) / (float) HOUR; }
        else if (t < rise + HOUR) { a = DAWN; b = DAY; f = (t - rise) / (float) HOUR; }
        else if (t < set - HOUR) { a = b = DAY; f = 0; }
        else if (t < set) { a = DAY; b = DUSK; f = 1 - (set - t) / (float) HOUR; }
        else { a = DUSK; b = NIGHT; f = (t - set) / (float) HOUR; }
        for (int i = 0; i < 5; i++) {
            palette[i] = mix(a[i], b[i], f);
            colors[i * 3] = ((palette[i] >> 16) & 255) / 255f;
            colors[i * 3 + 1] = ((palette[i] >> 8) & 255) / 255f;
            colors[i * 3 + 2] = (palette[i] & 255) / 255f;
        }
    }

    /** The sky's brightest tone (0 to 1): the screensaver darkens its text panels a little more by day. */
    float brightness() {
        float max = 0;
        for (int i = 0; i < 15; i += 3) max = Math.max(max, 0.2126f * colors[i] + 0.7152f * colors[i + 1] + 0.0722f * colors[i + 2]);
        return max;
    }

    /** Draws the sky at `seconds` (a slow clock: the curtains drift and sway over minutes) into the bitmap. */
    void step(float seconds) {
        // Each curtain: a wavy band across the sky, its height, thickness and brightness varying along it.
        for (int band = 0; band < 3; band++) {
            float speed = 0.021f + 0.007f * band, phase = band * 2.1f;
            float base = H * (0.26f + 0.17f * band);
            for (int x = 0; x < W; x++) {
                float u = x / (float) W;
                int i = band * W + x;
                center[i] = base + H * 0.11f * (float) Math.sin(u * (3.1f + band) + seconds * speed + phase)
                        + H * 0.04f * (float) Math.sin(u * 9.3f - seconds * speed * 1.7f + phase);
                width[i] = H * (0.07f + 0.035f * (float) Math.sin(u * 5.7f + seconds * speed * 0.8f + band));
                float shimmer = 0.5f + 0.5f * (float) Math.sin(u * 17f + seconds * 0.09f + phase * 3);
                glow[i] = (0.42f + 0.3f * shimmer) * (0.55f + 0.45f * (float) Math.sin(u * 2.3f + seconds * 0.013f + band));
            }
        }
        float tr = colors[0], tg = colors[1], tb = colors[2], br = colors[3], bg = colors[4], bb = colors[5];
        for (int y = 0; y < H; y++) {
            float v = y / (float) (H - 1);
            float sr = tr + (br - tr) * v, sg = tg + (bg - tg) * v, sb = tb + (bb - tb) * v;
            for (int x = 0; x < W; x++) {
                float r = sr, g = sg, b = sb;
                for (int band = 0; band < 3; band++) {
                    int i = band * W + x;
                    float d = (y - center[i]) / width[i];
                    // Curtains fade more slowly upward than downward, like the real ones.
                    float e = d < 0 ? d * 0.55f : d;
                    float a = glow[i] / (1 + e * e * e * e);
                    if (a < 0.004f) continue;
                    int c = 6 + band * 3;
                    r += (colors[c] - r) * a * 0.6f;
                    g += (colors[c + 1] - g) * a * 0.6f;
                    b += (colors[c + 2] - b) * a * 0.6f;
                }
                pixels[y * W + x] = 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
            }
        }
        bitmap.setPixels(pixels, 0, W, 0, 0, W, H);
    }

    void draw(Canvas c, int w, int h) {
        dst.set(0, 0, w, h);
        c.drawBitmap(bitmap, null, dst, paint);
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
