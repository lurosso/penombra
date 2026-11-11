package it.penombra;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** Lunario palette and small view helpers. */
final class Ui {
    static final int GROUND = 0xFF0B1022;
    static final int SURFACE = 0xFF141B35;
    static final int BODY = 0xFF171E3A;
    static final int BONE = 0xFFE3DCC8;
    /** Moonlight kept well under bone so the moon never glares in a dark room. */
    static final int LIT = 0xFFB9B3A1;
    static final int SLATE = 0xFF8D94AA;
    static final int OCHRE = 0xFFC59B4F;
    static final int RULE = 0x24E3DCC8;
    static final int RIPPLE = 0x22E3DCC8;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, int color, int weight) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, weight, false));
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable outline(Context c, int stroke, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(1, stroke); // one device pixel
        return g;
    }

    static Drawable pressable(Context c, int stroke, int fill, float radiusDp) {
        GradientDrawable shape = outline(c, stroke, fill, radiusDp);
        GradientDrawable mask = outline(c, 0xFFFFFFFF, 0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, mask);
    }

    static ColorStateList checked(int on, int off) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{on, off});
    }
}
