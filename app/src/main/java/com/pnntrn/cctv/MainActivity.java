package com.pnntrn.cctv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
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
    private static final int BG = 0xFF0A101A, PANEL = 0xFF162131, TEXT = 0xFFE6EDF6, MINT = 0xFF62E8BA;
    private final ArrayList<Location> locations = new ArrayList<>();
    private final ArrayList<StreamTile> streams = new ArrayList<>();
    private final ArrayList<View> tileViews = new ArrayList<>();
    private ProfileStore store;
    private Location current;
    private LinearLayout root;
    private int page, focusedCamera, fullscreen = -1, grabbed = -1;
    private boolean arranging, managing, started, storageOkay = true;
    private ArrayList<Camera> originalOrder;
    private Button layoutButton, nextButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        store = new ProfileStore(this);
        try { locations.addAll(store.load()); }
        catch (Exception e) {
            storageOkay = false;
            new AlertDialog.Builder(this).setTitle("Cannot open saved locations")
                    .setMessage("The encrypted settings could not be opened. Existing data has been kept. Restart the app; if the device key was lost, clear app data in Android Settings to start again.")
                    .setPositiveButton("Close", (d, w) -> finish()).setCancelable(false).show();
        }
        String selected = state != null ? state.getString("location", "")
                : getPreferences(0).getString("location", "");
        for (Location loc : locations) if (loc.id.equals(selected)) current = loc;
        if (current == null && !locations.isEmpty()) current = locations.get(0);
        if (state != null) {
            page = state.getInt("page"); focusedCamera = state.getInt("focus");
            fullscreen = state.getInt("fullscreen", -1);
        }
        render();
    }

    @Override protected void onStart() { super.onStart(); started = true; if (storageOkay) render(); }
    @Override protected void onStop() { started = false; releaseStreams(); super.onStop(); }
    @Override protected void onDestroy() { releaseStreams(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("location", current == null ? "" : current.id);
        out.putInt("page", page); out.putInt("focus", focusedCamera); out.putInt("fullscreen", fullscreen);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void releaseStreams() { for (StreamTile stream : streams) stream.release(); streams.clear(); }

    private GradientDrawable box(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(8));
        if (stroke != 0) d.setStroke(dp(3), stroke); return d;
    }
    private void focusStyle(View view) {
        view.setBackground(box(PANEL, 0));
        view.setOnFocusChangeListener((v, has) -> v.setBackground(box(has ? 0xFF243D46 : PANEL, has ? MINT : 0)));
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(12), dp(6), dp(12), dp(6)); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setId(View.generateViewId()); b.setText(label);
        b.setTextSize(14); b.setTextColor(TEXT); b.setAllCaps(false);
        b.setMinWidth(dp(72)); b.setMinimumWidth(dp(72)); b.setMinHeight(dp(44));
        b.setPadding(dp(12), dp(6), dp(12), dp(6)); focusStyle(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void addButton(LinearLayout parent, Button b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44));
        p.setMargins(dp(3), dp(3), dp(3), dp(3)); parent.addView(b, p);
    }
    private void base() {
        releaseStreams(); tileViews.clear();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(12), dp(20), dp(12)); setContentView(root);
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
            new AlertDialog.Builder(this).setTitle("Changes were not saved")
                    .setMessage("Could not write the encrypted settings. Check available storage and restart the app.")
                    .setPositiveButton("OK", (d, w) -> { if (storageOkay) render(); else finish(); }).show();
            return false;
        }
    }

    private void render() {
        if (!storageOkay) return;
        if (managing && current != null) { renderManager(); return; }
        if (fullscreen >= 0 && current != null && fullscreen < current.cameras.size()) { renderFullscreen(); return; }
        fullscreen = -1; base();
        LinearLayout header = row();
        TextView name = text("CCTV TV  /  " + (current == null ? "Welcome" : current.name), 19, TEXT);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, button("Locations", this::locationPicker));
        layoutButton = button(current == null ? "Layout" : current.grid + " tiles", this::layoutPicker);
        addButton(header, layoutButton);
        addButton(header, button(arranging ? "Save order" : "Arrange", this::toggleArrange));
        addButton(header, button("Cameras", () -> {
            if (arranging) { toast("Save or cancel arrangement first"); return; }
            if (current != null) { managing = true; render(); } else editLocation(null);
        }));
        root.addView(header);
        if (current == null || current.cameras.isEmpty()) {
            LinearLayout welcome = new LinearLayout(this); welcome.setOrientation(LinearLayout.VERTICAL); welcome.setGravity(Gravity.CENTER);
            welcome.addView(text(current == null ? "Your cameras. One remote." : "Add your first cameras", 30, TEXT));
            welcome.addView(text("Substreams in the grid  •  Mainstream when you press OK", 16, MINT));
            Button add = button(current == null ? "+ Add location" : "+ Add NVR / camera", () -> {
                if (current == null) editLocation(null); else addCameras();
            });
            welcome.addView(add, new LinearLayout.LayoutParams(dp(260), dp(52)));
            root.addView(welcome, new LinearLayout.LayoutParams(-1, 0, 1)); add.requestFocus();
            return;
        }
        page = CameraRules.clampPage(page, current.cameras.size(), current.grid);
        int columns = (int) Math.sqrt(current.grid);
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int r = 0; r < columns; r++) {
            LinearLayout line = row();
            grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int col = 0; col < columns; col++) {
                int absolute = page * current.grid + r * columns + col;
                FrameLayout tile = new FrameLayout(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
                p.setMargins(dp(3), dp(3), dp(3), dp(3)); line.addView(tile, p);
                if (absolute >= current.cameras.size()) { tile.setBackground(box(0xFF0E1724, 0)); continue; }
                Camera c = current.cameras.get(absolute);
                tile.setId(View.generateViewId()); tile.setFocusable(true); tile.setClickable(true);
                tile.setPadding(dp(3), dp(3), dp(3), dp(3)); tile.setBackground(box(PANEL, 0));
                if (arranging) {
                    TextView label = text((absolute + 1) + "\n" + c.name + (grabbed == absolute ? "\nMoving • arrows then OK" : "\nOK to pick up"), 19, TEXT);
                    label.setGravity(Gravity.CENTER); tile.addView(label, new FrameLayout.LayoutParams(-1, -1));
                    if (grabbed == absolute) tile.setBackground(box(0xFF243D46, MINT));
                } else if (started) {
                    StreamTile stream = new StreamTile(this, c, false, current.tcp);
                    tile.addView(stream, new FrameLayout.LayoutParams(-1, -1)); streams.add(stream); stream.start(tileViews.size() * 120L);
                }
                tile.setOnFocusChangeListener((v, has) -> {
                    v.setBackground(box(has ? 0xFF243D46 : PANEL, has ? MINT : 0));
                    if (has) focusedCamera = absolute;
                });
                tile.setOnHoverListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) v.requestFocus();
                    return false;
                });
                tile.setContentDescription(c.name + (arranging ? ", press OK to move" : ", press OK for mainstream"));
                tile.setOnClickListener(v -> {
                    focusedCamera = absolute;
                    if (arranging) { grabbed = grabbed == absolute ? -1 : absolute; render(); }
                    else { fullscreen = absolute; fullSub = false; render(); }
                });
                tile.setOnLongClickListener(v -> { if (!arranging) cameraOptions(absolute); return true; });
                tile.setOnKeyListener((v, code, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN || grabbed >= 0) return false;
                    int local = absolute - page * current.grid;
                    int target = -1;
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT && local % columns > 0) target = local - 1;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT && local % columns < columns - 1) target = local + 1;
                    if (code == KeyEvent.KEYCODE_DPAD_UP) {
                        if (local < columns) { layoutButton.requestFocus(); return true; }
                        target = local - columns;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        target = local + columns;
                        if (target >= tileViews.size()) { nextButton.requestFocus(); return true; }
                    }
                    if (target >= 0 && target < tileViews.size()) { tileViews.get(target).requestFocus(); return true; }
                    return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                });
                tileViews.add(tile);
            }
        }
        LinearLayout footer = row();
        String hint = arranging ? "OK pick / drop  •  Arrows move  •  Back cancel" : "OK expand  •  Hold OK options  •  Menu cameras";
        footer.addView(text(hint, 12, 0xFFA2B2C9), new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(text((page + 1) + " / " + CameraRules.pageCount(current.cameras.size(), current.grid), 13, MINT));
        addButton(footer, button("‹ Prev", () -> changePage(-1)));
        nextButton = button("Next ›", () -> changePage(1)); addButton(footer, nextButton);
        root.addView(footer);
        int focus = Math.max(0, Math.min(tileViews.size() - 1, focusedCamera - page * current.grid));
        if (!tileViews.isEmpty()) tileViews.get(focus).requestFocus();
    }

    private void changePage(int direction) {
        if (grabbed >= 0) { toast("Drop the camera with OK first"); return; }
        page = Math.floorMod(page + direction, CameraRules.pageCount(current.cameras.size(), current.grid));
        focusedCamera = page * current.grid; render();
    }

    private void renderFullscreen() {
        base(); root.setPadding(0, 0, 0, 0);
        Camera camera = current.cameras.get(fullscreen);
        FrameLayout frame = new FrameLayout(this); root.addView(frame, new LinearLayout.LayoutParams(-1, -1));
        if (started) {
            StreamTile tile = new StreamTile(this, camera, !fullSub, current.tcp);
            streams.add(tile); frame.addView(tile, new FrameLayout.LayoutParams(-1, -1)); tile.start(150);
        }
        TextView help = text("← → camera   •   OK sub / main   •   Back grid", 13, TEXT);
        help.setBackgroundColor(0xB8101825);
        FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        hint.setMargins(dp(16), dp(16), dp(16), 0); frame.addView(help, hint);
        frame.setFocusable(true); frame.requestFocus();
    }

    private boolean fullSub;
    private void toggleFullscreenStream() {
        fullSub = !fullSub; render();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (current != null && fullscreen < 0 && !arranging) { managing = !managing; render(); } return true;
        }
        if (fullscreen >= 0 && !managing) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) toggleFullscreenStream();
                    else {
                        fullscreen = Math.floorMod(fullscreen + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1), current.cameras.size());
                        focusedCamera = fullscreen; fullSub = false; render();
                    }
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
        if (fullscreen >= 0) {
            focusedCamera = fullscreen; page = fullscreen / current.grid; fullscreen = -1; fullSub = false; render();
        } else if (arranging) {
            current.cameras.clear(); current.cameras.addAll(originalOrder); arranging = false; grabbed = -1; render();
        } else if (managing) { managing = false; render(); }
        else new AlertDialog.Builder(this).setTitle("Exit CCTV TV?")
                .setNegativeButton("Keep watching", null).setPositiveButton("Exit", (d, w) -> finish()).show();
    }

    private void toggleArrange() {
        if (current == null || current.cameras.isEmpty()) return;
        if (arranging) { arranging = false; grabbed = -1; persist(); }
        else { arranging = true; grabbed = -1; originalOrder = new ArrayList<>(current.cameras); }
        render();
    }
    private void layoutPicker() {
        if (current == null) return;
        if (arranging) { toast("Save or cancel arrangement first"); return; }
        int[] sizes = {1, 4, 9, 16};
        new AlertDialog.Builder(this).setTitle("Live grid • fewer tiles uses less power")
                .setItems(new String[]{"1 camera", "4 cameras", "9 cameras", "16 cameras"}, (d, w) -> {
                    current.grid = sizes[w]; page = focusedCamera / current.grid; persist(); render();
                }).show();
    }
    private void locationPicker() {
        if (arranging) { toast("Save or cancel arrangement first"); return; }
        String[] names = new String[locations.size() + 1];
        for (int i = 0; i < locations.size(); i++) names[i] = locations.get(i).name;
        names[names.length - 1] = "+ Add location";
        new AlertDialog.Builder(this).setTitle("Locations").setItems(names, (d, which) -> {
            if (which == locations.size()) editLocation(null);
            else { current = locations.get(which); page = 0; focusedCamera = 0; fullscreen = -1; rememberLocation(); render(); }
        }).show();
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(24), dp(8), dp(24), dp(16)); return l;
    }
    private ScrollView scroll(View content) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(content); return s; }
    private EditText field(LinearLayout form, String label, String value, int type) {
        form.addView(text(label, 13, 0xFFACBBD0));
        EditText e = new EditText(this); e.setText(value); e.setTextSize(16); e.setSingleLine(true); e.setInputType(type);
        e.setContentDescription(label); e.setSelectAllOnFocus(true); e.setMinimumHeight(dp(48));
        form.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private static String value(EditText e) { return e.getText().toString().trim(); }
    private AlertDialog dialog(String title, LinearLayout form, String save, Runnable action) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll(form))
                .setNegativeButton("Cancel", null).setPositiveButton(save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { action.run(); dialog.dismiss(); }
            catch (IllegalArgumentException e) { toast(e.getMessage()); }
        }));
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        return dialog;
    }
    private void editLocation(Location location) {
        LinearLayout fields = form();
        EditText name = field(fields, "Location name", location == null ? "" : location.name, InputType.TYPE_CLASS_TEXT);
        dialog(location == null ? "Add location" : "Rename location", fields, "Save", () -> {
            if (value(name).isEmpty()) throw new IllegalArgumentException("Enter a location name");
            if (location == null) { current = new Location(value(name)); locations.add(current); page = 0; focusedCamera = 0; }
            else location.name = value(name);
            if (persist()) { managing = true; render(); }
        });
    }

    private void renderManager() {
        base();
        LinearLayout header = row();
        header.addView(text(current.name + " / Cameras", 23, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        addButton(header, button("‹ Live", () -> { managing = false; render(); }));
        addButton(header, button("+ Add", this::addCameras));
        addButton(header, button("Location", this::locationOptions)); root.addView(header);
        root.addView(text("Camera settings stay on this device. RTSP transport: " + (current.tcp ? "TCP" : "UDP with TCP fallback"), 13, MINT));
        LinearLayout list = form();
        if (current.cameras.isEmpty()) list.addView(text("Add an NVR to create channels together, or add a custom RTSP camera.", 18, TEXT));
        for (int i = 0; i < current.cameras.size(); i++) {
            int index = i; Camera c = current.cameras.get(i);
            LinearLayout line = row();
            TextView title = text((i + 1) + ". " + c.name, 17, TEXT); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
            Button up = button("↑", () -> { CameraRules.move(current.cameras, index, index - 1); persist(); renderManager(); focusManagerCamera(index - 1); });
            up.setContentDescription("Move " + c.name + " up"); up.setEnabled(i > 0); addButton(line, up);
            Button down = button("↓", () -> { CameraRules.move(current.cameras, index, index + 1); persist(); renderManager(); focusManagerCamera(index + 1); });
            down.setContentDescription("Move " + c.name + " down"); down.setEnabled(i < current.cameras.size() - 1); addButton(line, down);
            Button edit = button("Edit", () -> editCamera(c)); edit.setContentDescription("Edit " + c.name); addButton(line, edit);
            addButton(line, button("More", () -> cameraOptions(index)));
            list.addView(line); tileViews.add(edit);
        }
        root.addView(scroll(list), new LinearLayout.LayoutParams(-1, 0, 1));
        if (!tileViews.isEmpty()) tileViews.get(Math.min(focusedCamera, tileViews.size() - 1)).requestFocus();
        else header.getChildAt(2).requestFocus();
    }
    private void focusManagerCamera(int index) {
        focusedCamera = Math.max(0, Math.min(index, tileViews.size() - 1));
        if (!tileViews.isEmpty()) tileViews.get(focusedCamera).requestFocus();
    }
    private void locationOptions() {
        new AlertDialog.Builder(this).setTitle(current.name)
                .setItems(new String[]{"Rename", "Switch TCP / UDP", "Delete location"}, (d, w) -> {
                    if (w == 0) editLocation(current);
                    if (w == 1) { current.tcp = !current.tcp; persist(); render(); }
                    if (w == 2) new AlertDialog.Builder(this).setTitle("Delete " + current.name + "?")
                            .setMessage("This removes all saved cameras in this location.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (x, y) -> {
                                locations.remove(current); current = locations.isEmpty() ? null : locations.get(0);
                                page = 0; focusedCamera = 0; managing = current != null; persist(); render();
                            }).show();
                }).show();
    }
    private void cameraOptions(int index) {
        Camera c = current.cameras.get(index);
        new AlertDialog.Builder(this).setTitle(c.name)
                .setItems(new String[]{"Edit name / RTSP URLs", "Move to position…", "Delete camera"}, (d, w) -> {
                    if (w == 0) editCamera(c);
                    if (w == 1) {
                        LinearLayout f = form(); EditText position = field(f, "Position (1–" + current.cameras.size() + ")", "" + (index + 1), InputType.TYPE_CLASS_NUMBER);
                        dialog("Move " + c.name, f, "Move", () -> {
                            int to = number(position, "Position", 1, current.cameras.size()) - 1;
                            focusedCamera = CameraRules.move(current.cameras, index, to); page = focusedCamera / current.grid;
                            persist(); render();
                        });
                    }
                    if (w == 2) new AlertDialog.Builder(this).setTitle("Delete " + c.name + "?")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (x, y) -> {
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
        new AlertDialog.Builder(this).setTitle("Add cameras")
                .setItems(new String[]{"Hikvision NVR", "VIGI NVR", "VIGI standalone camera", "Custom RTSP camera"}, (d, w) -> {
                    if (w == 3) editCamera(null); else addPreset(CameraRules.Preset.values()[w]);
                }).show();
    }
    private int number(EditText field, String label, int min, int max) {
        try {
            int n = Integer.parseInt(value(field)); if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(label + " must be " + min + "–" + max);
    }
    private void addPreset(CameraRules.Preset preset) {
        LinearLayout f = form();
        EditText prefix = field(f, "Camera name prefix", "Camera", InputType.TYPE_CLASS_TEXT);
        EditText host = field(f, "NVR / camera IP or hostname", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText port = field(f, "RTSP port", "554", InputType.TYPE_CLASS_NUMBER);
        EditText user = field(f, "Username", "admin", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = field(f, "Password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText first = field(f, "First NVR channel", "1", InputType.TYPE_CLASS_NUMBER);
        Spinner count = spinner(f, "Channels to add", new String[]{"1", "4", "8", "9", "16", "24", "32"});
        count.setSelection(4);
        if (preset == CameraRules.Preset.VIGI_CAMERA) { first.setText("1"); first.setEnabled(false); count.setSelection(0); count.setEnabled(false); }
        if (preset == CameraRules.Preset.VIGI_NVR) f.addView(text("Enable the NVR RTSP server. This uses /live/{channel}/{1 or 2}/avm. Older firmware may require an update.", 13, MINT));
        dialog("Add " + preset.name().replace('_', ' '), f, "Add channels", () -> {
            int n = Integer.parseInt(count.getSelectedItem().toString());
            int start = number(first, "First channel", 1, 256);
            int p = number(port, "Port", 1, 65535);
            if (start + n - 1 > 256) throw new IllegalArgumentException("Last channel must be 256 or less");
            if (current.cameras.size() + n > 256) throw new IllegalArgumentException("Maximum 256 cameras per location");
            String prefixValue = value(prefix).isEmpty() ? "Camera" : value(prefix);
            ArrayList<Camera> added = new ArrayList<>();
            for (int i = start; i < start + n; i++) {
                String sub = CameraRules.streamUrl(preset, value(host), p, user.getText().toString(), pass.getText().toString(), i, false);
                String main = CameraRules.streamUrl(preset, value(host), p, user.getText().toString(), pass.getText().toString(), i, true);
                added.add(new Camera(prefixValue + " " + i, sub, main));
            }
            current.cameras.addAll(added); if (persist()) { managing = true; render(); }
        });
    }
    private void editCamera(Camera camera) {
        LinearLayout f = form();
        EditText name = field(f, "Camera name", camera == null ? "Camera " + (current.cameras.size() + 1) : camera.name, InputType.TYPE_CLASS_TEXT);
        // URLs may contain credentials; password variation hides them on a shared TV screen.
        int urlType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText sub = field(f, "Substream RTSP URL (grid)", camera == null ? "" : camera.subUrl, urlType);
        EditText main = field(f, "Mainstream RTSP URL (fullscreen)", camera == null ? "" : camera.mainUrl, urlType);
        dialog(camera == null ? "Add custom camera" : "Edit camera", f, "Save", () -> {
            if (value(name).isEmpty()) throw new IllegalArgumentException("Enter a camera name");
            CameraRules.validateUrl(value(sub)); CameraRules.validateUrl(value(main));
            if (camera == null) {
                if (current.cameras.size() >= 256) throw new IllegalArgumentException("Maximum 256 cameras per location");
                current.cameras.add(new Camera(value(name), value(sub), value(main)));
            } else { camera.name = value(name); camera.subUrl = value(sub); camera.mainUrl = value(main); }
            if (persist()) render();
        });
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
