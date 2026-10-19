package com.tvhome.example;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

// Tiny white outline icons on a 24x24 grid, so the app needs no image resources.
// Each icon is a list of ops: 0=moveTo x y, 1=lineTo x y, 2=close, 3=circle cx cy r.
final class LineIcon extends Drawable {
    static final int[] PLAY = {0, 60, 45, 1, 60, 195, 1, 190, 120, 2};
    static final int[] HDMI = {0, 30, 90, 1, 210, 90, 1, 210, 130, 1, 185, 160, 1, 55, 160, 1, 30, 130, 2,
            0, 80, 120, 1, 160, 120};
    static final int[] SETTINGS = {0, 40, 70, 1, 140, 70, 0, 180, 70, 1, 200, 70, 0, 40, 170, 1, 60, 170,
            0, 100, 170, 1, 200, 170, 3, 160, 70, 20, 3, 80, 170, 20};
    static final int[] PLUS = {0, 120, 50, 1, 120, 190, 0, 50, 120, 1, 190, 120};
    // Weather. Op 4 is quadTo cx cy x y.
    static final int[] SUN = {3, 120, 120, 40, 0, 120, 30, 1, 120, 55, 0, 120, 185, 1, 120, 210, 0, 30, 120, 1, 55, 120,
            0, 185, 120, 1, 210, 120, 0, 56, 56, 1, 73, 73, 0, 167, 167, 1, 184, 184, 0, 184, 56, 1, 167, 73,
            0, 73, 167, 1, 56, 184};
    static final int[] CLOUD = {0, 60, 180, 1, 170, 180, 4, 215, 180, 215, 145, 4, 215, 110, 175, 108, 4, 170, 60, 120, 60,
            4, 78, 60, 72, 102, 4, 30, 104, 30, 142, 4, 30, 180, 60, 180};
    static final int[] PARTLY = {3, 90, 86, 28, 0, 90, 28, 1, 90, 44, 0, 28, 86, 1, 44, 86, 0, 46, 42, 1, 57, 53,
            0, 134, 42, 1, 123, 53, 0, 80, 200, 1, 175, 200, 4, 212, 200, 212, 172, 4, 212, 144, 178, 142,
            4, 172, 104, 132, 104, 4, 98, 104, 92, 138, 4, 60, 140, 60, 170, 4, 60, 200, 80, 200};
    static final int[] RAIN = {0, 60, 150, 1, 170, 150, 4, 215, 150, 215, 115, 4, 215, 80, 175, 78, 4, 170, 30, 120, 30,
            4, 78, 30, 72, 72, 4, 30, 74, 30, 112, 4, 30, 150, 60, 150,
            0, 80, 175, 1, 70, 215, 0, 125, 175, 1, 115, 215, 0, 170, 175, 1, 160, 215};

    private final int[] ops;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    LineIcon(int[] ops) {
        this.ops = ops;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xEBFFFFFF);
    }

    LineIcon tint(int color) {
        paint.setColor(color);
        return this;
    }

    @Override
    public void draw(Canvas c) {
        if (ops == null) return;
        float s = getBounds().width() / 240f; // ops are in tenths of the 24-unit grid
        paint.setStrokeWidth(16 * s);
        path.reset();
        for (int i = 0; i < ops.length; ) {
            switch (ops[i]) {
                case 0: path.moveTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 1: path.lineTo(ops[i + 1] * s, ops[i + 2] * s); i += 3; break;
                case 2: path.close(); i += 1; break;
                case 4: path.quadTo(ops[i + 1] * s, ops[i + 2] * s, ops[i + 3] * s, ops[i + 4] * s); i += 5; break;
                default: path.addCircle(ops[i + 1] * s, ops[i + 2] * s, ops[i + 3] * s, Path.Direction.CW); i += 4;
            }
        }
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.drawPath(path, paint);
        c.restore();
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 44; }
    @Override public int getIntrinsicHeight() { return 44; }
}
