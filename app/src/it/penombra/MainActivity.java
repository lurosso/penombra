package it.penombra;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.app.StatusBarManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private ScrollView scroll;
    private MoonView moon;
    private TextView phase;
    private TextView summary;
    private LinearLayout permCard;
    private TextView nativeSub;
    private View nativeRow;
    private final LinearLayout[] presetBoxes = new LinearLayout[Dim.PRESETS.length];
    private final MoonGlyph[] glyphs = new MoonGlyph[Dim.PRESETS.length];
    private final TextView[] presetLabels = new TextView[Dim.PRESETS.length];
    private PenSwitch nativeSw;
    private PenSwitch eclSw;
    private PenSwitch schedSw;
    private PenSwitch lockSw;
    private View lockRow;
    private SeekBar nativeSeek;
    private SeekBar eclSeek;
    private TextView nativeVal;
    private TextView eclVal;
    private TextView eclSub;
    private View veilHint;
    private TextView startBtn;
    private TextView endBtn;
    private TextView nextLine;
    private boolean binding;
    private boolean pendingOverlay;
    private boolean adjusting;
    private boolean nativeOnCached;
    private long lastWrite;
    private ContentObserver observer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.GROUND);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));

        buildMoon(col);
        buildPresets(col);
        col.addView(rule(), ruleLp(24));
        buildNative(col);
        buildPermissionCard(col);
        col.addView(rule(), ruleLp(8));
        buildEclipse(col);
        col.addView(rule(), ruleLp(8));
        buildSchedule(col);

        // A night-coloured band under the status bar, so scrolled text never runs into the clock.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.GROUND);
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        View statusScrim = new View(this);
        statusScrim.setBackgroundColor(Ui.GROUND);
        root.addView(statusScrim, new FrameLayout.LayoutParams(MATCH, 0, Gravity.TOP));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.GROUND, 0x000B1022}));
        root.addView(fade, new FrameLayout.LayoutParams(MATCH, Ui.dp(this, 20), Gravity.TOP));

        final int side = Ui.dp(this, 20);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            col.setPadding(side + s.left, s.top + Ui.dp(this, 4), side + s.right, s.bottom + Ui.dp(this, 28));
            statusScrim.getLayoutParams().height = s.top;
            statusScrim.requestLayout();
            ((FrameLayout.LayoutParams) fade.getLayoutParams()).topMargin = s.top;
            fade.requestLayout();
            return WindowInsets.CONSUMED;
        });
        setContentView(root);

        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                // While a finger is on the moon or the slider, the finger is the truth.
                if (!adjusting) refresh(true);
            }
        };

        // A force-stop (e.g. from a task manager) cancels the alarms: opening the app sets them again.
        if (Scheduler.enabled(this)) Scheduler.reschedule(this);
    }

    /**
     * Writes the level while dragging, at most every 70 ms, and always on release,
     * so Android is not flooded and the moon never fights the finger.
     */
    private boolean liveWrite(int level, boolean done) {
        Prefs.get(this).edit().putInt(Prefs.NATIVE_LEVEL, level).apply();
        long now = SystemClock.uptimeMillis();
        if (!done && now - lastWrite < 70) return true;
        lastWrite = now;
        Dim.setNativeLevel(this, level);
        if (!nativeOnCached) {
            if (!Dim.setNative(this, true, true)) return false;
            nativeOnCached = true;
        }
        return true;
    }

    /** Without the native layer the moon sets the veil: throttled like the native writes. */
    private void soloDrag(int level, boolean done) {
        long now = SystemClock.uptimeMillis();
        if (done || now - lastWrite >= 70) {
            lastWrite = now;
            Dim.setEclipseLevel(this, level);
        } else {
            Prefs.get(this).edit().putInt(Prefs.ECL_LEVEL, level).apply();
        }
        if (!done) {
            showSoloLive(level);
            return;
        }
        if (!Dim.eclipseRunning(this)) startEclipse();
        refresh(false);
    }

    // ---- building -------------------------------------------------------------------------

    private void buildMoon(LinearLayout col) {
        moon = new MoonView(this);
        moon.setListener((level, done) -> {
            adjusting = !done;
            if (Dim.solo(this)) {
                soloDrag(level, done);
                return;
            }
            if (!liveWrite(level, done)) {
                adjusting = false;
                showPermissionNeeded();
                return;
            }
            if (done) refresh(false);
            else showLive(true, level);
        });
        col.addView(moon, new LinearLayout.LayoutParams(MATCH, Ui.dp(this, 252)));

        phase = Ui.text(this, 30, Ui.BONE, 300);
        phase.setGravity(Gravity.CENTER_HORIZONTAL);
        phase.setLetterSpacing(-0.01f);
        col.addView(phase, new LinearLayout.LayoutParams(MATCH, WRAP));

        summary = Ui.text(this, 14, Ui.SLATE, 400);
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        summary.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = Ui.dp(this, 8);
        col.addView(summary, lp);
    }

    /** Inside the Luce ridotta section, shown only while WRITE_SECURE_SETTINGS is missing. */
    private void buildPermissionCard(LinearLayout col) {
        final String cmd = "adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        permCard = new LinearLayout(this);
        permCard.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        permCard.setPadding(pad, pad, pad, pad);
        permCard.setBackground(Ui.outline(this, Ui.RULE, Ui.SURFACE, 16));

        TextView title = Ui.text(this, 16, Ui.BONE, 500);
        title.setText(R.string.perm_title);
        permCard.addView(title);

        TextView body = Ui.text(this, 14, Ui.SLATE, 400);
        body.setText(R.string.perm_body);
        body.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, WRAP);
        blp.topMargin = Ui.dp(this, 6);
        permCard.addView(body, blp);

        TextView code = Ui.text(this, 13, Ui.BONE, 400);
        code.setTypeface(Typeface.MONOSPACE);
        code.setText(cmd);
        code.setTextIsSelectable(true);
        code.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        code.setBackground(Ui.outline(this, 0x00000000, 0x14E3DCC8, 8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = Ui.dp(this, 10);
        permCard.addView(code, clp);

        TextView copy = Ui.text(this, 14, Ui.BONE, 500);
        copy.setText(R.string.perm_copy);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setMinHeight(Ui.dp(this, 48));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("Penombra", cmd));
            copy.setText(R.string.perm_copied);
            copy.postDelayed(() -> copy.setText(R.string.perm_copy), 2000);
        });
        permCard.addView(copy, new LinearLayout.LayoutParams(WRAP, WRAP));

        TextView note = Ui.text(this, 14, Ui.SLATE, 400);
        note.setText(R.string.perm_note);
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        permCard.addView(note, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = Ui.dp(this, 14);
        lp.bottomMargin = Ui.dp(this, 16);
        col.addView(permCard, lp);
    }

    private void buildPresets(LinearLayout col) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        int gap = Ui.dp(this, 8);
        for (int i = 0; i < Dim.PRESETS.length; i++) {
            final Dim.Preset p = Dim.PRESETS[i];
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            box.setClickable(true);
            box.setFocusable(true);
            box.setContentDescription(getString(R.string.preset_desc, getString(p.label)));
            box.setOnClickListener(v -> applyPreset(p));

            MoonGlyph g = new MoonGlyph(this, 1f - p.nativeLevel / 100f, p.eclipseLevel > 0);
            int gs = Ui.dp(this, 26);
            box.addView(g, new LinearLayout.LayoutParams(gs, gs));

            TextView l = Ui.text(this, 13, Ui.BONE, 400);
            l.setText(p.label);
            l.setGravity(Gravity.CENTER);
            l.setMaxLines(2);
            l.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(WRAP, WRAP);
            llp.topMargin = Ui.dp(this, 8);
            box.addView(l, llp);

            box.setMinimumHeight(Ui.dp(this, 80));
            box.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
            if (i > 0) lp.setMarginStart(gap);
            row.addView(box, lp);
            presetBoxes[i] = box;
            glyphs[i] = g;
            presetLabels[i] = l;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = Ui.dp(this, 28);
        col.addView(row, lp);
    }

    private void buildNative(LinearLayout col) {
        nativeSw = makeSwitch(getString(R.string.native_title));
        nativeSub = Ui.text(this, 14, Ui.SLATE, 400);
        col.addView(header(getString(R.string.native_title), getString(R.string.native_sub), nativeSw, nativeSub));
        nativeVal = valueLabel();
        nativeSeek = makeSeek();
        nativeRow = sliderRow(nativeSeek, nativeVal);
        col.addView(nativeRow);

        lockSw = makeSwitch(getString(R.string.lock_title));
        lockRow = optionRow(getString(R.string.lock_title), getString(R.string.lock_sub), lockSw);
        col.addView(lockRow);
        lockSw.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Prefs.get(this).edit().putBoolean(Prefs.LOCK_VEIL, on).apply();
            Dim.syncVeilService(this);
        });

        nativeSw.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (!Dim.setNative(this, on, true) && on) showPermissionNeeded();
            refresh(true);
        });
        nativeSeek.setOnSeekBarChangeListener(new SeekListener() {
            @Override
            public void onStartTrackingTouch(SeekBar s) {
                adjusting = true;
            }

            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                if (!liveWrite(p, false)) showPermissionNeeded();
                showLive(true, p);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                liveWrite(s.getProgress(), true);
                adjusting = false;
                refresh(false);
            }
        });
    }

    private void buildEclipse(LinearLayout col) {
        eclSw = makeSwitch(getString(R.string.eclipse_title));
        eclSub = Ui.text(this, 14, Ui.SLATE, 400);
        col.addView(header(getString(R.string.eclipse_title), null, eclSw, eclSub));
        eclVal = valueLabel();
        eclSeek = makeSeek();
        col.addView(sliderRow(eclSeek, eclVal));
        buildVeilHint(col);

        eclSw.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (on) startEclipse();
            else Dim.setEclipse(this, false, true);
            refresh(true);
        });
        eclSeek.setOnSeekBarChangeListener(new SeekListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                Dim.setEclipseLevel(MainActivity.this, p);
                eclVal.setText(p + "%");
                moon.setState(Dim.nativeOn(MainActivity.this), Dim.nativeLevel(MainActivity.this),
                        Dim.eclipseRunning(MainActivity.this), p, false);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (!Dim.eclipseRunning(MainActivity.this)) startEclipse();
                refresh(false);
            }
        });
    }

    /**
     * Without the ADB permission Penombra cannot switch its accessibility service on by itself, and only that
     * service can draw the veil above the status bar: say so, and open the page where the person can do it.
     */
    private void buildVeilHint(LinearLayout col) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 14, Ui.SLATE, 400);
        t.setText(R.string.veil_hint);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        box.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView open = Ui.text(this, 14, Ui.BONE, 500);
        open.setText(R.string.veil_hint_open);
        open.setGravity(Gravity.CENTER_VERTICAL);
        open.setMinHeight(Ui.dp(this, 48));
        open.setOnClickListener(v -> openAccessibility());
        box.addView(open, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = Ui.dp(this, 4);
        col.addView(box, lp);
        veilHint = box;
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException ignored) {
        }
    }

    private void buildSchedule(LinearLayout col) {
        schedSw = makeSwitch(getString(R.string.schedule_title));
        col.addView(header(getString(R.string.schedule_title), getString(R.string.schedule_sub), schedSw, null));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        startBtn = timeButton(Prefs.SCHED_START, Scheduler.DEFAULT_START, getString(R.string.schedule_start_desc));
        endBtn = timeButton(Prefs.SCHED_END, Scheduler.DEFAULT_END, getString(R.string.schedule_end_desc));
        times.addView(startBtn, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
        elp.setMarginStart(Ui.dp(this, 8));
        times.addView(endBtn, elp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tlp.topMargin = Ui.dp(this, 14);
        col.addView(times, tlp);

        nextLine = Ui.text(this, 14, Ui.SLATE, 500);
        nextLine.setFontFeatureSettings("tnum");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        nlp.topMargin = Ui.dp(this, 12);
        col.addView(nextLine, nlp);

        schedSw.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            Prefs.get(this).edit().putBoolean(Prefs.SCHED_ON, on).apply();
            Scheduler.reschedule(this);
            refresh(false);
        });

        if (Build.VERSION.SDK_INT >= 33) {
            col.addView(rule(), ruleLp(24));
            TextView addTile = Ui.text(this, 15, Ui.BONE, 500);
            addTile.setText(R.string.add_tile);
            addTile.setSingleLine(true);
            addTile.setGravity(Gravity.CENTER);
            addTile.setMinHeight(Ui.dp(this, 52));
            addTile.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            addTile.setBackground(Ui.pressable(this, 0x40E3DCC8, Ui.GROUND, 26));
            addTile.setOnClickListener(v -> requestTile((TextView) v));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(MATCH, WRAP);
            alp.topMargin = Ui.dp(this, 20);
            col.addView(addTile, alp);

            // Per-app language (Android 13+): System, Italiano, English. Android recreates the screen itself.
            TextView lang = Ui.text(this, 15, Ui.BONE, 500);
            lang.setText(getString(R.string.language, languageName()));
            lang.setSingleLine(true);
            lang.setGravity(Gravity.CENTER);
            lang.setMinHeight(Ui.dp(this, 52));
            lang.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            lang.setBackground(Ui.pressable(this, 0x40E3DCC8, Ui.GROUND, 26));
            lang.setOnClickListener(v -> pickLanguage());
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(MATCH, WRAP);
            llp.topMargin = Ui.dp(this, 12);
            col.addView(lang, llp);
        }
    }

    private static final String[] LANGS = {"", "it", "en"};

    /** The language set for Penombra, or "System" when it follows the phone. */
    private String languageName() {
        LocaleList l = getSystemService(LocaleManager.class).getApplicationLocales();
        if (l.isEmpty()) return getString(R.string.language_system);
        String tag = l.get(0).getLanguage();
        return "it".equals(tag) ? "Italiano" : "en".equals(tag) ? "English" : l.get(0).getDisplayLanguage(l.get(0));
    }

    private void pickLanguage() {
        LocaleList l = getSystemService(LocaleManager.class).getApplicationLocales();
        String cur = l.isEmpty() ? "" : l.get(0).getLanguage();
        int checked = 0;
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(cur)) checked = i;
        String[] names = {getString(R.string.language_system), "Italiano", "English"};
        new AlertDialog.Builder(this, R.style.PenombraAlert)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(names, checked, (dlg, which) -> {
                    dlg.dismiss();
                    getSystemService(LocaleManager.class).setApplicationLocales(
                            which == 0 ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(LANGS[which]));
                })
                .show();
    }

    private void requestTile(TextView button) {
        StatusBarManager sbm = getSystemService(StatusBarManager.class);
        sbm.requestAddTileService(new ComponentName(this, DimTileService.class), "Penombra",
                Icon.createWithResource(this, R.drawable.ic_tile), getMainExecutor(), r -> {
                    if (r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            || r == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        button.setText(R.string.tile_added);
                        button.setTextColor(Ui.SLATE);
                        button.setEnabled(false);
                    }
                });
    }

    private View header(String title, String sub, PenSwitch sw, TextView subView) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 64));
        row.setPadding(0, Ui.dp(this, 14), 0, 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 18, Ui.BONE, 500);
        t.setText(title);
        texts.addView(t);
        TextView s = subView != null ? subView : Ui.text(this, 14, Ui.SLATE, 400);
        if (sub != null) s.setText(sub);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, WRAP);
        slp.topMargin = Ui.dp(this, 4);
        texts.addView(s, slp);

        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.setMarginEnd(Ui.dp(this, 16));
        row.addView(texts, tl);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 48)));
        return row;
    }

    /** A smaller row for an option that belongs to the section above: title, explanation, switch. */
    private View optionRow(String title, String sub, PenSwitch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 56));
        row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 16, Ui.BONE, 500);
        t.setText(title);
        texts.addView(t);
        TextView s = Ui.text(this, 14, Ui.SLATE, 400);
        s.setText(sub);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, WRAP);
        slp.topMargin = Ui.dp(this, 4);
        texts.addView(s, slp);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.setMarginEnd(Ui.dp(this, 16));
        row.addView(texts, tl);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 48)));
        return row;
    }

    private View sliderRow(SeekBar seek, TextView val) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f);
        sl.setMarginStart(-Ui.dp(this, 12));
        row.addView(seek, sl);
        row.addView(val, new LinearLayout.LayoutParams(Ui.dp(this, 52), WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = Ui.dp(this, 4);
        lp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);
        return row;
    }

    private PenSwitch makeSwitch(String label) {
        PenSwitch sw = new PenSwitch(this);
        sw.setContentDescription(label);
        return sw;
    }

    private SeekBar makeSeek() {
        SeekBar s = new SeekBar(this);
        s.setMax(100);
        s.setProgressBackgroundTintList(ColorStateList.valueOf(0x40E3DCC8));
        s.setSplitTrack(false);
        return s;
    }

    private TextView valueLabel() {
        TextView v = Ui.text(this, 15, Ui.SLATE, 500);
        v.setGravity(Gravity.END);
        v.setFontFeatureSettings("tnum");
        return v;
    }

    private TextView timeButton(String key, int def, String desc) {
        TextView b = Ui.text(this, 15, Ui.BONE, 400);
        b.setGravity(Gravity.CENTER);
        b.setFontFeatureSettings("tnum");
        b.setBackground(Ui.pressable(this, 0x40E3DCC8, Ui.GROUND, 26));
        b.setContentDescription(desc);
        b.setOnClickListener(v -> {
            int cur = Prefs.get(this).getInt(key, def);
            new TimePickerDialog(this, R.style.PenombraDialog, (tp, h, m) -> {
                Prefs.get(this).edit().putInt(key, h * 60 + m).apply();
                Scheduler.reschedule(this);
                refresh(false);
            }, cur / 60, cur % 60, true).show();
        });
        return b;
    }

    private View rule() {
        View v = new View(this);
        v.setBackgroundColor(Ui.RULE);
        return v;
    }

    private LinearLayout.LayoutParams ruleLp(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 1);
        lp.topMargin = Ui.dp(this, topDp);
        return lp;
    }

    // ---- actions --------------------------------------------------------------------------

    private void applyPreset(Dim.Preset p) {
        if (Dim.solo(this)) {
            Dim.setEclipseLevel(this, p.soloEclipse);
            startEclipse();
            refresh(true);
            return;
        }
        if (p.eclipseLevel > 0 && !Dim.canOverlay(this)) {
            Dim.setNativeLevel(this, p.nativeLevel);
            Dim.setNative(this, true, true);
            Dim.setEclipseLevel(this, p.eclipseLevel);
            askOverlay();
            refresh(true);
            return;
        }
        if (p.eclipseLevel > 0) askNotificationsOnce();
        Dim.applyPreset(this, p);
        refresh(true);
    }

    private void startEclipse() {
        if (!Dim.canOverlay(this)) {
            askOverlay();
            return;
        }
        askNotificationsOnce();
        Dim.setEclipse(this, true, true);
    }

    private void askOverlay() {
        pendingOverlay = true;
        Prefs.get(this).edit().putBoolean(Prefs.ECL_WANTED, true).apply();
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private void askNotificationsOnce() {
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = Prefs.get(this);
        if (p.getBoolean(Prefs.ASKED_NOTIF, false)) return;
        p.edit().putBoolean(Prefs.ASKED_NOTIF, true).apply();
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    private void showPermissionNeeded() {
        permCard.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, permCard.getTop() - Ui.dp(this, 24))));
    }

    // ---- state ----------------------------------------------------------------------------

    /** Fast path while dragging: moon and labels only. */
    private void showLive(boolean nativeOn, int level) {
        boolean ecl = Dim.eclipseRunning(this);
        int el = Dim.eclipseLevel(this);
        moon.setState(nativeOn, level, ecl, el, false);
        phase.setText(Dim.phaseName(this, nativeOn, level, ecl));
        summary.setText(Dim.summary(this, nativeOn, level, ecl, el));
        nativeVal.setText(level + "%");
        if (nativeSeek.getProgress() != level) {
            binding = true;
            nativeSeek.setProgress(level);
            binding = false;
        }
    }

    /** Fast path while dragging the moon in solo Eclissi: the veil is previewed on the moon. */
    private void showSoloLive(int level) {
        moon.setState(false, level, true, level, false);
        phase.setText(Dim.phaseName(this, false, level, true));
        summary.setText(Dim.summary(this, false, 0, true, level));
        eclVal.setText(level + "%");
        if (eclSeek.getProgress() != level) {
            binding = true;
            eclSeek.setProgress(level);
            binding = false;
        }
    }

    private void refresh(boolean animate) {
        binding = true;
        boolean canWrite = Dim.canWrite(this);
        boolean solo = !canWrite;
        boolean canOverlay = Dim.canOverlay(this);
        boolean nOn = canWrite && Dim.nativeOn(this);
        nativeOnCached = nOn;
        int nLvl = Dim.nativeLevel(this);
        boolean eOn = Dim.eclipseRunning(this);
        int eLvl = Dim.eclipseLevel(this);

        permCard.setVisibility(solo ? View.VISIBLE : View.GONE);
        nativeSw.setVisibility(solo ? View.GONE : View.VISIBLE);
        nativeRow.setVisibility(solo ? View.GONE : View.VISIBLE);
        lockRow.setVisibility(solo ? View.GONE : View.VISIBLE);
        lockSw.setChecked(Dim.lockVeil(this));
        nativeSub.setText(solo ? R.string.native_sub_solo : R.string.native_sub);

        // In solo Eclissi the moon's drag level is the veil's.
        moon.setState(nOn, solo ? eLvl : nLvl, eOn, eLvl, animate);
        phase.setText(Dim.phaseName(this, nOn, nLvl, eOn));
        summary.setText(Dim.summary(this, nOn, nLvl, eOn, eLvl));

        nativeSw.setChecked(nOn);
        nativeSeek.setProgress(nLvl);
        tint(nativeSeek, nativeVal, nOn);
        nativeVal.setText(nLvl + "%");

        eclSw.setChecked(eOn);
        eclSeek.setProgress(eLvl);
        tint(eclSeek, eclVal, eOn);
        eclVal.setText(eLvl + "%");
        eclSub.setText(eclipseSub(solo, canOverlay));
        veilHint.setVisibility(solo && VeilService.live == null ? View.VISIBLE : View.GONE);

        for (int i = 0; i < Dim.PRESETS.length; i++) {
            Dim.Preset p = Dim.PRESETS[i];
            boolean sel = solo ? Dim.soloSelected(p, eOn, eLvl)
                    : nOn && nLvl == p.nativeLevel
                    && (p.eclipseLevel == 0 ? !eOn : eOn && eLvl == p.eclipseLevel);
            glyphs[i].setOn(sel);
            presetLabels[i].setTextColor(sel ? Ui.OCHRE : Ui.BONE);
            presetBoxes[i].setBackground(Ui.pressable(this, sel ? Ui.OCHRE : Ui.RULE, Ui.GROUND, 16));
            presetBoxes[i].setSelected(sel);
            presetBoxes[i].setContentDescription(getString(sel ? R.string.preset_desc_on : R.string.preset_desc,
                    getString(p.label)));
        }

        boolean sOn = Scheduler.enabled(this);
        schedSw.setChecked(sOn);
        startBtn.setText(getString(R.string.schedule_from, Scheduler.fmt(Scheduler.start(this))));
        endBtn.setText(getString(R.string.schedule_to, Scheduler.fmt(Scheduler.end(this))));
        nextLine.setText(Scheduler.nextLine(this));
        nextLine.setTextColor(sOn ? Ui.OCHRE : Ui.SLATE);
        binding = false;
    }

    private void tint(SeekBar s, TextView val, boolean on) {
        ColorStateList c = ColorStateList.valueOf(on ? Ui.OCHRE : Ui.SLATE);
        s.setProgressTintList(c);
        s.setThumbTintList(c);
        val.setTextColor(on ? Ui.OCHRE : Ui.SLATE);
    }

    private static int eclipseSub(boolean solo, boolean canOverlay) {
        if (!solo) return canOverlay ? R.string.eclipse_sub : R.string.eclipse_sub_need;
        return canOverlay ? R.string.eclipse_sub_solo : R.string.eclipse_sub_solo_need;
    }

    // ---- lifecycle ------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_ON), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Dim.K_LEVEL), false, observer);
        if (pendingOverlay && Dim.canOverlay(this)) {
            pendingOverlay = false;
            askNotificationsOnce();
            Dim.setEclipse(this, true, true);
        }
        refresh(false);
    }

    @Override
    protected void onPause() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        getContentResolver().unregisterContentObserver(observer);
        // The tile is an active tile: it only shows what changed here once asked to update.
        Dim.refreshTile(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (Prefs.ECL_RUNNING.equals(key) && !adjusting) refresh(true);
    }

    /** SeekBar listener with empty defaults. */
    private abstract static class SeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar s) {}

        @Override
        public void onStopTrackingTouch(SeekBar s) {}
    }
}
