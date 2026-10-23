package com.pnntrn.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int BG = 0xFF0B111B, PANEL = 0xFF192638, TEXT = 0xFFEEF4FC, MINT = 0xFF65DFCA;
    private final ArrayList<Location> locations = new ArrayList<>();
    private final ArrayList<StreamTile> streams = new ArrayList<>();
    private final ArrayList<View> tileViews = new ArrayList<>();
    private ProfileStore store;
    private Location current;
    private LinearLayout root;
    private int page, focusedCamera, fullscreen = -1, grabbed = -1;
    private boolean arranging, managing, technician, started, storageOkay = true;
    private boolean chromeVisible = true;
    private final java.util.Set<AlertDialog> dialogs = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private TextView viewingHelp;
    private final TvViewingState remote = new TvViewingState();
    private AlertDialog viewingMenu, technicianConfirmation;
    private final Runnable menuHold = () -> {
        if (viewingInputFocused() && remote.menuHeld(android.os.SystemClock.uptimeMillis())) confirmTechnician();
    };
    private final Runnable okHold = () -> {
        if (started && !managing && viewingMenu == null && technicianConfirmation == null && hasWindowFocus()
                && remote.held(TvViewingState.Key.OK, android.os.SystemClock.uptimeMillis())) viewingMenu();
    };
    private final Runnable backHold = () -> {
        if (viewingMenu != null && viewingInputFocused()
                && remote.held(TvViewingState.Key.VIEWING_BACK, android.os.SystemClock.uptimeMillis())) confirmTechnician();
    };
    private ArrayList<Camera> originalOrder;
    private Button layoutButton, nextButton;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler viewingSave = new Handler(Looper.getMainLooper());
    private int fullscreenToken;
    private PhoneSetupServer phone;
    private AlertDialog pairingDialog;
    private String pairingLocation = "";
    private StreamTile fullTile;
    private TextView fullHelp;
    private Toast activeToast;
    private AppUpdater updater;
    private AlertDialog recorderEditor, connectionPreview, draftExitDialog;
    private RecorderDraft recorderDraft;
    private EditText[] recorderFields;
    private TextView recorderSecretHint;
    private String pendingRecorderDraft;
    private int previewGeneration;
    private boolean previewVerified;

    private SharedPreferences draftPreferences() { return getSharedPreferences("recorder-drafts", MODE_PRIVATE); }
    private void captureRecorderDraft() {
        if (recorderDraft == null || recorderFields == null) return;
        recorderDraft.name = recorderFields[0].getText().toString();
        recorderDraft.host = recorderFields[1].getText().toString();
        recorderDraft.port = recorderFields[2].getText().toString();
        recorderDraft.username = recorderFields[3].getText().toString();
        recorderDraft.first = recorderFields[5].getText().toString();
        recorderDraft.count = recorderFields[6].getText().toString();
        recorderDraft.prefix = recorderFields[7].getText().toString();
        try { draftPreferences().edit().putString(recorderDraft.key(), recorderDraft.encode())
                .putString("active", recorderDraft.key()).apply(); } catch (org.json.JSONException ignored) { }
    }
    private void closeConnectionPreview() {
        previewGeneration++; previewVerified = false;
        if (connectionPreview != null) {
            AlertDialog d = connectionPreview; connectionPreview = null;
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); d.dismiss();
        }
    }
    private void protectRecorderOnBackground() {
        captureRecorderDraft(); closeConnectionPreview();
        if (recorderFields != null) {
            recorderFields[4].setText("");
            recorderSecretHint.setText(getString(R.string.tv_password_cleared_for_privacy_re_enter_before_testing_for_an_existing_recorder_blank_keeps_its_saved_password));
        }
    }
    private void restoreRecorderEditor() {
        if (!technician || pendingRecorderDraft == null || recorderEditor != null) return;
        String encoded = pendingRecorderDraft; pendingRecorderDraft = null;
        try {
            RecorderDraft d = RecorderDraft.decode(encoded);
            for (Location l : locations) if (l.id.equals(d.locationId)) {
                Nvr existing = null;
                for (Nvr n : l.devices) if (n.id.equals(d.recorderId)) existing = n;
                if (!d.recorderId.isEmpty() && existing == null) return;
                draftPreferences().edit().putString(d.key(), encoded).apply();
                releaseStreams(); fullscreen = -1; current = l; managing = true; page = 0; render();
                editNvr(existing, CameraRules.Preset.valueOf(d.preset)); return;
            }
        } catch (Exception ignored) { /* Malformed/stale drafts never affect saved profiles. */ }
    }
    private void finishRecorderEditor(boolean discard) {
        if (recorderEditor == null) return;
        captureRecorderDraft(); closeConnectionPreview();
        AlertDialog exit = draftExitDialog; draftExitDialog = null;
        if (exit != null) exit.dismiss();
        SharedPreferences.Editor prefs = draftPreferences().edit().remove("active");
        if (discard) prefs.remove(recorderDraft.key());
        prefs.apply(); recorderFields[4].setText("");
        AlertDialog d = recorderEditor; recorderEditor = null; recorderFields = null; recorderDraft = null;
        d.dismiss(); if (started) render();
    }
    private void askLeaveRecorder() {
        if (draftExitDialog != null && draftExitDialog.isShowing()) return;
        captureRecorderDraft();
        AlertDialog confirmation = dialogBuilder().setTitle(getString(R.string.tv_unsaved_recorder_changes))
                .setMessage(getString(R.string.tv_keep_the_nonsecret_fields_to_continue_later_or_explicitly_discard_them_passwords_are_never_kept_in_a_draft))
                .setNeutralButton(getString(R.string.tv_continue_editing), null)
                .setNegativeButton(getString(R.string.tv_discard), (d, w) -> finishRecorderEditor(true))
                .setPositiveButton(getString(R.string.tv_keep_draft), (d, w) -> finishRecorderEditor(false)).create();
        draftExitDialog = confirmation;
        confirmation.setOnDismissListener(d -> { if (draftExitDialog == confirmation) draftExitDialog = null; });
        confirmation.show();
    }
    private void showUpdates() {
        try { if (updater == null) updater = new AppUpdater(this); updater.show(); }
        catch (Exception e) { toast(getString(R.string.tv_cannot_open_updates_restart_cctv_and_retry)); }
    }
    private final Runnable hideChrome = () -> setViewingChrome(false);
    private void setViewingChrome(boolean visible) {
        chromeVisible = visible;
        for (StreamTile stream : streams) stream.setChromeVisible(visible);
        if (fullHelp != null) fullHelp.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (viewingHelp != null) viewingHelp.setVisibility(visible ? View.VISIBLE : View.GONE);
        for (View tile : tileViews) {
            if (tile instanceof FrameLayout) {
                GradientDrawable border = new GradientDrawable(); border.setColor(android.graphics.Color.TRANSPARENT);
                if (visible && tile.hasFocus()) border.setStroke(dp(3), MINT);
                ((FrameLayout) tile).setForeground(border);
            }
        }
    }
    private void revealChrome() {
        ui.removeCallbacks(hideChrome); setViewingChrome(true);
        if (!managing && !arranging) ui.postDelayed(hideChrome, 4000);
    }
    private boolean viewingInputFocused() {
        if (!started || managing || technicianConfirmation != null) return false;
        return viewingMenu == null ? hasWindowFocus()
                : viewingMenu.isShowing() && viewingMenu.getWindow().getDecorView().hasWindowFocus();
    }
    private void cancelRemoteHolds() {
        ui.removeCallbacks(menuHold); ui.removeCallbacks(okHold); ui.removeCallbacks(backHold);
        remote.cancelHolds();
    }
    private void confirmTechnician() {
        if (technician || technicianConfirmation != null) return;
        cancelRemoteHolds();
        if (viewingMenu != null) viewingMenu.dismiss();
        AlertDialog dialog = dialogBuilder().setTitle(getString(R.string.tv_technician_setup))
                .setMessage(getString(R.string.tv_only_enter_to_change_camera_settings))
                .setNegativeButton(getString(R.string.tv_keep_watching), null)
                .setPositiveButton(getString(R.string.tv_enter_setup), (d, w) -> enterTechnician()).create();
        technicianConfirmation = dialog;
        dialog.setOnDismissListener(d -> { technicianConfirmation = null; cancelRemoteHolds(); });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setFocusableInTouchMode(true);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void enterTechnician() {
        technician = true; fullscreen = -1; managing = true;
        String active = draftPreferences().getString("active", null);
        if (active != null) pendingRecorderDraft = draftPreferences().getString(active, null);
        restoreRecorderEditor(); render();
    }
    private void endTechnician() {
        closeConnectionPreview();
        if (recorderEditor != null) {
            captureRecorderDraft();
            try { pendingRecorderDraft = recorderDraft.encode(); } catch (org.json.JSONException ignored) { }
            recorderFields[4].setText("");
            AlertDialog editor = recorderEditor; recorderEditor = null; recorderFields = null; recorderDraft = null; editor.dismiss();
        }
        if (draftExitDialog != null) { draftExitDialog.dismiss(); draftExitDialog = null; }
        if (arranging) { current.cameras.clear(); current.cameras.addAll(originalOrder); }
        closePairing(); technician = false; managing = false; arranging = false; grabbed = -1;
    }
    private void viewingMenu() {
        if (managing || viewingMenu != null || technicianConfirmation != null) return;
        cancelRemoteHolds();
        TextView hint = text(getString(R.string.tv_viewing_menu_setup_hint), 14, TEXT);
        hint.setPadding(dp(24), dp(8), dp(24), dp(16));
        AlertDialog menu = dialogBuilder().setTitle(getString(R.string.tv_viewing_menu))
                .setView(hint)
                .setItems(new String[]{getString(R.string.tv_camera_grid), getString(R.string.tv_grid_size), getString(R.string.tv_switch_location)}, (d, which) -> {
                    if (which == 0 && fullscreen >= 0) { page = fullscreen / current.grid; fullscreen = -1; render(); }
                    if (which == 1) layoutPicker();
                    if (which == 2) locationPicker();
                }).create();
        viewingMenu = menu;
        menu.setOnDismissListener(d -> { viewingMenu = null; cancelRemoteHolds(); revealChrome(); });
        menu.show();
    }
    private void saveViewingSoon() {
        if (current == null || arranging || !storageOkay) return;
        current.lastPage = page;
        if (focusedCamera >= 0 && focusedCamera < current.cameras.size()) current.lastCameraId = current.cameras.get(focusedCamera).id;
        viewingSave.removeCallbacksAndMessages(null);
        viewingSave.postDelayed(() -> { if (started && !arranging) persist(); }, 800);
    }
    private void closePairing() {
        if (phone != null) { phone.close(); phone = null; }
        if (pairingDialog != null) { AlertDialog d = pairingDialog; pairingDialog = null; d.dismiss(); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        store = new ProfileStore(this);
        try { locations.addAll(store.load()); }
        catch (Exception e) {
            storageOkay = false;
            dialogBuilder().setTitle(getString(R.string.tv_cannot_open_saved_locations))
                    .setMessage(getString(R.string.tv_the_encrypted_settings_could_not_be_opened_existing_data_has_been_kept_restart_the_app_if_the_device_key_was_lost_clear_app_data_in_android_settings_to_start_again))
                    .setPositiveButton(getString(R.string.tv_close), (d, w) -> finish()).setCancelable(false).show();
        }
        String selected = state != null ? state.getString("location", "")
                : getPreferences(0).getString("location", "");
        for (Location loc : locations) if (loc.id.equals(selected)) current = loc;
        if (current == null && !locations.isEmpty()) current = locations.get(0);
        if (current != null) {
            int savedPage = state == null ? current.lastPage : state.getInt("page", current.lastPage);
            String savedCameraId = state == null ? current.lastCameraId : state.getString("camera", current.lastCameraId);
            focusedCamera = TvViewingState.restoreCameraIndex(current.cameras, current.grid, savedPage, savedCameraId);
            page = focusedCamera / current.grid;
        }
        if (state != null) {
            fullSub = state.getBoolean("fullSub", false);
            if (current != null && !current.cameras.isEmpty() && state.getInt("fullscreen", -1) >= 0)
                fullscreen = focusedCamera;
        }
        if (state != null && state.containsKey("update.pending")) {
            try { updater = new AppUpdater(this); updater.restoreState(state); } catch (Exception ignored) { }
        }
        String activeDraft = draftPreferences().getString("active", null);
        pendingRecorderDraft = state != null ? state.getString("recorder.draft") : null;
        if (pendingRecorderDraft == null && activeDraft != null)
            pendingRecorderDraft = draftPreferences().getString(activeDraft, null);
        render();
    }

    @Override protected void onStart() { super.onStart(); started = true; if (storageOkay) { render(); } }
    @Override protected void onResume() { super.onResume(); if (updater != null) updater.resume(); }
    @Override protected void onPause() {
        cancelRemoteHolds();
        protectRecorderOnBackground(); endTechnician();
        for (AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss(); dialogs.clear();
        if (viewingMenu != null) viewingMenu.dismiss();
        if (technicianConfirmation != null) technicianConfirmation.dismiss();
        super.onPause();
    }
    @Override protected void onStop() {
        cancelRemoteHolds();
        protectRecorderOnBackground();
        if (updater != null) updater.stop();
        viewingSave.removeCallbacksAndMessages(null);
        if (storageOkay && current != null && !arranging) {
            current.lastPage = page;
            if (focusedCamera >= 0 && focusedCamera < current.cameras.size()) current.lastCameraId = current.cameras.get(focusedCamera).id;
            try { store.save(locations); } catch (Exception ignored) { }
        }
        started = false; if (activeToast != null) activeToast.cancel(); closePairing(); releaseStreams(); ui.removeCallbacksAndMessages(null); super.onStop();
    }
    @Override protected void onDestroy() {
        cancelRemoteHolds();
        closeConnectionPreview();
        if (draftExitDialog != null) draftExitDialog.dismiss();
        if (recorderEditor != null) { recorderFields[4].setText(""); recorderEditor.dismiss(); recorderEditor = null; }
        if (updater != null) updater.close(); releaseStreams(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersive(); else cancelRemoteHolds();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        captureRecorderDraft();
        if (recorderDraft != null) {
            try { out.putString("recorder.draft", recorderDraft.encode()); } catch (org.json.JSONException ignored) { }
            recorderFields[4].setText("");
            recorderSecretHint.setText(getString(R.string.tv_password_cleared_for_recreation_re_enter_before_testing_blank_keeps_the_saved_password_for_an_existing_recorder));
        }
        super.onSaveInstanceState(out);
        if (updater != null) updater.saveState(out);
        out.putString("location", current == null ? "" : current.id);
        out.putString("camera", current != null && focusedCamera >= 0 && focusedCamera < current.cameras.size()
                ? current.cameras.get(focusedCamera).id : "");
        out.putInt("page", page); out.putInt("fullscreen", fullscreen); out.putBoolean("fullSub", fullSub);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void releaseStreams() {
        fullscreenToken++; ui.removeCallbacks(hideChrome); fullTile = null; fullHelp = null; viewingHelp = null;
        for (StreamTile stream : streams) stream.release(); streams.clear();
    }

    private GradientDrawable box(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12));
        d.setStroke(dp(stroke != 0 ? 3 : 1), stroke != 0 ? stroke : 0xFF2B3B50); return d;
    }
    private void focusStyle(View view) {
        view.setBackground(box(PANEL, 0));
        view.setOnFocusChangeListener((v, has) -> v.setBackground(box(has ? 0xFF24453F : PANEL, has ? MINT : 0)));
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(12), dp(6), dp(12), dp(6)); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setId(View.generateViewId()); b.setText(label);
        b.setTextSize(14); b.setTextColor(TEXT); b.setAllCaps(false);
        b.setMinWidth(dp(72)); b.setMinimumWidth(dp(72)); b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(6), dp(12), dp(6)); focusStyle(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48));
        p.setMargins(dp(3), dp(3), dp(3), dp(3)); parent.addView(b, p);
    }
    private void base() {
        if (activeToast != null) { activeToast.cancel(); activeToast = null; }
        releaseStreams(); tileViews.clear();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(32), dp(20), dp(32), dp(20)); setContentView(root);
    }
    private void rememberLocation() { getPreferences(0).edit().putString("location", current == null ? "" : current.id).apply(); }

    /** Save failure restores the last durable state, avoiding a false 'saved' UI. */
    private boolean persist() {
        try { store.save(locations); rememberLocation(); return true; }
        catch (Exception e) {
            String selected = current == null ? "" : current.id;
            try {
                locations.clear(); locations.addAll(store.load()); current = null;
                for (Location loc : locations) if (loc.id.equals(selected)) current = loc;
                if (current == null && !locations.isEmpty()) current = locations.get(0);
            } catch (Exception ignored) { storageOkay = false; releaseStreams(); }
            dialogBuilder().setTitle(getString(R.string.tv_changes_were_not_saved))
                    .setMessage(getString(R.string.tv_could_not_write_the_encrypted_settings_check_available_storage_and_restart_the_app))
                    .setPositiveButton(getString(R.string.tv_ok), (d, w) -> { if (storageOkay) render(); else finish(); }).show();
            return false;
        }
    }

    private void render() {
        if (!storageOkay || recorderEditor != null) return;
        if (managing && technician) { renderManager(); return; }
        if (fullscreen >= 0 && current != null && fullscreen < current.cameras.size()) { renderFullscreen(); return; }
        fullscreen = -1; base(); root.setPadding(0, 0, 0, 0);
        FrameLayout stage = new FrameLayout(this); stage.setTag("viewing-stage");
        root.addView(stage, new LinearLayout.LayoutParams(-1, -1));
        if (current == null || current.cameras.isEmpty()) {
            TextView welcome = text(getString(R.string.tv_no_cameras_yet_hold_menu_for_3_seconds_to_set_up), 24, TEXT);
            welcome.setGravity(Gravity.CENTER); stage.addView(welcome, new FrameLayout.LayoutParams(-1, -1));
            welcome.setFocusable(true); welcome.requestFocus(); return;
        }
        page = CameraRules.clampPage(page, current.cameras.size(), current.grid);
        int columns = (int) Math.sqrt(current.grid);
        LinearLayout grid = new LinearLayout(this); grid.setTag("camera-grid"); grid.setOrientation(LinearLayout.VERTICAL);
        stage.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        for (int r = 0; r < columns; r++) {
            LinearLayout line = row(); grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int col = 0; col < columns; col++) {
                int absolute = page * current.grid + r * columns + col;
                FrameLayout tile = new FrameLayout(this);
                line.addView(tile, new LinearLayout.LayoutParams(0, -1, 1));
                if (absolute >= current.cameras.size()) { tile.setBackgroundColor(android.graphics.Color.BLACK); continue; }
                Camera c = current.cameras.get(absolute);
                tile.setId(View.generateViewId()); tile.setFocusable(true); tile.setFocusableInTouchMode(true); tile.setClickable(true);
                tile.setBackgroundColor(android.graphics.Color.BLACK);
                if (arranging) {
                    TextView label = text((absolute + 1) + "\n" + c.name, 19, TEXT);
                    label.setGravity(Gravity.CENTER); tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
                } else if (started) {
                    StreamTile stream = new StreamTile(this, c, false, current.tcp);
                    tile.addView(stream, new FrameLayout.LayoutParams(-1, -1)); streams.add(stream); stream.start(tileViews.size() * 120L);
                }
                tile.setOnFocusChangeListener((v, has) -> { if (has) { focusedCamera = absolute; saveViewingSoon(); } setViewingChrome(chromeVisible); });
                tile.setContentDescription(c.name + getString(R.string.tv_camera_action));
                tile.setOnClickListener(v -> { focusedCamera = absolute; fullscreen = absolute; fullSub = false; render(); });
                tile.setOnLongClickListener(v -> true);
                tileViews.add(tile);
            }
        }
        TextView help = text(current.name + "  •  " + (page + 1) + "/" + CameraRules.pageCount(current.cameras.size(), current.grid)
                + getString(R.string.tv_grid_help), 14, TEXT);
        help.setBackgroundColor(0xB8101825); viewingHelp = help;
        stage.addView(help, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        int focus = Math.max(0, Math.min(tileViews.size() - 1, focusedCamera - page * current.grid));
        if (!tileViews.isEmpty()) tileViews.get(focus).requestFocus(); revealChrome();
    }

    private void changePage(int direction) {
        if (grabbed >= 0) { toast(getString(R.string.tv_drop_the_camera_with_ok_first)); return; }
        page = Math.floorMod(page + direction, CameraRules.pageCount(current.cameras.size(), current.grid));
        focusedCamera = page * current.grid; saveViewingSoon(); render();
    }

    private void renderFullscreen() {
        base(); root.setPadding(0, 0, 0, 0);
        Camera camera = current.cameras.get(fullscreen);
        final int token = fullscreenToken;
        focusedCamera = fullscreen; saveViewingSoon();
        FrameLayout frame = new FrameLayout(this); root.addView(frame, new LinearLayout.LayoutParams(-1, -1));
        if (started) {
            StreamTile tile = new StreamTile(this, camera, !fullSub, current.tcp);
            fullTile = tile;
            if (!fullSub) tile.setListener(new StreamTile.Listener() {
                @Override public void onFirstFrame() { }
                @Override public void onFailure(PlaybackFailure reason) {
                    ui.post(() -> {
                        if (started && token == fullscreenToken && fullscreen >= 0 && !fullSub) {
                            fullSub = true; render(); toast(getString(R.string.tv_mainstream_unavailable_switched_to_substream));
                        }
                    });
                }
            });
            streams.add(tile); frame.addView(tile, new FrameLayout.LayoutParams(-1, -1)); tile.start(150);
        }
        TextView help = text(getString(R.string.tv_camera_ok_sub_main_back_grid), 13, TEXT);
        fullHelp = help;
        help.setBackgroundColor(0xB8101825);
        FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hint.setMargins(dp(16), dp(16), dp(16), 0); frame.addView(help, hint);
        frame.setFocusable(true); frame.setFocusableInTouchMode(true); frame.requestFocus(); revealChrome();
    }

    private boolean fullSub;
    private void toggleFullscreenStream() {
        fullSub = !fullSub; render();
    }

    private static TvViewingState.KeyPhase keyPhase(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) return TvViewingState.KeyPhase.UP;
        return event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                ? TvViewingState.KeyPhase.DOWN : TvViewingState.KeyPhase.REPEAT;
    }
    private boolean dispatchMenuKey(KeyEvent event) {
        if (technician) return true;
        long now = android.os.SystemClock.uptimeMillis();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) { remote.menuDown(now); ui.postDelayed(menuHold, TvViewingState.MENU_HOLD_MS); }
            else if (remote.menuHeld(now)) confirmTechnician();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            ui.removeCallbacks(menuHold);
            if (event.isCanceled()) remote.cancelMenu();
            else if (remote.menuHeld(now)) confirmTechnician();
            if (remote.menuUp(now)) viewingMenu();
        }
        return true;
    }
    private boolean dispatchViewingBack(KeyEvent event) {
        long now = android.os.SystemClock.uptimeMillis();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                remote.down(TvViewingState.Key.VIEWING_BACK, now);
                ui.postDelayed(backHold, TvViewingState.BACK_HOLD_MS);
            } else if (remote.held(TvViewingState.Key.VIEWING_BACK, now)) confirmTechnician();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            ui.removeCallbacks(backHold);
            if (event.isCanceled()) remote.cancel(TvViewingState.Key.VIEWING_BACK);
            else if (remote.held(TvViewingState.Key.VIEWING_BACK, now)) confirmTechnician();
            if (remote.up(TvViewingState.Key.VIEWING_BACK, now) && viewingMenu != null) viewingMenu.dismiss();
        }
        return true;
    }
    private boolean dispatchViewingOk(KeyEvent event) {
        long now = android.os.SystemClock.uptimeMillis();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                remote.down(TvViewingState.Key.OK, now);
                ui.postDelayed(okHold, TvViewingState.OK_HOLD_MS);
            } else if (remote.held(TvViewingState.Key.OK, now)) viewingMenu();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            ui.removeCallbacks(okHold);
            if (event.isCanceled()) remote.cancel(TvViewingState.Key.OK);
            else if (remote.held(TvViewingState.Key.OK, now)) viewingMenu();
            if (remote.up(TvViewingState.Key.OK, now)) {
                if (fullscreen >= 0) toggleFullscreenStream();
                else if (current != null && !current.cameras.isEmpty()) { fullscreen = focusedCamera; fullSub = false; render(); }
            }
        }
        return true;
    }
    private boolean dispatchDialogKey(AlertDialog dialog, int key, KeyEvent event) {
        if (dialog == viewingMenu) {
            if (key == KeyEvent.KEYCODE_BACK) return dispatchViewingBack(event);
            if (key == KeyEvent.KEYCODE_MENU) return dispatchMenuKey(event);
        }
        TvViewingState.Key pending = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                ? TvViewingState.Key.OK : key == KeyEvent.KEYCODE_BACK ? TvViewingState.Key.VIEWING_BACK
                : key == KeyEvent.KEYCODE_MENU ? TvViewingState.Key.MENU : null;
        if (pending != null && remote.suppressPending(pending, keyPhase(event))) return true;
        return key == KeyEvent.KEYCODE_MENU;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (recorderEditor != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) return true;
            return super.dispatchKeyEvent(event);
        }
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK && remote.suppressPending(TvViewingState.Key.VIEWING_BACK, keyPhase(event))) return true;
        if (!managing && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_MENU)) revealChrome();
        if (key == KeyEvent.KEYCODE_MENU) return dispatchMenuKey(event);
        if (!managing && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) return dispatchViewingOk(event);
        if (!managing && fullscreen < 0 && current != null && !current.cameras.isEmpty()) {
            int dx = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            int dy = key == KeyEvent.KEYCODE_DPAD_UP ? -1 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
            if (dx != 0 || dy != 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int target = TvViewingState.move(focusedCamera, current.cameras.size(), current.grid, dx, dy);
                    if (target / current.grid != page) { focusedCamera = target; page = target / current.grid; render(); }
                    else tileViews.get(target - page * current.grid).requestFocus();
                    saveViewingSoon();
                }
                return true;
            }
        }
        if (fullscreen >= 0 && !managing) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) revealChrome();
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    fullscreen = Math.floorMod(fullscreen + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1), current.cameras.size());
                    focusedCamera = fullscreen; fullSub = false; render();
                }
                return true;
            }
        }
        if (arranging && grabbed >= 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
            int columns = (int) Math.sqrt(current.grid);
            int code = event.getKeyCode();
            int delta = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                    : code == KeyEvent.KEYCODE_DPAD_UP ? -columns : code == KeyEvent.KEYCODE_DPAD_DOWN ? columns : 0;
            if (delta != 0) {
                int target = grabbed + delta;
                if (target >= 0 && target < current.cameras.size()) {
                    CameraRules.swap(current.cameras, grabbed, target); grabbed = target; focusedCamera = target;
                    page = target / current.grid; render();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (connectionPreview != null) { closeConnectionPreview(); return; }
        if (recorderEditor != null) { askLeaveRecorder(); return; }
        if (fullscreen >= 0) {
            focusedCamera = fullscreen; page = fullscreen / current.grid; fullscreen = -1; fullSub = false; render();
        } else if (arranging) {
            current.cameras.clear(); current.cameras.addAll(originalOrder); arranging = false; grabbed = -1; render();
        } else if (managing) { endTechnician(); render(); }
        else dialogBuilder().setTitle(getString(R.string.tv_exit_cctv))
                .setNegativeButton(getString(R.string.tv_keep_watching), null).setPositiveButton(getString(R.string.tv_exit), (d, w) -> finish()).show();
    }

    private void toggleArrange() {
        if (current == null || current.cameras.isEmpty()) return;
        if (arranging) { arranging = false; grabbed = -1; persist(); }
        else { arranging = true; grabbed = -1; originalOrder = new ArrayList<>(current.cameras); }
        render();
    }
    private void layoutPicker() {
        if (current == null) return;
        if (arranging) { toast(getString(R.string.tv_save_or_cancel_arrangement_first)); return; }
        int[] sizes = {1, 4, 9, 16};
        dialogBuilder().setTitle(getString(R.string.tv_live_grid_fewer_tiles_uses_less_power))
                .setItems(new String[]{getString(R.string.tv_1_camera), getString(R.string.tv_4_cameras), getString(R.string.tv_9_cameras), getString(R.string.tv_16_cameras)}, (d, w) -> {
                    current.grid = sizes[w]; page = focusedCamera / current.grid; persist(); render();
                }).show();
    }
    private void locationPicker() {
        if (arranging) { toast(getString(R.string.tv_save_or_cancel_arrangement_first)); return; }
        String[] names = new String[locations.size() + (technician ? 1 : 0)];
        for (int i = 0; i < locations.size(); i++) names[i] = locations.get(i).name;
        if (technician) names[names.length - 1] = getString(R.string.tv_add_location);
        dialogBuilder().setTitle(getString(R.string.tv_locations)).setItems(names, (d, which) -> {
            if (technician && which == locations.size()) editLocation(null);
            else { current = locations.get(which);
                focusedCamera = TvViewingState.restoreCameraIndex(current.cameras, current.grid, current.lastPage, current.lastCameraId);
                page = focusedCamera / current.grid;
                fullscreen = -1; rememberLocation(); render(); }
        }).show();
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(24), dp(8), dp(24), dp(16)); return l;
    }
    private ScrollView scroll(View content) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(content); return s; }
    private EditText field(LinearLayout form, String label, String value, int type) {
        form.addView(text(label, 13, 0xFFACBBD0));
        EditText e = new EditText(this); e.setText(value); e.setTextSize(16); e.setSingleLine(true); e.setInputType(type);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            e.setSaveEnabled(false); e.setSaveFromParentEnabled(false);
            e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        e.setContentDescription(label); e.setSelectAllOnFocus(true); e.setMinimumHeight(dp(48));
        form.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private static String value(EditText e) { return e.getText().toString().trim(); }
    private AlertDialog dialog(String title, LinearLayout form, String save, Runnable action) {
        AlertDialog dialog = dialogBuilder().setTitle(title).setView(scroll(form))
                .setNegativeButton(getString(R.string.tv_cancel), null).setPositiveButton(save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { action.run(); dialog.dismiss(); }
            catch (IllegalArgumentException e) { toast(validationMessage(e)); }
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return dialog;
    }
    private void editLocation(Location location) {
        LinearLayout fields = form();
        EditText name = field(fields, getString(R.string.tv_location_name), location == null ? "" : location.name, InputType.TYPE_CLASS_TEXT);
        dialog(location == null ? getString(R.string.tv_add_location_label) : getString(R.string.tv_rename_location), fields, getString(R.string.tv_save), () -> {
            if (value(name).isEmpty()) throw new IllegalArgumentException(getString(R.string.tv_enter_a_location_name));
            if (location == null) { current = new Location(value(name)); locations.add(current); page = 0; focusedCamera = 0; }
            else location.name = value(name);
            if (persist()) { managing = true; render(); }
        });
    }

    private void renderManager() {
        base();
        if (!technician) { managing = false; render(); return; }
        LinearLayout header = row();
        header.addView(text(current == null ? getString(R.string.tv_setup) : current.name + getString(R.string.tv_setup_suffix), 23, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, button(getString(R.string.tv_live), () -> { endTechnician(); render(); })); root.addView(header);
        LinearLayout tools = row();
        Button primary = button(current == null ? getString(R.string.tv_add_location) : current.devices.isEmpty() ? getString(R.string.tv_add_recorder) : getString(R.string.tv_edit_recorder), () -> {
            if (current == null) editLocation(null);
            else if (current.devices.isEmpty()) addCameras();
            else editNvr(current.devices.get(0), current.devices.get(0).preset);
        }); addButton(tools, primary);
        addButton(tools, button(getString(R.string.tv_location), () -> dialogBuilder().setTitle(getString(R.string.tv_locations)).setItems(new String[]{getString(R.string.tv_switch_location), getString(R.string.tv_add_location), getString(R.string.tv_location_settings)}, (d, w) -> {
            if (w == 0) locationPicker(); if (w == 1) editLocation(null); if (w == 2 && current != null) locationOptions();
        }).show()));
        if (current != null) {
            addButton(tools, button(getString(R.string.tv_advanced), () -> dialogBuilder().setTitle(getString(R.string.tv_advanced_setup))
                .setItems(new String[]{getString(R.string.tv_recorders), getString(R.string.tv_add_nvr_camera), getString(R.string.tv_set_up_from_phone)}, (d, w) -> {
                    if (w == 0) recorderPicker(); if (w == 1) addCameras(); if (w == 2) startPhoneSetup();
                }).show()));
        }
        addButton(tools, button(getString(R.string.tv_check_for_updates), this::showUpdates)); root.addView(tools);
        if (current == null) { primary.requestFocus(); return; }
        root.addView(text(getString(R.string.tv_transport) + (current.tcp ? "TCP" : "UDP / TCP"), 13, MINT));
        if (!current.devices.isEmpty()) {
            Nvr recorder = current.devices.get(0);
            root.addView(text(getString(R.string.tv_primary_recorder, recorder.name, recorder.count), 18, TEXT));
        }
        LinearLayout list = form();
        if (current.cameras.isEmpty()) list.addView(text(getString(R.string.tv_add_an_nvr_to_create_channels_together_or_add_a_custom_rtsp_camera), 18, TEXT));
        for (int i = 0; i < current.cameras.size(); i++) {
            int index = i; Camera c = current.cameras.get(i);
            LinearLayout line = row();
            TextView title = text((i + 1) + ". " + c.name, 17, TEXT); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
            Button up = button("↑", () -> { CameraRules.move(current.cameras, index, index - 1); persist(); renderManager(); focusManagerCamera(index - 1); });
            up.setContentDescription(getString(R.string.tv_move_camera_up, c.name)); up.setEnabled(i > 0); up.setAlpha(i > 0 ? 1f : .35f); addButton(line, up);
            Button down = button("↓", () -> { CameraRules.move(current.cameras, index, index + 1); persist(); renderManager(); focusManagerCamera(index + 1); });
            down.setContentDescription(getString(R.string.tv_move_camera_down, c.name)); down.setEnabled(i < current.cameras.size() - 1); down.setAlpha(i < current.cameras.size() - 1 ? 1f : .35f); addButton(line, down);
            Button edit = button(getString(R.string.tv_edit), () -> editCamera(c)); edit.setContentDescription(getString(R.string.tv_edit_label) + c.name); addButton(line, edit);
            addButton(line, button(getString(R.string.tv_more), () -> cameraOptions(index)));
            list.addView(line); tileViews.add(edit);
        }
        root.addView(scroll(list), new LinearLayout.LayoutParams(-1, 0, 1));
        primary.requestFocus();
    }
    private void focusManagerCamera(int index) {
        focusedCamera = Math.max(0, Math.min(index, tileViews.size() - 1));
        if (!tileViews.isEmpty()) tileViews.get(focusedCamera).requestFocus();
    }
    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(this) {
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create(); trackDialog(dialog); return dialog;
            }
        };
    }
    private void trackDialog(AlertDialog dialog) {
        dialogs.removeIf(previous -> !previous.isShowing());
        dialogs.add(dialog);
        dialog.setOnKeyListener((d, key, event) -> dispatchDialogKey(dialog, key, event));
        dialog.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(focus -> {
            if (!focus) cancelRemoteHolds();
        });
    }
    private AlertDialog.Builder safeDeleteDialog(String title) {
        return new AlertDialog.Builder(this) {
            @Override public AlertDialog create() {
                AlertDialog result = super.create(); trackDialog(result);
                result.setOnShowListener(d -> {
                    Button cancel = result.getButton(AlertDialog.BUTTON_NEGATIVE);
                    cancel.setFocusableInTouchMode(true); cancel.requestFocus();
                }); return result;
            }
        }.setTitle(title);
    }
    private void locationOptions() {
        dialogBuilder().setTitle(current.name)
                .setItems(new String[]{getString(R.string.tv_rename), getString(R.string.tv_switch_tcp_udp), getString(R.string.tv_delete_location)}, (d, w) -> {
                    if (w == 0) editLocation(current);
                    if (w == 1) { current.tcp = !current.tcp; persist(); render(); }
                    if (w == 2) safeDeleteDialog(getString(R.string.tv_delete) + current.name + "?")
                            .setMessage(getString(R.string.tv_this_removes_all_saved_cameras_in_this_location))
                            .setNegativeButton(getString(R.string.tv_cancel), null).setPositiveButton(getString(R.string.tv_delete_label), (x, y) -> {
                                locations.remove(current); current = locations.isEmpty() ? null : locations.get(0);
                                page = 0; focusedCamera = 0; managing = true; persist(); render();
                            }).show();
                }).show();
    }
    private void cameraOptions(int index) {
        if (!technician) return;
        Camera c = current.cameras.get(index);
        dialogBuilder().setTitle(c.name)
                .setItems(new String[]{getString(R.string.tv_edit_name_rtsp_urls), getString(R.string.tv_move_to_position), getString(R.string.tv_delete_camera)}, (d, w) -> {
                    if (w == 0) editCamera(c);
                    if (w == 1) {
                        LinearLayout f = form(); EditText position = field(f, getString(R.string.tv_position_1) + current.cameras.size() + ")", "" + (index + 1), InputType.TYPE_CLASS_NUMBER);
                        dialog(getString(R.string.tv_move) + c.name, f, getString(R.string.tv_move_label), () -> {
                            int to = number(position, getString(R.string.tv_position), 1, current.cameras.size()) - 1;
                            focusedCamera = CameraRules.move(current.cameras, index, to); page = focusedCamera / current.grid;
                            persist(); render();
                        });
                    }
                    if (w == 2) safeDeleteDialog(getString(R.string.tv_delete) + c.name + "?")
                            .setNegativeButton(getString(R.string.tv_cancel), null).setPositiveButton(getString(R.string.tv_delete_label), (x, y) -> {
                                current.cameras.remove(c); focusedCamera = Math.max(0, index - 1); persist(); render();
                            }).show();
                }).show();
    }

    private Spinner spinner(LinearLayout fields, String label, String[] entries) {
        fields.addView(text(label, 13, 0xFFACBBD0)); Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        s.setAdapter(adapter); s.setMinimumHeight(dp(48)); s.setContentDescription(label); fields.addView(s); return s;
    }
    private void addCameras() {
        dialogBuilder().setTitle(getString(R.string.tv_add_cameras))
                .setItems(new String[]{"Hikvision NVR", "VIGI NVR", getString(R.string.tv_vigi_standalone_camera), getString(R.string.tv_custom_rtsp_camera)}, (d, w) -> {
                    if (w == 3) editCamera(null); else editNvr(null, CameraRules.Preset.values()[w]);
                }).show();
    }
    private int number(EditText field, String label, int min, int max) {
        try {
            int n = Integer.parseInt(value(field)); if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(getString(R.string.tv_number_range, label, min, max));
    }
    private void editCamera(Camera camera) {
        LinearLayout f = form();
        EditText name = field(f, getString(R.string.tv_camera_name), camera == null ? getString(R.string.tv_camera) + (current.cameras.size() + 1) : camera.name, InputType.TYPE_CLASS_TEXT);
        // URLs may contain credentials; password variation hides them on a shared TV screen.
        int urlType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText sub = field(f, getString(R.string.tv_substream_rtsp_url_grid), camera == null ? "" : camera.subUrl, urlType);
        EditText main = field(f, getString(R.string.tv_mainstream_rtsp_url_fullscreen), camera == null ? "" : camera.mainUrl, urlType);
        addButton(f, button(getString(R.string.tv_test_substream_on_tv), () -> {
            try {
                CameraRules.validateUrl(value(sub)); CameraRules.validateUrl(value(main));
                showConnectionTest(new Camera(value(name), value(sub), value(main)), null);
            } catch (IllegalArgumentException e) { toast(validationMessage(e)); }
        }));
        addButton(f, button(getString(R.string.tv_show_hide_urls), () -> {
            boolean hidden = sub.getTransformationMethod() != null;
            sub.setTransformationMethod(hidden ? null : android.text.method.PasswordTransformationMethod.getInstance());
            main.setTransformationMethod(hidden ? null : android.text.method.PasswordTransformationMethod.getInstance());
        }));
        dialog(camera == null ? getString(R.string.tv_add_custom_camera) : getString(R.string.tv_edit_camera), f, getString(R.string.tv_save), () -> {
            if (value(name).isEmpty()) throw new IllegalArgumentException(getString(R.string.tv_enter_a_camera_name));
            CameraRules.validateUrl(value(sub)); CameraRules.validateUrl(value(main));
            if (camera == null) {
                if (current.cameras.size() >= 256) throw new IllegalArgumentException(getString(R.string.tv_maximum_256_cameras_per_location));
                current.cameras.add(new Camera(value(name), value(sub), value(main)));
            } else {
                if (!camera.subUrl.equals(value(sub)) || !camera.mainUrl.equals(value(main))) camera.deviceId = "";
                camera.name = value(name); camera.subUrl = value(sub); camera.mainUrl = value(main);
            }
            if (persist()) render();
        });
    }
    private void recorderPicker() {
        String[] names = new String[current.devices.size() + 1];
        for (int i = 0; i < current.devices.size(); i++) names[i] = current.devices.get(i).name;
        names[names.length - 1] = getString(R.string.tv_add_recorder_label);
        dialogBuilder().setTitle(getString(R.string.tv_shared_recorder_settings)).setItems(names, (d, which) -> {
            if (which == current.devices.size()) addCameras();
            else editNvr(current.devices.get(which), current.devices.get(which).preset);
        }).show();
    }

    // Keep raw edits (including pasted nonnumeric text) while offering a numeric keyboard.
    // Only Test & save validates and parses; draft capture/restoration never normalizes text.
    private EditText recorderNumberField(LinearLayout form, String label, String raw) {
        EditText field = field(form, label, raw, InputType.TYPE_CLASS_TEXT);
        field.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    private void editNvr(Nvr existing, CameraRules.Preset preset) {
        if (!technician || recorderEditor != null || current == null) return;
        Nvr initial = existing == null ? new Nvr() : existing;
        if (existing == null) { initial.preset = preset; initial.name = preset.name().replace('_', ' '); initial.prefix = getString(R.string.tv_camera_label); }
        RecorderDraft draft = new RecorderDraft();
        draft.locationId = current.id; draft.recorderId = existing == null ? "" : existing.id;
        draft.preset = preset.name(); draft.name = initial.name; draft.host = initial.host;
        draft.port = "" + initial.port; draft.username = initial.username; draft.first = "" + initial.first;
        draft.count = "" + initial.count; draft.prefix = initial.prefix;
        String saved = draftPreferences().getString(draft.key(), null);
        if (saved != null) try { draft = RecorderDraft.decode(saved); } catch (org.json.JSONException ignored) { }
        recorderDraft = draft;
        LinearLayout f = form();
        f.addView(text(getString(R.string.tv_scrolls_through_fields_back_keep_draft_discard_password_must_be_re_entered_after_leaving_the_app), 13, MINT));
        EditText name = field(f, getString(R.string.tv_recorder_name), draft.name, InputType.TYPE_CLASS_TEXT);
        EditText host = field(f, getString(R.string.tv_ip_address_or_hostname), draft.host, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText port = recorderNumberField(f, getString(R.string.tv_rtsp_port), draft.port);
        EditText user = field(f, getString(R.string.tv_username), draft.username, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = field(f, getString(R.string.tv_password), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        recorderSecretHint = text(existing == null ? getString(R.string.tv_enter_password_before_testing_passwords_are_never_kept_in_drafts)
                : getString(R.string.tv_blank_password_keeps_the_existing_saved_credential_enter_a_password_to_replace_it), 13, MINT);
        f.addView(recorderSecretHint);
        EditText first = recorderNumberField(f, getString(R.string.tv_first_channel), draft.first);
        EditText count = recorderNumberField(f, getString(R.string.tv_channel_count), draft.count);
        EditText prefix = field(f, getString(R.string.tv_new_camera_name_prefix), draft.prefix, InputType.TYPE_CLASS_TEXT);
        if (preset == CameraRules.Preset.VIGI_CAMERA) { count.setText("1"); count.setEnabled(false); first.setText("1"); first.setEnabled(false); }
        recorderFields = new EditText[]{name, host, port, user, pass, first, count, prefix};
        f.addView(text(getString(R.string.tv_moves_between_fields_and_scrolls_test_save_is_below_back_offers_keep_draft_or_discard_preview_back_returns_here), 13, MINT));
        AlertDialog editor = dialogBuilder().setTitle(existing == null ? getString(R.string.tv_add_recorder) : getString(R.string.tv_edit_label) + existing.name)
                .setView(scroll(f)).setNegativeButton(getString(R.string.tv_cancel), null).setPositiveButton(getString(R.string.tv_test_save), null).create();
        recorderEditor = editor; editor.setCancelable(false); editor.setCanceledOnTouchOutside(false);
        editor.setOnCancelListener(d -> askLeaveRecorder());
        editor.setOnKeyListener((d, code, event) -> {
            if (code == KeyEvent.KEYCODE_MENU) return true;
            if (code == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) { askLeaveRecorder(); return true; }
            return code == KeyEvent.KEYCODE_BACK;
        });
        Runnable bindEditor = () -> {
            editor.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> askLeaveRecorder());
            Button testButton = editor.getButton(AlertDialog.BUTTON_POSITIVE);
            for (int i = 0; i < recorderFields.length; i++) {
                final int index = i; EditText field = recorderFields[i]; field.setId(View.generateViewId());
                field.setOnKeyListener((v, code, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN)) return false;
                    int target = index + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
                    while (target >= 0 && target < recorderFields.length && !recorderFields[target].isEnabled()) target += code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1;
                    if (target >= recorderFields.length) testButton.requestFocus();
                    else if (target >= 0) recorderFields[target].requestFocus();
                    return true;
                });
            }
            testButton.setNextFocusUpId(prefix.getId());
            testButton.setOnClickListener(v -> {
                try {
                    captureRecorderDraft();
                    Nvr candidate = new Nvr(); candidate.id = initial.id; candidate.preset = preset;
                    candidate.name = value(name); candidate.host = value(host); candidate.port = number(port, getString(R.string.tv_port), 1, 65535);
                    candidate.username = user.getText().toString();
                    candidate.password = pass.getText().toString();
                    if (candidate.password.isEmpty() && existing != null) candidate.password = initial.password;
                    candidate.first = number(first, getString(R.string.tv_first_channel), 1, 256); candidate.count = number(count, getString(R.string.tv_channel_count), 1, 256);
                    candidate.prefix = value(prefix).isEmpty() ? getString(R.string.tv_camera_label) : value(prefix); candidate.validate();
                    showConnectionTest(new Camera(candidate.prefix + " " + candidate.first, candidate.url(candidate.first, false), candidate.url(candidate.first, true)), () -> {
                        try {
                            candidate.apply(current);
                            if (persist()) { managing = true; finishRecorderEditor(true); }
                        } catch (IllegalArgumentException e) { toast(validationMessage(e)); }
                    });
                } catch (IllegalArgumentException e) { toast(validationMessage(e)); }
            });
        };
        for (EditText field : recorderFields) if (field != pass) field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { captureRecorderDraft(); }
            public void afterTextChanged(Editable e) { }
        });
        editor.show(); bindEditor.run(); captureRecorderDraft();
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void previewOnUi(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run(); else ui.post(action);
    }

    private void showConnectionTest(Camera camera, Runnable onVerified) {
        closeConnectionPreview(); releaseStreams();
        final int generation = ++previewGeneration; previewVerified = false;
        LinearLayout content = form();
        TextView outcome = text(getString(R.string.tv_checking_substream), 15, MINT); content.addView(outcome);
        StreamTile preview = new StreamTile(this, camera, false, current.tcp);
        preview.setAutoRetry(false); streams.add(preview);
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(220)));
        AlertDialog test = dialogBuilder().setTitle(getString(R.string.tv_connection_preview))
                .setView(content).setNegativeButton(getString(R.string.tv_back), null)
                .setPositiveButton(onVerified == null ? getString(R.string.tv_done) : getString(R.string.tv_save_channels), null).create();
        connectionPreview = test;
        final boolean[] failed = {false};
        preview.setListener(new StreamTile.Listener() {
            @Override public void onFirstFrame() {
                previewOnUi(() -> {
                    if (!started || failed[0] || generation != previewGeneration || connectionPreview != test || !test.isShowing()) return;
                    previewVerified = true; outcome.setText(getString(R.string.tv_picture_verified_substream_is_playing));
                    test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                });
            }
            @Override public void onFailure(PlaybackFailure reason) {
                previewOnUi(() -> {
                    if (generation != previewGeneration || connectionPreview != test || !test.isShowing()) return;
                    failed[0] = true; previewVerified = false; outcome.setText(UiText.translate(MainActivity.this, reason.message));
                    test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(onVerified == null);
                });
            }
        });
        Runnable bindPreview = () -> {
            test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(onVerified == null);
            test.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (onVerified != null && (!started || !previewVerified || generation != previewGeneration)) return;
                test.dismiss(); if (onVerified != null) onVerified.run();
            });
            preview.start(0);
        };
        test.setOnDismissListener(d -> {
            preview.release(); streams.remove(preview);
            if (connectionPreview == test) { connectionPreview = null; previewVerified = false; previewGeneration++; }
        });
        test.setOnKeyListener((d, code, event) -> code == KeyEvent.KEYCODE_MENU);
        test.show(); bindPreview.run();
    }

    private void startPhoneSetup() {
        if (!technician || current == null || !started) return;
        closePairing(); pairingLocation = current.id;
        try {
            String address = PhoneSetupServer.localAddress();
            String html;
            try (java.io.InputStream in = getResources().openRawResource(R.raw.phone_setup)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; for (int n; (n = in.read(buffer)) != -1;) bytes.write(buffer, 0, n);
                html = bytes.toString("UTF-8");
            }
            phone = new PhoneSetupServer(address, html, (action, input) -> {
                FutureTask<JSONObject> task = new FutureTask<>(() -> {
                    if (!technician || !started || phone == null || !phone.isActive() || current == null || !current.id.equals(pairingLocation))
                        throw new IllegalStateException(getString(R.string.tv_pairing_ended));
                    return phoneAction(action, input);
                });
                ui.post(task);
                try { return task.get(8, TimeUnit.SECONDS); }
                catch (Exception e) { task.cancel(false); throw e; }
            });
            LinearLayout content = form();
            content.addView(text(getString(R.string.tv_open_on_your_phone), 15, 0xFFACBBD0));
            content.addView(text(phone.address(), 23, TEXT));
            content.addView(text(getString(R.string.tv_pairing_code) + phone.session.pin, 30, MINT));
            content.addView(text(getString(R.string.tv_same_trusted_wi_fi_lan_expires_in_10_minutes_closing_this_window_stops_setup_camera_passwords_travel_over_your_local_network_so_use_a_trusted_network), 14, TEXT));
            pairingDialog = dialogBuilder().setTitle(getString(R.string.tv_set_up_from_phone))
                    .setView(content).setNegativeButton(getString(R.string.tv_close_pairing), null).create();
            PhoneSetupServer opened = phone;
            pairingDialog.setOnDismissListener(d -> { opened.close(); if (phone == opened) phone = null; });
            pairingDialog.show();
            ui.postDelayed(() -> { if (phone == opened) { closePairing(); toast(getString(R.string.tv_phone_setup_session_ended)); } }, 600000);
        } catch (Exception e) { closePairing(); toast(getString(R.string.tv_no_local_network_address_connect_the_tv_to_your_lan_or_wi_fi)); }
    }

    private JSONObject phoneAction(String action, JSONObject input) throws Exception {
        if (action.equals("config")) {
            JSONArray devices = new JSONArray(), cameras = new JSONArray();
            for (Nvr n : current.devices) devices.put(ProfileStore.nvrJson(n, false));
            for (Camera c : current.cameras) if (c.deviceId.isEmpty()) cameras.put(new JSONObject().put("id", c.id).put("name", c.name));
            return new JSONObject().put("location", current.name).put("devices", devices).put("cameras", cameras);
        }
        if (action.equals("prepare")) {
            String id = input.optString("id"), type = input.optString("preset");
            if (type.equals("CUSTOM")) {
                Camera existing = null;
                for (Camera c : current.cameras) if (("camera:" + c.id).equals(id)) existing = c;
                String sub = input.optString("sub").trim(), main = input.optString("main").trim();
                if (existing != null) { if (sub.isEmpty()) sub = existing.subUrl; if (main.isEmpty()) main = existing.mainUrl; }
                CameraRules.validateUrl(sub); CameraRules.validateUrl(main);
                if (input.optString("name").trim().isEmpty()) throw new IllegalArgumentException();
                return new JSONObject().put("kind", "camera").put("id", existing == null ? "" : existing.id)
                        .put("name", input.getString("name").trim()).put("sub", sub).put("main", main);
            }
            Nvr old = null; for (Nvr n : current.devices) if (n.id.equals(id)) old = n;
            Nvr n = new Nvr(); if (old != null) n.id = old.id;
            n.name = input.getString("name").trim(); n.host = input.getString("host").trim(); n.preset = CameraRules.Preset.valueOf(type);
            n.port = Integer.parseInt(input.getString("port")); n.first = Integer.parseInt(input.getString("first"));
            n.count = n.preset == CameraRules.Preset.VIGI_CAMERA ? 1 : Integer.parseInt(input.getString("count"));
            n.username = input.optString("username"); n.password = input.optString("password");
            if (old != null && n.password.isEmpty()) n.password = old.password;
            n.prefix = input.optString("prefix", getString(R.string.tv_camera_label)).trim(); if (n.prefix.isEmpty()) n.prefix = getString(R.string.tv_camera_label);
            n.validate(); return new JSONObject().put("kind", "nvr").put("nvr", ProfileStore.nvrJson(n, true));
        }
        if (action.equals("save")) {
            if (input.getString("kind").equals("nvr")) ProfileStore.nvrFromJson(input.getJSONObject("nvr")).apply(current);
            else {
                Camera found = null;
                for (Camera c : current.cameras) if (c.id.equals(input.optString("id"))) found = c;
                CameraRules.validateUrl(input.getString("sub")); CameraRules.validateUrl(input.getString("main"));
                if (found == null) {
                    if (current.cameras.size() >= 256) throw new IllegalArgumentException();
                    current.cameras.add(new Camera(input.getString("name"), input.getString("sub"), input.getString("main")));
                } else { found.name = input.getString("name"); found.subUrl = input.getString("sub"); found.mainUrl = input.getString("main"); found.deviceId = ""; }
            }
            if (!persist()) throw new IllegalStateException();
            render(); return new JSONObject().put("message", getString(R.string.tv_saved_on_tv_you_can_close_pairing_and_start_watching));
        }
        throw new IllegalArgumentException();
    }

    private String validationMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        return message != null && message.matches(".*[\\u0E00-\\u0E7F].*") ? message : UiText.translate(this, message);
    }
    private void toast(String message) {
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(this, message, Toast.LENGTH_LONG); activeToast.show();
    }
}
