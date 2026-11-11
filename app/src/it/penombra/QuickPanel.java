package it.penombra;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Opened by long-pressing the Quick Settings tile: a sheet over whatever is on screen,
 * to set the level, pick a preset or toggle the eclipse without opening the app.
 * In solo Eclissi (no WRITE_SECURE_SETTINGS) the head switch, slider and presets drive the veil
 * and the separate Eclissi row is hidden.
 */
public class QuickPanel extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private LinearLayout sheet;
    private MoonGlyph glyph;
    private TextView phase;
    private TextView summary;
    private PenSwitch nativeSw;
    private PenSwitch eclSw;
    private View eclRow;
    private SeekBar seek;
    private TextView val;
    private final LinearLayout[] boxes = new LinearLayout[Dim.PRESETS.length];
    private final MoonGlyph[] glyphs = new MoonGlyph[Dim.PRESETS.length];
    private final TextView[] labels = new TextView[Dim.PRESETS.length];
    private boolean adjusting;
    private boolean solo;
    private long lastWrite;
    private boolean closing;
    private boolean shown;
    private ContentObserver observer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        overridePendingTransition(0, 0);

        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> dismiss());
        root.setContentDescription(getString(R.string.close));

        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.GROUND);
        float rad = Ui.dp(this, 28);
        bg.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        bg.setStroke(1, Ui.RULE);
        sheet.setBackground(bg);

        View handle = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0x668D94AA);
        hb.setCornerRadius(Ui.dp(this, 2));
        handle.setBackground(hb);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(this, 16);
        sheet.addView(handle, hlp);

        // Header: current phase, Luce ridotta switch
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        glyph = new MoonGlyph(this, 1f, false);
        int gs = Ui.dp(this, 40);
        head.addView(glyph, new LinearLayout.LayoutParams(gs, gs));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        phase = Ui.text(this, 20, Ui.BONE, 400);
        summary = Ui.text(this, 14, Ui.SLATE, 400);
        summary.setFontFeatureSettings("tnum");
        texts.addView(phase);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
        slp.topMargin = Ui.dp(this, 4);
        texts.addView(summary, slp);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.setMarginStart(Ui.dp(this, 14));
        head.addView(texts, tl);
        nativeSw = new PenSwitch(this);
        nativeSw.setContentDescription(getString(R.string.native_title));
        head.addView(nativeSw, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 48)));
        sheet.addView(head);

        // Level slider
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        seek = new SeekBar(this);
        seek.setMax(100);
        seek.setSplitTrack(false);
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x40E3DCC8));
        seek.setContentDescription(getString(R.string.native_intensity));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f);
        sl.setMarginStart(-Ui.dp(this, 12));
        row.addView(seek, sl);
        val = Ui.text(this, 15, Ui.SLATE, 500);
        val.setGravity(Gravity.END);
        val.setFontFeatureSettings("tnum");
        row.addView(val, new LinearLayout.LayoutParams(Ui.dp(this, 52), WRAP));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = Ui.dp(this, 8);
        sheet.addView(row, rlp);

        // Presets
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setBaselineAligned(false);
        for (int i = 0; i < Dim.PRESETS.length; i++) {
            final Dim.Preset p = Dim.PRESETS[i];
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            box.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 10));
            box.setMinimumHeight(Ui.dp(this, 72));
            box.setClickable(true);
            box.setFocusable(true);
            box.setOnClickListener(v -> applyPreset(p));
            MoonGlyph g = new MoonGlyph(this, 1f - p.nativeLevel / 100f, p.eclipseLevel > 0);
            int ps = Ui.dp(this, 22);
            box.addView(g, new LinearLayout.LayoutParams(ps, ps));
            TextView l = Ui.text(this, 13, Ui.BONE, 400);
            l.setText(p.label);
            l.setGravity(Gravity.CENTER);
            l.setMaxLines(2);
            l.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
            llp.topMargin = Ui.dp(this, 6);
            box.addView(l, llp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
            if (i > 0) lp.setMarginStart(Ui.dp(this, 8));
            presets.addView(box, lp);
            boxes[i] = box;
            glyphs[i] = g;
            labels[i] = l;
        }
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, WRAP);
        plp.topMargin = Ui.dp(this, 8);
        sheet.addView(presets, plp);

        // Eclipse
        LinearLayout ecl = new LinearLayout(this);
        ecl.setOrientation(LinearLayout.HORIZONTAL);
        ecl.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout et = new LinearLayout(this);
        et.setOrientation(LinearLayout.VERTICAL);
        TextView eTitle = Ui.text(this, 16, Ui.BONE, 500);
        eTitle.setText(R.string.eclipse_title);
        TextView eSub = Ui.text(this, 14, Ui.SLATE, 400);
        eSub.setText(R.string.eclipse_short);
        et.addView(eTitle);
        LinearLayout.LayoutParams esl = new LinearLayout.LayoutParams(WRAP, WRAP);
        esl.topMargin = Ui.dp(this, 4);
        et.addView(eSub, esl);
        ecl.addView(et, new LinearLayout.LayoutParams(0, WRAP, 1f));
        eclSw = new PenSwitch(this);
        eclSw.setContentDescription(getString(R.string.eclipse_title));
        ecl.addView(eclSw, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 48)));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(MATCH, WRAP);
        elp.topMargin = Ui.dp(this, 16);
        sheet.addView(ecl, elp);
        eclRow = ecl;

        // Open the full app
        TextView open = Ui.text(this, 15, Ui.BONE, 500);
        open.setText(R.string.open_app);
        open.setGravity(Gravity.CENTER);
        open.setMinHeight(Ui.dp(this, 48));
        open.setBackground(Ui.pressable(this, 0x40E3DCC8, Ui.GROUND, 24));
        open.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            overridePendingTransition(0, 0);
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(MATCH, WRAP);
        olp.topMargin = Ui.dp(this, 16);
        sheet.addView(open, olp);

        root.addView(sheet, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        final int side = Ui.dp(this, 20);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets s = insets.getInsets(WindowInsets.Type.systemBars());
            sheet.setPadding(side + s.left, Ui.dp(this, 10), side + s.right, s.bottom + Ui.dp(this, 16));
            return WindowInsets.CONSUMED;
        });
        setContentView(root);

        wire();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (!adjusting) refresh();
            }
        };
    }

    private void wire() {
        nativeSw.setOnCheckedChangeListener((s, on) -> {
            if (solo) {
                if (on && !Dim.canOverlay(this)) {
                    openApp();
                    return;
                }
                Dim.setEclipse(this, on, true);
                refresh();
                return;
            }
            if (!Dim.setNative(this, on, true) && on) openApp();
            refresh();
            Dim.refreshTile(this);
        });
        eclSw.setOnCheckedChangeListener((s, on) -> {
            if (on && !Dim.canOverlay(this)) {
                openApp();
                return;
            }
            Dim.setEclipse(this, on, true);
            refresh();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar s) {
                adjusting = true;
            }

            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                setLevel(p, false);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                setLevel(s.getProgress(), true);
                adjusting = false;
                refresh();
                Dim.refreshTile(QuickPanel.this);
            }
        });
    }

    /** Throttled while dragging; always written on release. */
    private void setLevel(int level, boolean done) {
        if (solo) {
            setVeil(level, done);
            return;
        }
        Prefs.get(this).edit().putInt(Prefs.NATIVE_LEVEL, level).apply();
        long now = SystemClock.uptimeMillis();
        if (done || now - lastWrite > 70) {
            Dim.setNativeLevel(this, level);
            if (!Dim.nativeOn(this) && !Dim.setNative(this, true, true)) openApp();
            lastWrite = now;
        }
        showLevel(true, level);
    }

    /** Solo Eclissi: the slider is the veil's, throttled the same way. */
    private void setVeil(int level, boolean done) {
        long now = SystemClock.uptimeMillis();
        if (done || now - lastWrite > 70) {
            Dim.setEclipseLevel(this, level);
            lastWrite = now;
        } else {
            Prefs.get(this).edit().putInt(Prefs.ECL_LEVEL, level).apply();
        }
        if (done && !Dim.eclipseRunning(this) && !Dim.setEclipse(this, true, true)) {
            openApp();
            return;
        }
        show(false, Dim.nativeLevel(this), true, level);
    }

    private void applyPreset(Dim.Preset p) {
        if (solo) {
            if (!Dim.canOverlay(this)) {
                openApp();
                return;
            }
            Dim.applySoloPreset(this, p);
            refresh();
            Dim.refreshTile(this);
            return;
        }
        if (!Dim.canWrite(this) || (p.eclipseLevel > 0 && !Dim.canOverlay(this))) {
            openApp();
            return;
        }
        Dim.applyPreset(this, p);
        refresh();
        Dim.refreshTile(this);
    }

    private void openApp() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    private void showLevel(boolean on, int level) {
        show(on, level, Dim.eclipseRunning(this), Dim.eclipseLevel(this));
    }

    private void show(boolean nOn, int nLvl, boolean eOn, int eLvl) {
        glyph.setPhase(nOn ? 1f - nLvl / 100f : 1f, eOn);
        phase.setText(Dim.phaseName(this, nOn, nLvl, eOn));
        val.setText((solo ? eLvl : nLvl) + "%");
        summary.setText(Dim.summary(this, nOn, nLvl, eOn, eLvl));
    }

    private void refresh() {
        solo = Dim.solo(this);
        boolean nOn = !solo && Dim.nativeOn(this);
        int nLvl = Dim.nativeLevel(this);
        boolean eOn = Dim.eclipseRunning(this);
        int eLvl = Dim.eclipseLevel(this);
        boolean live = solo ? eOn : nOn;
        nativeSw.setChecked(live);
        nativeSw.setContentDescription(getString(solo ? R.string.eclipse_title : R.string.native_title));
        seek.setContentDescription(getString(solo ? R.string.eclipse_intensity : R.string.native_intensity));
        eclRow.setVisibility(solo ? View.GONE : View.VISIBLE);
        eclSw.setChecked(eOn);
        if (!adjusting) seek.setProgress(solo ? eLvl : nLvl);
        ColorStateList c = ColorStateList.valueOf(live ? Ui.OCHRE : Ui.SLATE);
        seek.setProgressTintList(c);
        seek.setThumbTintList(c);
        val.setTextColor(live ? Ui.OCHRE : Ui.SLATE);
        show(nOn, nLvl, eOn, eLvl);
        for (int i = 0; i < Dim.PRESETS.length; i++) {
            Dim.Preset p = Dim.PRESETS[i];
            boolean sel = solo ? Dim.soloSelected(p, eOn, eLvl)
                    : nOn && nLvl == p.nativeLevel
                    && (p.eclipseLevel == 0 ? !eOn : eOn && eLvl == p.eclipseLevel);
            glyphs[i].setOn(sel);
            labels[i].setTextColor(sel ? Ui.OCHRE : Ui.BONE);
            boxes[i].setBackground(Ui.pressable(this, sel ? Ui.OCHRE : Ui.RULE, Ui.GROUND, 16));
            boxes[i].setContentDescription(getString(sel ? R.string.preset_desc_on : R.string.preset_desc,
                    getString(p.label)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_ON), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_LEVEL), false, observer);
        refresh();
        if (!shown) {
            shown = true;
            sheet.setTranslationY(Ui.dp(this, 480));
            sheet.animate().translationY(0).setDuration(ValueAnimator.areAnimatorsEnabled() ? 220 : 0)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    @Override
    protected void onPause() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        getContentResolver().unregisterContentObserver(observer);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.ECL_RUNNING.equals(key) && !adjusting) refresh();
    }

    private void dismiss() {
        if (closing) return;
        closing = true;
        sheet.animate().translationY(sheet.getHeight()).setDuration(ValueAnimator.areAnimatorsEnabled() ? 160 : 0)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }
}
