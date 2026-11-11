package it.penombra;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;
import android.widget.Switch;

/**
 * Material 3 switch drawn directly: 52x32dp track, ochre filled with a 24dp night thumb when on,
 * 2dp slate outline with a 16dp slate thumb when off. Announced as a switch to screen readers.
 */
final class PenSwitch extends View implements Checkable {
    interface Listener {
        void onChanged(PenSwitch s, boolean checked);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean checked;
    private float t; // 0 = off, 1 = on (animated)
    private ValueAnimator anim;
    private Listener listener;

    PenSwitch(Context c) {
        super(c);
        track.setColor(Ui.OCHRE);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Ui.dp(c, 2));
        outline.setColor(Ui.SLATE);
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> {
            setChecked(!checked, true);
            if (listener != null) listener.onChanged(this, checked);
        });
    }

    void setOnCheckedChangeListener(Listener l) {
        listener = l;
    }

    @Override
    public void setChecked(boolean c) {
        setChecked(c, false);
    }

    private void setChecked(boolean c, boolean animate) {
        if (c == checked && anim == null) {
            t = c ? 1f : 0f;
            invalidate();
            return;
        }
        checked = c;
        if (anim != null) anim.cancel();
        anim = null;
        float target = c ? 1f : 0f;
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            anim = ValueAnimator.ofFloat(t, target);
            anim.setDuration(160);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(v -> {
                t = (float) v.getAnimatedValue();
                invalidate();
            });
            anim.start();
        } else {
            t = target;
        }
        refreshDrawableState();
        invalidate();
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked, true);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 60), w), resolveSize(Ui.dp(getContext(), 48), h));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float dp = getResources().getDisplayMetrics().density;
        float tw = 52 * dp, th = 32 * dp;
        float left = getWidth() - tw - 2 * dp;
        float top = (getHeight() - th) / 2f;
        r.set(left, top, left + tw, top + th);
        float half = th / 2f;
        float sw = outline.getStrokeWidth() / 2f;

        // track: outline fades into fill
        track.setAlpha(Math.round(255 * t));
        cv.drawRoundRect(r, half, half, track);
        if (t < 1f) {
            outline.setAlpha(Math.round(255 * (1f - t)));
            RectF o = new RectF(r.left + sw, r.top + sw, r.right - sw, r.bottom - sw);
            cv.drawRoundRect(o, half - sw, half - sw, outline);
        }
        // thumb: slides and grows from 16dp slate to 24dp night
        float cx = r.left + half + (tw - th) * t;
        float rad = (8 + 4 * t) * dp;
        thumb.setColor(blend(Ui.SLATE, Ui.GROUND, t));
        cv.drawCircle(cx, r.centerY(), rad, thumb);
    }

    private static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return 0xff000000 | (Math.round(ar + (br - ar) * f) << 16)
                | (Math.round(ag + (bg - ag) * f) << 8) | Math.round(ab + (bb - ab) * f);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
