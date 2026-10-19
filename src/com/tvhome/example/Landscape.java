package com.tvhome.example;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

// The background: layered mountain ridges under a night or morning sky. Drawn once into a bitmap
// (565, half the memory) so redraws while moving focus are a plain blit. Coordinates are designed at 1920x1080.
final class Landscape {
    private Landscape() {}

    private static final int[] NIGHT_SKY = {0xFF060913, 0xFF111A38, 0xFF2B2C55, 0xFF4B3B60};
    private static final int[] MORNING_SKY = {0xFFB9CFEE, 0xFFDDE2F3, 0xFFF6DFD8, 0xFFFBEBD9};
    private static final float[] SKY_AT = {0f, .45f, .75f, 1f};

    // base y, amplitude, frequency, colour, mist colour (0 for none)
    private static final long[][] NIGHT_RIDGES = {
            {600, 170, 16, 0xFF2D3563L, 0x337882C8L}, {700, 150, 21, 0xFF232B52L, 0x336E78BEL},
            {790, 120, 27, 0xFF1A2142L, 0x2E646EB4L}, {880, 90, 34, 0xFF121832L, 0x245A64AAL},
            {960, 60, 44, 0xFF0B0F22L, 0}};
    private static final long[][] MORNING_RIDGES = {
            {600, 170, 16, 0xFFB4C3DEL, 0x73FFF4ECL}, {700, 150, 21, 0xFF9FB1D1L, 0x6BFFF4ECL},
            {790, 120, 27, 0xFF8AA0C5L, 0x61FFF4ECL}, {880, 90, 34, 0xFF738CB4L, 0x4DFFF4ECL},
            {960, 60, 44, 0xFF5F7AA4L, 0}};

    static Bitmap render(boolean night, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        c.scale(w / 1920f, h / 1080f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setDither(true);

        p.setShader(new LinearGradient(0, 0, 0, 1080, night ? NIGHT_SKY : MORNING_SKY, SKY_AT, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 1920, 1080, p);
        p.setShader(null);

        if (night) {
            Random r = new Random(5);
            p.setColor(0xFFF3F1FF);
            for (int i = 0; i < 230; i++) {
                p.setAlpha((int) (255 * (.25f + r.nextFloat() * .7f)));
                c.drawCircle(r.nextFloat() * 1920, r.nextFloat() * 560, .5f + r.nextFloat() * 1.4f, p);
            }
            p.setAlpha(255);
            // The moon sits between the clock and the weather card, clear of both.
            p.setShader(new RadialGradient(1000, 170, 200, 0x80EBECFF, 0x00EBECFF, Shader.TileMode.CLAMP));
            c.drawRect(800, 0, 1200, 400, p);
            p.setShader(null);
            p.setColor(0xFFF4F1E6);
            c.drawCircle(1000, 170, 54, p);
        } else {
            p.setShader(new RadialGradient(430, 560, 620,
                    new int[] {0xF2FFF4E0, 0x8CFFEED6, 0x00FFEED6}, new float[] {0f, .25f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 1300, 1080, p);
            p.setShader(null);
            p.setColor(0xFFFFF6E4);
            c.drawCircle(430, 520, 62, p);
        }

        Noise n = new Noise(night ? 7 : 21);
        for (long[] l : night ? NIGHT_RIDGES : MORNING_RIDGES) ridge(c, n, l);
        return b;
    }

    private static void ridge(Canvas c, Noise n, long[] l) {
        float base = l[0], amp = l[1], freq = l[2] / 10000f;
        Path path = new Path();
        path.moveTo(0, 1080);
        for (int x = 0; x <= 1920; x += 6) {
            float y = base - amp * (n.at(x * freq) * .62f + n.at(x * freq * 2.3f + 40) * .27f + n.at(x * freq * 5.1f + 90) * .11f);
            path.lineTo(x, y);
        }
        path.lineTo(1920, 1080);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor((int) l[3]);
        c.drawPath(path, p);
        if (l[4] != 0) {
            int mist = (int) l[4], clear = mist & 0x00FFFFFF;
            float top = base - amp - 40;
            p.setShader(new LinearGradient(0, top, 0, base + 90, new int[] {clear, mist, clear}, new float[] {0f, .6f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, top, 1920, base + 90, p);
        }
    }

    // Smooth 1-D value noise.
    private static final class Noise {
        private final float[] v = new float[256];

        Noise(long seed) {
            Random r = new Random(seed);
            for (int i = 0; i < v.length; i++) v[i] = r.nextFloat();
        }

        float at(float x) {
            int i = (int) Math.floor(x);
            float f = x - i, s = f * f * (3 - 2 * f);
            float a = v[i & 255], b = v[(i + 1) & 255];
            return a + (b - a) * s;
        }
    }
}
