package it.penombra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small phase glyph: a preset, or the current state in the quick panel. */
final class MoonGlyph extends View {
    private final Paint body = MoonView.fill(Ui.BODY);
    private final Paint lit = MoonView.fill(Ui.LIT);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = MoonView.fill(Ui.GROUND);
    private final Path a = new Path();
    private final Path b = new Path();
    private final Path clip = new Path();
    private final RectF ro = new RectF();
    private final RectF re = new RectF();
    private float fraction;
    private boolean eclipse;

    MoonGlyph(Context c, float fraction, boolean eclipse) {
        super(c);
        this.fraction = fraction;
        this.eclipse = eclipse;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setOn(false);
    }

    void setOn(boolean on) {
        lit.setColor(on ? Ui.OCHRE : Ui.LIT);
        rim.setColor(on ? Ui.OCHRE : 0x40E3DCC8);
        invalidate();
    }

    void setPhase(float fraction, boolean eclipse) {
        this.fraction = fraction;
        this.eclipse = eclipse;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - 1.5f;
        MoonView.drawPhase(cv, cx, cy, r, eclipse ? Math.max(fraction, 0.35f) : fraction, body, lit, a, b, ro, re);
        if (eclipse) {
            int save = cv.save();
            clip.reset();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            cv.clipPath(clip);
            shadow.setAlpha(235);
            cv.drawCircle(cx + r * 0.28f, cy, r * 1.02f, shadow);
            cv.restoreToCount(save);
        }
        cv.drawCircle(cx, cy, r, rim);
    }
}
