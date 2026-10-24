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
    private ArrayList<Camera> originalOrder;
    private View viewingBar;
    private android.widget.ImageButton layoutControl, locationControl;
    private java.util.Map<String,Boolean> originalHidden;
    private EditText recorderLocationName;
    private Spinner recorderDefaultGrid;
    private Location previousRecorderLocation;
    private final Runnable leftHold = () -> openHeldShortcut(TvViewingState.Key.LEFT);
    private final Runnable rightHold = () -> openHeldShortcut(TvViewingState.Key.RIGHT);
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
        if(recorderLocationName!=null)recorderDraft.locationName=recorderLocationName.getText().toString();
        if(recorderDefaultGrid!=null)recorderDraft.defaultGrid=GridLayoutSpec.sizes()[recorderDefaultGrid.getSelectedItemPosition()];
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
            if(d.recorderId.isEmpty()){
                previousRecorderLocation=current;current=new Location(d.locationId,d.locationName);
                current.defaultGrid=d.defaultGrid;current.grid=d.defaultGrid;
                draftPreferences().edit().putString(d.key(),encoded).apply();
                managing=true;editNvr(null,CameraRules.Preset.valueOf(d.preset));
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
        else if(!locations.contains(current)){
            prefs.putString("active",recorderDraft.key());
            try{pendingRecorderDraft=recorderDraft.encode();}catch(org.json.JSONException ignored){ }
        }
        prefs.apply(); recorderFields[4].setText("");
        AlertDialog d = recorderEditor; recorderEditor = null; recorderFields = null; recorderDraft = null;
        d.dismiss(); recorderLocationName=null;recorderDefaultGrid=null;restoreStagedLocation(); if (started) render();
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
        if (!visible && viewingBar != null && viewingBar.hasFocus()) return;
        chromeVisible = visible;
        if (viewingBar != null) viewingBar.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        ui.removeCallbacks(menuHold); ui.removeCallbacks(okHold); ui.removeCallbacks(leftHold); ui.removeCallbacks(rightHold);
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
        if (arranging) { restoreArrangement(); restoreViewingSelection(); }
        restoreStagedLocation(); closePairing(); technician = false; managing = false; arranging = false; grabbed = -1;
    }
    private void viewingMenu() {
        if (managing || viewingMenu != null || technicianConfirmation != null) return;
        cancelRemoteHolds();
        TextView hint = text(getString(R.string.tv_viewing_menu_setup_hint), 14, TEXT);
        hint.setPadding(dp(24), dp(8), dp(24), dp(16));
        AlertDialog menu = dialogBuilder().setTitle(getString(R.string.tv_viewing_menu))
                .setView(hint)
                .setItems(new String[]{getString(R.string.tv_camera_grid), getString(R.string.tv_grid_size), getString(R.string.tv_switch_location), getString(R.string.tv_settings)}, (d, which) -> {
                    if (which == 0 && fullscreen >= 0) { page = fullscreen / current.grid; fullscreen = -1; render(); }
                    if (which == 1) layoutPicker();
                    if (which == 2) locationPicker();
                    if (which == 3) confirmTechnician();
                }).create();
        viewingMenu = menu;
        menu.setOnDismissListener(d -> { viewingMenu = null; cancelRemoteHolds(); revealChrome(); });
        menu.show();
    }
    private void saveViewingSoon() {
        if (current == null || arranging || !storageOkay) return;
        current.lastPage = page;
        if (focusedCamera >= 0 && focusedCamera < current.visibleCameras().size()) current.lastCameraId = current.visibleCameras().get(focusedCamera).id;
        viewingSave.removeCallbacksAndMessages(null);
        viewingSave.postDelayed(() -> { if (started && !arranging) persist(); }, 800);
    }
    private void closePairing() {
        if (phone != null) { closeConnectionPreview(); phone.close(); phone = null; }
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
            focusedCamera = TvViewingState.restoreCameraIndex(current.visibleCameras(), current.grid, savedPage, savedCameraId);
            page = focusedCamera / current.grid;
        }
        if (state != null) {
            fullSub = state.getBoolean("fullSub", false);
            if (current != null && !current.visibleCameras().isEmpty() && state.getInt("fullscreen", -1) >= 0)
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
            if (focusedCamera >= 0 && focusedCamera < current.visibleCameras().size()) current.lastCameraId = current.visibleCameras().get(focusedCamera).id;
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
        out.putString("camera", current != null && focusedCamera >= 0 && focusedCamera < current.visibleCameras().size()
                ? current.visibleCameras().get(focusedCamera).id : "");
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
        fullscreenToken++; ui.removeCallbacks(hideChrome); fullTile = null; fullHelp = null; viewingHelp = null; viewingBar = null; layoutControl = null; locationControl = null;
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

    private ArrayList<Camera> gridCameras() {
        return current == null ? new ArrayList<>() : arranging ? current.cameras : current.visibleCameras();
    }
    private int gridSize() { return arranging ? 6 : current == null ? 4 : current.grid; }
    private String[] layoutNames() {
        int[] sizes=GridLayoutSpec.sizes(); String[] names=new String[sizes.length];
        for(int i=0;i<sizes.length;i++) names[i]=sizes[i]==6?getString(R.string.tv_layout_six):getString(R.string.tv_layout_option,sizes[i]);
        return names;
    }
    private void applyLayout(int size) {
        if(current==null||arranging) return;
        boolean keepControlFocus=layoutControl!=null&&layoutControl.hasFocus();
        current.grid=size; page=focusedCamera/size; if(!persist())return; render();
        if(keepControlFocus&&layoutControl!=null)layoutControl.requestFocus();
    }
    private android.widget.ImageButton toolbarIcon(int drawable,String tag,String label,Runnable click) {
        android.widget.ImageButton b=new android.widget.ImageButton(this); b.setId(View.generateViewId()); b.setTag(tag);
        b.setImageResource(drawable); b.setContentDescription(label); b.setTooltipText(label);
        b.setPadding(dp(12),dp(12),dp(12),dp(12)); focusStyle(b); b.setOnClickListener(v->click.run());
        b.setOnFocusChangeListener((v,has)->{v.setBackground(box(has?0xFF24453F:PANEL,has?MINT:0));revealChrome();});
        return b;
    }
    private void addViewingBar(FrameLayout stage) {
        LinearLayout bar=row(); bar.setTag("viewing-toolbar"); bar.setPadding(dp(8),dp(4),dp(8),dp(4)); bar.setBackgroundColor(0xDA101825);
        layoutControl=toolbarIcon(R.drawable.ic_grid_layout,"layout-control",getString(R.string.tv_layout_control,current==null?4:current.grid),()->{
            if(current==null)return; int[] sizes=GridLayoutSpec.sizes(); int at=0;
            for(int i=0;i<sizes.length;i++)if(sizes[i]==current.grid)at=i;
            applyLayout(sizes[(at+1)%sizes.length]);
        });
        layoutControl.setOnLongClickListener(v->{layoutPicker();return true;});
        bar.addView(layoutControl,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView name=text(current==null?getString(R.string.app_name):getString(R.string.tv_page_label,current.name,page+1,CameraRules.pageCount(gridCameras().size(),gridSize())),18,TEXT);
        name.setTag("location-title"); name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        viewingHelp=name; bar.addView(name,new LinearLayout.LayoutParams(0,dp(48),1));
        locationControl=toolbarIcon(R.drawable.ic_location_switch,"location-control",getString(R.string.tv_location_control),this::locationPicker);
        bar.addView(locationControl,new LinearLayout.LayoutParams(dp(48),dp(48)));
        viewingBar=bar; stage.addView(bar,new FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP));
    }
    private void render() {
        if(!storageOkay||recorderEditor!=null)return;
        if(managing&&technician&&!arranging){renderManager();return;}
        ArrayList<Camera> cameras=gridCameras();
        if(fullscreen>=0&&!arranging&&!cameras.isEmpty()){fullscreen=Math.min(fullscreen,cameras.size()-1);renderFullscreen();return;}
        fullscreen=-1; base(); root.setPadding(0,0,0,0);
        if(arranging){
            root.setPadding(dp(16),dp(8),dp(16),dp(8));
            root.addView(text(getString(R.string.tv_manage_channels),22,TEXT));
            root.addView(text(grabbed<0?getString(R.string.tv_channel_edit_hint):getString(R.string.tv_channel_source,cameras.get(grabbed).name),14,MINT));
            LinearLayout actions=row();actions.setTag("channel-actions");
            Button saveOrder=button(getString(R.string.tv_save_arrangement),()->finishArrangement(true));saveOrder.setTag("save-channels");addButton(actions,saveOrder);
            addButton(actions,button(getString(R.string.tv_cancel_arrangement),()->finishArrangement(false)));
            Button hide=button(getString(cameras.get(Math.max(0,Math.min(focusedCamera,cameras.size()-1))).hidden?R.string.tv_show_selected:R.string.tv_hide_selected),this::toggleSelectedHidden);hide.setTag("channel-hide");addButton(actions,hide);
            addButton(actions,button("‹",()->changePage(-1)));addButton(actions,button("›",()->changePage(1)));root.addView(actions);
        }
        FrameLayout stage=new FrameLayout(this);stage.setTag("viewing-stage");
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        if(cameras.isEmpty()){
            TextView welcome=text(current!=null&&!current.cameras.isEmpty()?getString(R.string.tv_all_channels_hidden):getString(R.string.tv_no_cameras_yet_hold_menu_for_3_seconds_to_set_up),22,TEXT);
            welcome.setTag("empty-viewing-target");welcome.setGravity(Gravity.CENTER);stage.addView(welcome,new FrameLayout.LayoutParams(-1,-1));
            welcome.setFocusable(true);welcome.setFocusableInTouchMode(true);
            if(!arranging)addViewingBar(stage);welcome.requestFocus();revealChrome();return;
        }
        int size=gridSize();page=CameraRules.clampPage(page,cameras.size(),size);focusedCamera=Math.max(page*size,Math.min(focusedCamera,Math.min(cameras.size()-1,(page+1)*size-1)));
        CameraGridView grid=new CameraGridView(this,size);grid.setTag("camera-grid");stage.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        for(int slot=0;slot<size;slot++){
            final int absolute=page*size+slot;FrameLayout tile=new FrameLayout(this);grid.addView(tile);
            tile.setBackgroundColor(android.graphics.Color.BLACK);if(absolute>=cameras.size())continue;
            Camera c=cameras.get(absolute);tile.setId(View.generateViewId());tile.setFocusable(true);tile.setFocusableInTouchMode(true);tile.setClickable(true);
            tile.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)v.requestFocus();return false;});
            if(started){StreamTile stream=new StreamTile(this,c,false,current.tcp);tile.addView(stream,new FrameLayout.LayoutParams(-1,-1));streams.add(stream);stream.start(slot*120L);}
            if(arranging){
                TextView state=text((absolute+1)+" • "+c.name+(c.hidden?" • "+getString(R.string.tv_hidden_channel):""),14,c.hidden?0xFFFFCC86:MINT);
                state.setBackgroundColor(0xDA101825);state.setTag("channel-state:"+c.id);tile.addView(state,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
                tile.setAlpha(c.hidden?.65f:1f);
            }
            tile.setOnFocusChangeListener((v,has)->{if(has){focusedCamera=absolute;if(!arranging)saveViewingSoon();else{Button hide=root.findViewWithTag("channel-hide");if(hide!=null)hide.setText(c.hidden?R.string.tv_show_selected:R.string.tv_hide_selected);}}setViewingChrome(chromeVisible);});
            tile.setContentDescription(c.name+getString(R.string.tv_camera_action)+(arranging&&c.hidden?" • "+getString(R.string.tv_hidden_channel):""));
            tile.setOnClickListener(v->{focusedCamera=absolute;if(arranging){if(grabbed<0)grabbed=absolute;else{CameraRules.swap(current.cameras,grabbed,absolute);grabbed=-1;}render();}else{fullscreen=absolute;fullSub=true;render();}});
            tile.setOnLongClickListener(v->{if(arranging){focusedCamera=absolute;toggleSelectedHidden();}return true;});tileViews.add(tile);
        }
        if(!arranging)addViewingBar(stage);
        if(!tileViews.isEmpty())tileViews.get(focusedCamera-page*size).requestFocus();revealChrome();
    }
    private void toggleSelectedHidden(){
        if(!arranging||current==null||focusedCamera<0||focusedCamera>=current.cameras.size())return;
        Camera c=current.cameras.get(focusedCamera);c.hidden=!c.hidden;render();
    }
    private void restoreArrangement(){
        if(!arranging||current==null)return;
        current.cameras.clear();current.cameras.addAll(originalOrder);
        for(Camera c:current.cameras)if(originalHidden.containsKey(c.id))c.hidden=originalHidden.get(c.id);
    }
    private void finishArrangement(boolean save){
        if(!arranging)return;if(!save)restoreArrangement();arranging=false;grabbed=-1;managing=true;
        restoreViewingSelection();
        if(!save||persist())render();
    }
    private void restoreViewingSelection(){
        focusedCamera=TvViewingState.restoreCameraIndex(current.visibleCameras(),current.grid,current.lastPage,current.lastCameraId);page=focusedCamera/current.grid;
    }
    private void changePage(int direction){
        if(grabbed>=0&&!arranging){toast(getString(R.string.tv_drop_the_camera_with_ok_first));return;}
        int size=gridSize();page=Math.floorMod(page+direction,CameraRules.pageCount(gridCameras().size(),size));focusedCamera=page*size;saveViewingSoon();render();
    }
    private void renderFullscreen(){
        base();root.setPadding(0,0,0,0);Camera camera=current.visibleCameras().get(fullscreen);fullSub=true;focusedCamera=fullscreen;saveViewingSoon();
        FrameLayout frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,-1));
        if(started){StreamTile tile=new StreamTile(this,camera,false,current.tcp);fullTile=tile;streams.add(tile);frame.addView(tile,new FrameLayout.LayoutParams(-1,-1));tile.start(150);}
        fullHelp=text(getString(R.string.tv_fullscreen_sub_help),13,TEXT);fullHelp.setBackgroundColor(0xB8101825);frame.addView(fullHelp,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START));
        frame.setFocusable(true);frame.setFocusableInTouchMode(true);frame.requestFocus();addViewingBar(frame);revealChrome();
    }
    private boolean fullSub=true;
    private void toggleFullscreenStream(){revealChrome();if(layoutControl!=null)layoutControl.requestFocus();}

    private void openHeldShortcut(TvViewingState.Key key){
        if(!viewingInputFocused()||arranging)return;
        if(remote.held(key,android.os.SystemClock.uptimeMillis())){if(key==TvViewingState.Key.LEFT)layoutPicker();else locationPicker();}
    }
    private boolean dispatchHorizontal(KeyEvent event){
        boolean left=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT;TvViewingState.Key key=left?TvViewingState.Key.LEFT:TvViewingState.Key.RIGHT;
        Runnable hold=left?leftHold:rightHold;long now=android.os.SystemClock.uptimeMillis();
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){remote.down(key,now);ui.postDelayed(hold,1000);}else openHeldShortcut(key);}
        else if(event.getAction()==KeyEvent.ACTION_UP){ui.removeCallbacks(hold);if(event.isCanceled())remote.cancel(key);else openHeldShortcut(key);if(remote.up(key,now))moveViewing(left?-1:1,0);}
        return true;
    }
    private void focusViewingContent(){
        if(fullscreen>=0&&fullTile!=null){((View)fullTile.getParent()).requestFocus();return;}
        if(!tileViews.isEmpty()){tileViews.get(Math.max(0,Math.min(tileViews.size()-1,focusedCamera-page*gridSize()))).requestFocus();return;}
        View empty=root.findViewWithTag("empty-viewing-target");if(empty!=null)empty.requestFocus();
    }
    private void moveViewing(int dx,int dy){
        if(viewingBar!=null&&viewingBar.hasFocus()){
            if(dy>0)focusViewingContent();
            else if(dx!=0){if(dx<0)layoutControl.requestFocus();else locationControl.requestFocus();}return;
        }
        if(current==null||gridCameras().isEmpty()){if(dy<0&&layoutControl!=null)layoutControl.requestFocus();return;}
        if(fullscreen>=0){if(dx!=0){fullscreen=Math.floorMod(fullscreen+dx,gridCameras().size());focusedCamera=fullscreen;render();}else if(dy<0&&layoutControl!=null)layoutControl.requestFocus();return;}
        int size=gridSize(),local=focusedCamera-page*size;
        if(dy<0&&arranging&&GridLayoutSpec.cells(size).get(local).y==0){View save=root.findViewWithTag("save-channels");if(save!=null)save.requestFocus();return;}
        if(dy<0&&!arranging&&GridLayoutSpec.cells(size).get(local).y==0&&layoutControl!=null){layoutControl.requestFocus();return;}
        int target=TvViewingState.move(focusedCamera,gridCameras().size(),size,dx,dy);focusedCamera=target;
        if(target/size!=page){page=target/size;render();}else if(!tileViews.isEmpty())tileViews.get(target-page*size).requestFocus();saveViewingSoon();
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
            if (event.getRepeatCount() == 0) remote.down(TvViewingState.Key.VIEWING_BACK, now);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (event.isCanceled()) remote.cancel(TvViewingState.Key.VIEWING_BACK);
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
                else if (current != null && !current.visibleCameras().isEmpty()) { fullscreen = focusedCamera; fullSub = true; render(); }
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
                : key == KeyEvent.KEYCODE_MENU ? TvViewingState.Key.MENU : key == KeyEvent.KEYCODE_DPAD_LEFT ? TvViewingState.Key.LEFT : key == KeyEvent.KEYCODE_DPAD_RIGHT ? TvViewingState.Key.RIGHT : null;
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
        if (!managing && !arranging && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)) {
            if(viewingBar!=null&&viewingBar.hasFocus())return super.dispatchKeyEvent(event);
            return dispatchViewingOk(event);
        }
        if(!managing&&!arranging&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT))return dispatchHorizontal(event);
        if(arranging){View actions=root.findViewWithTag("channel-actions");if(actions!=null&&actions.hasFocus())return super.dispatchKeyEvent(event);}
        if((!managing||arranging)&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
            if(event.getAction()==KeyEvent.ACTION_DOWN)moveViewing(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed(){
        if(connectionPreview!=null){closeConnectionPreview();return;}
        if(recorderEditor!=null){askLeaveRecorder();return;}
        if(arranging){finishArrangement(false);return;}
        if(managing){endTechnician();render();return;}
        if(fullscreen>=0){focusedCamera=fullscreen;page=fullscreen/current.grid;fullscreen=-1;fullSub=true;render();return;}
        if(viewingBar!=null&&viewingBar.hasFocus())focusViewingContent();
        revealChrome();
    }
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){if(!managing&&started)revealChrome();return super.dispatchTouchEvent(event);}
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event){if(!managing&&started)revealChrome();return super.onGenericMotionEvent(event);}
    private void toggleArrange(){
        if(!technician||current==null||current.cameras.isEmpty())return;
        if(arranging){finishArrangement(true);return;}
        arranging=true;grabbed=-1;originalOrder=new ArrayList<>(current.cameras);originalHidden=new java.util.HashMap<>();
        for(Camera c:current.cameras)originalHidden.put(c.id,c.hidden);
        focusedCamera=0;page=0;render();
    }

    private void layoutPicker(){
        if(current==null||arranging)return;cancelRemoteHolds();int[] sizes=GridLayoutSpec.sizes();
        dialogBuilder().setTitle(getString(R.string.tv_grid_size)).setItems(layoutNames(),(d,w)->applyLayout(sizes[w])).show();
    }

    private void locationPicker() {
        if (arranging) { toast(getString(R.string.tv_save_or_cancel_arrangement_first)); return; }
        String[] names = new String[locations.size() + (technician ? 1 : 0)];
        for (int i = 0; i < locations.size(); i++) names[i] = locations.get(i).name;
        if (technician) names[names.length - 1] = getString(R.string.tv_add_location);
        dialogBuilder().setTitle(getString(R.string.tv_locations)).setItems(names, (d, which) -> {
            if (technician && which == locations.size()) editLocation(null);
            else { current = locations.get(which);
                focusedCamera = TvViewingState.restoreCameraIndex(current.visibleCameras(), current.grid, current.lastPage, current.lastCameraId);
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
    private void restoreStagedLocation(){
        if(current!=null&&!locations.contains(current))current=previousRecorderLocation!=null?previousRecorderLocation:locations.isEmpty()?null:locations.get(0);
        previousRecorderLocation=null;
    }
    private void editLocation(Location location){
        if(!technician)return;
        if(location!=null){if(!location.devices.isEmpty())editNvr(location.devices.get(0),location.devices.get(0).preset);else addCameras();return;}
        dialogBuilder().setTitle(getString(R.string.tv_add_nvr_location)).setItems(new String[]{"Hikvision NVR","VIGI NVR"},(d,w)->{
            previousRecorderLocation=current;current=new Location("");page=0;focusedCamera=0;editNvr(null,w==0?CameraRules.Preset.HIKVISION:CameraRules.Preset.VIGI_NVR);
        }).show();
    }
    private void renderManager(){
        base();if(!technician){managing=false;render();return;}
        LinearLayout header=row();header.addView(text(current==null?getString(R.string.tv_setup):current.name+getString(R.string.tv_setup_suffix),23,TEXT),new LinearLayout.LayoutParams(0,-2,1));
        addButton(header,button(getString(R.string.tv_live),()->{endTechnician();render();}));root.addView(header);
        LinearLayout tools=row();
        Button primary=button(current==null?getString(R.string.tv_add_location):current.devices.isEmpty()?getString(R.string.tv_add_recorder):getString(R.string.tv_edit_recorder),()->{
            if(current==null)editLocation(null);else if(current.devices.isEmpty())addCameras();else editNvr(current.devices.get(0),current.devices.get(0).preset);
        });primary.setTag("nvr-primary");addButton(tools,primary);
        addButton(tools,button(getString(R.string.tv_location),()->dialogBuilder().setTitle(getString(R.string.tv_locations)).setItems(new String[]{getString(R.string.tv_switch_location),getString(R.string.tv_add_location),getString(R.string.tv_location_settings)},(d,w)->{
            if(w==0)locationPicker();if(w==1)editLocation(null);if(w==2&&current!=null)locationOptions();
        }).show()));
        if(current!=null){
            if(pendingRecorderDraft!=null)addButton(tools,button(getString(R.string.tv_resume_draft),this::restoreRecorderEditor));
            Button channels=button(getString(R.string.tv_manage_channels),this::toggleArrange);channels.setTag("manage-channels");channels.setEnabled(!current.cameras.isEmpty());addButton(tools,channels);
            addButton(tools,button(getString(R.string.tv_advanced),()->dialogBuilder().setTitle(getString(R.string.tv_advanced_setup)).setItems(new String[]{getString(R.string.tv_set_up_from_phone)},(d,w)->startPhoneSetup()).show()));
        }
        addButton(tools,button(getString(R.string.tv_check_for_updates),this::showUpdates));root.addView(tools);
        root.addView(text(getString(R.string.tv_substream_only),15,MINT));
        if(current!=null){
            if(!current.devices.isEmpty()){Nvr n=current.devices.get(0);root.addView(text(getString(R.string.tv_primary_recorder,n.name,n.count),20,TEXT));}
            root.addView(text(getString(R.string.tv_default_grid)+": "+current.defaultGrid,17,TEXT));
            root.addView(text(getString(R.string.tv_transport)+(current.tcp?"TCP":"UDP / TCP"),14,TEXT));
        }
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

    private Spinner spinner(LinearLayout fields, String label, String[] entries) {
        fields.addView(text(label, 13, 0xFFACBBD0)); Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries);
        s.setAdapter(adapter); s.setMinimumHeight(dp(48)); s.setContentDescription(label); fields.addView(s); return s;
    }
    private void addCameras(){
        if(!technician||current==null)return;
        if(!current.devices.isEmpty()){editNvr(current.devices.get(0),current.devices.get(0).preset);return;}
        dialogBuilder().setTitle(getString(R.string.tv_add_recorder)).setItems(new String[]{"Hikvision NVR","VIGI NVR"},(d,w)->editNvr(null,w==0?CameraRules.Preset.HIKVISION:CameraRules.Preset.VIGI_NVR)).show();
    }

    private int number(EditText field, String label, int min, int max) {
        try {
            int n = Integer.parseInt(value(field)); if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(getString(R.string.tv_number_range, label, min, max));
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
        if(existing==null&&!current.devices.isEmpty()){toast(getString(R.string.tv_nvr_required));return;}
        Nvr initial = existing == null ? new Nvr() : existing;
        if (existing == null) { initial.preset = preset; initial.name = preset.name().replace('_', ' '); initial.prefix = getString(R.string.tv_camera_label); }
        RecorderDraft draft = new RecorderDraft();
        draft.locationId = current.id; draft.recorderId = existing == null ? "" : existing.id;
        draft.preset = preset.name(); draft.name = initial.name; draft.host = initial.host;
        draft.port = "" + initial.port; draft.username = initial.username; draft.first = "" + initial.first;
        draft.count = "" + initial.count; draft.prefix = initial.prefix; draft.locationName=current.name;draft.defaultGrid=current.defaultGrid;
        String saved = draftPreferences().getString(draft.key(), null);
        if (saved != null) try { draft = RecorderDraft.decode(saved); } catch (org.json.JSONException ignored) { }
        recorderDraft = draft;
        LinearLayout f = form();
        recorderLocationName=field(f,getString(R.string.tv_location_name),draft.locationName.isEmpty()?current.name:draft.locationName,InputType.TYPE_CLASS_TEXT);
        recorderDefaultGrid=spinner(f,getString(R.string.tv_default_grid),layoutNames());
        int[] available=GridLayoutSpec.sizes();for(int i=0;i<available.length;i++)if(available[i]==draft.defaultGrid)recorderDefaultGrid.setSelection(i);
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
                    else recorderDefaultGrid.requestFocus();
                    return true;
                });
            }
            testButton.setNextFocusUpId(prefix.getId());
            testButton.setOnClickListener(v -> {
                try {
                    captureRecorderDraft();
                    String locationName=value(recorderLocationName);
                    if(locationName.isEmpty())throw new IllegalArgumentException(getString(R.string.tv_empty_location_name));
                    final int selectedDefault=GridLayoutSpec.sizes()[recorderDefaultGrid.getSelectedItemPosition()];
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
                            if(!locations.contains(current)||current.defaultGrid!=selectedDefault)current.grid=selectedDefault;
                            current.name=locationName;current.defaultGrid=selectedDefault;
                            if(!locations.contains(current))locations.add(current);
                            if (persist()) { managing = true; finishRecorderEditor(true); }
                        } catch (IllegalArgumentException e) { toast(validationMessage(e)); }
                    });
                } catch (IllegalArgumentException e) { toast(validationMessage(e)); }
            });
        };
        for (EditText field : new EditText[]{name,host,port,user,first,count,prefix,recorderLocationName}) field.addTextChangedListener(new TextWatcher() {
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

    private void showConnectionTest(Camera camera,Runnable onVerified){
        closeConnectionPreview();releaseStreams();final int generation=++previewGeneration;previewVerified=false;
        StreamCheckState checks=new StreamCheckState();LinearLayout content=form();
        Button sub=button(getString(R.string.tv_test_substream),()->{});sub.setTag("stream-test-sub");content.addView(sub);
        TextView result=text("",16,TEXT);result.setTag("stream-result-sub");content.addView(result);
        FrameLayout video=new FrameLayout(this);content.addView(video,new LinearLayout.LayoutParams(-1,dp(220)));
        content.addView(text(getString(R.string.tv_substream_only),14,MINT));
        AlertDialog test=dialogBuilder().setTitle(getString(R.string.tv_connection_preview)).setView(scroll(content)).setNegativeButton(getString(R.string.tv_back),null)
            .setPositiveButton(onVerified==null?getString(R.string.tv_done):getString(R.string.tv_save_channels),null).create();
        connectionPreview=test;StreamTile[] active={null};
        Runnable begin=()->{
            if(!started||generation!=previewGeneration||connectionPreview!=test||!test.isShowing())return;
            int attempt=checks.begin(StreamCheckState.Stream.SUB);previewVerified=false;
            test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(onVerified==null);
            if(active[0]!=null){active[0].release();streams.remove(active[0]);}video.removeAllViews();
            result.setText(getString(R.string.tv_stream_testing));result.setTextColor(TEXT);
            StreamTile preview=new StreamTile(this,camera,false,current.tcp);active[0]=preview;preview.setAutoRetry(false);streams.add(preview);video.addView(preview,new FrameLayout.LayoutParams(-1,-1));
            preview.setListener(new StreamTile.Listener(){
                public void onFirstFrame(){previewOnUi(()->{if(!started||generation!=previewGeneration||connectionPreview!=test||!checks.verified(attempt))return;
                    previewVerified=checks.canSave();result.setText(getString(R.string.tv_stream_verified)+" • "+preview.detectedVideoCodec());result.setTextColor(MINT);test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(onVerified==null||previewVerified);});}
                public void onFailure(PlaybackFailure reason){previewOnUi(()->{if(!started||generation!=previewGeneration||connectionPreview!=test||!checks.failed(attempt))return;
                    previewVerified=false;result.setText(UiText.playback(MainActivity.this,reason));result.setTextColor(0xFFFFA6A6);test.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(onVerified==null);});}
            });preview.start(0);
        };
        sub.setOnClickListener(v->begin.run());
        test.setOnDismissListener(d->{checks.close();if(active[0]!=null){active[0].release();streams.remove(active[0]);active[0]=null;}if(connectionPreview==test){connectionPreview=null;previewVerified=false;previewGeneration++;}});
        test.setOnKeyListener((d,key,event)->key==KeyEvent.KEYCODE_MENU);test.show();
        test.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(onVerified!=null&&(!started||!checks.canSave()||generation!=previewGeneration))return;test.dismiss();if(onVerified!=null)onVerified.run();});begin.run();
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
            pairingDialog.setOnDismissListener(d -> { opened.close(); if (phone == opened) { closeConnectionPreview(); phone = null; } });
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
            if(!type.equals("HIKVISION")&&!type.equals("VIGI_NVR"))throw new IllegalArgumentException(getString(R.string.tv_nvr_required));
            Nvr old = null; for (Nvr n : current.devices) if (n.id.equals(id)) old = n;
            if(old==null&&!current.devices.isEmpty())throw new IllegalArgumentException(getString(R.string.tv_nvr_required));
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
            if(!input.getString("kind").equals("nvr"))throw new IllegalArgumentException(getString(R.string.tv_nvr_required));
            Nvr incoming=ProfileStore.nvrFromJson(input.getJSONObject("nvr"));
            if(!current.devices.isEmpty()&&!current.devices.get(0).id.equals(incoming.id))throw new IllegalArgumentException(getString(R.string.tv_nvr_required));
            Location target=current;PhoneSetupServer requestedSession=phone;
            showConnectionTest(new Camera(incoming.prefix+" "+incoming.first,incoming.url(incoming.first,false),incoming.url(incoming.first,true)),()->{
                if(!technician||!started||current!=target||(requestedSession!=null&&(phone!=requestedSession||!requestedSession.isActive()))){toast(getString(R.string.tv_pairing_ended));return;}
                try{incoming.apply(target);if(persist())render();}catch(IllegalArgumentException error){toast(validationMessage(error));}
            });
            return new JSONObject().put("pendingTv",true).put("message",getString(R.string.tv_confirm_phone_on_tv));
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
