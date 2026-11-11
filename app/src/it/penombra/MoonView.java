package it.penombra;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

/**
 * The signature: the screen's darkness drawn as a waning moon.
 * Luce ridotta sets the phase; Eclissi slides a shadow across it. Drag up/down on it to change the phase.
 */
public class MoonView extends View {
    public interface Listener {
        void onDrag(int level, boolean done);
    }

    private final Paint body = fill(Ui.BODY);
    private final Paint lit = fill(Ui.LIT);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = fill(Ui.GROUND);
    private final Paint shadowRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path a = new Path();
    private final Path b = new Path();
    private final Path clip = new Path();
    private final RectF ro = new RectF();
    private final RectF re = new RectF();
    private final int slop;

    private boolean nativeOn;
    private int level;
    private int eclipseLevel = Dim.DEFAULT_ECLIPSE;
    private float cover;
    private ValueAnimator anim;
    private ValueAnimator phaseAnim;
    private float shownLit = 1f;
    private Listener listener;
    private float radius = 1f;
    private float downY;
    private int downLevel;
    private boolean moved;

    public MoonView(Context c) {
        super(c);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1f);
        rim.setColor(0x33E3DCC8);
        shadowRim.setStyle(Paint.Style.STROKE);
        shadowRim.setStrokeWidth(2f);
        shadowRim.setColor(Ui.OCHRE);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setState(boolean nativeOn, int level, boolean eclipse, int eclipseLevel, boolean animate) {
        float fromLit = shownLit;
        this.nativeOn = nativeOn;
        this.level = level;
        this.eclipseLevel = eclipseLevel;
        float toLit = litFraction();
        if (phaseAnim != null) phaseAnim.cancel();
        if (animate && ValueAnimator.areAnimatorsEnabled() && Math.abs(fromLit - toLit) > 0.001f) {
            phaseAnim = ValueAnimator.ofFloat(fromLit, toLit);
            phaseAnim.setDuration(180);
            phaseAnim.setInterpolator(new DecelerateInterpolator(1.5f));
            phaseAnim.addUpdateListener(v -> {
                shownLit = (float) v.getAnimatedValue();
                invalidate();
            });
            phaseAnim.start();
        } else {
            shownLit = toLit;
        }
        float target = eclipse ? 1f : 0f;
        if (anim != null) anim.cancel();
        if (animate && ValueAnimator.areAnimatorsEnabled() && cover != target) {
            anim = ValueAnimator.ofFloat(cover, target);
            anim.setDuration(220);
            anim.setInterpolator(new DecelerateInterpolator(2f));
            anim.addUpdateListener(v -> {
                cover = (float) v.getAnimatedValue();
                invalidate();
            });
            anim.start();
        } else {
            cover = target;
        }
        Context c = getContext();
        setContentDescription(c.getString(R.string.moon_desc, Dim.phaseName(c, nativeOn, level),
                eclipse ? c.getString(R.string.moon_desc_eclipse) : ""));
        invalidate();
    }

    private float litFraction() {
        return nativeOn ? 1f - level / 100f : 1f;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - Ui.dp(getContext(), 14);
        radius = r;
        drawPhase(cv, cx, cy, r, shownLit, body, lit, a, b, ro, re);
        cv.drawCircle(cx, cy, r, rim);
        if (cover > 0.001f) {
            int save = cv.save();
            clip.reset();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            cv.clipPath(clip);
            float sx = cx + 0.22f * r + 2.0f * r * (1f - cover);
            float sr = r * 1.04f;
            shadow.setAlpha(Math.round(255 * (0.55f + 0.4f * eclipseLevel / 100f)));
            cv.drawCircle(sx, cy, sr, shadow);
            cv.drawCircle(sx, cy, sr, shadowRim);
            cv.restoreToCount(save);
        }
    }

    /** Waning moon lit on the left; f = lit fraction from 1 (full) to 0 (new). */
    static void drawPhase(Canvas cv, float cx, float cy, float r, float f,
                          Paint body, Paint lit, Path a, Path b, RectF ro, RectF re) {
        cv.drawCircle(cx, cy, r, body);
        if (f <= 0.005f) return;
        if (f >= 0.995f) {
            cv.drawCircle(cx, cy, r, lit);
            return;
        }
        ro.set(cx - r, cy - r, cx + r, cy + r);
        a.reset();
        a.addArc(ro, 90, 180);
        a.close();
        float rx = r * Math.abs(2f * f - 1f);
        re.set(cx - rx, cy - r, cx + rx, cy + r);
        b.reset();
        b.addOval(re, Path.Direction.CW);
        a.op(b, f >= 0.5f ? Path.Op.UNION : Path.Op.DIFFERENCE);
        cv.drawPath(a, lit);
    }

    private int levelAt(float y) {
        return Dim.clamp(downLevel + Math.round((y - downY) / (2f * radius) * 100f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getY();
                downLevel = level;
                moved = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.abs(e.getY() - downY) < slop) return true;
                moved = true;
                listener.onDrag(levelAt(e.getY()), false);
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (moved) listener.onDrag(levelAt(e.getY()), true);
                else performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (moved) listener.onDrag(level, true);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
