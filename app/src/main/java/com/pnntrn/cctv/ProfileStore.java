package com.pnntrn.cctv;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local, atomic, encrypted profiles. Android backups are disabled in the manifest. */
public final class ProfileStore {
    private static final String ALIAS = "cctv-tv-profiles-v1";
    private final AtomicFile file;

    public ProfileStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "locations.enc"));
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    public ArrayList<Location> load() throws Exception {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return new ArrayList<>();
        byte[] data = file.readFully();
        if (data.length < 29 || data[0] != 1) throw new IllegalStateException("Unsupported profile file");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, data, 1, 12));
        String json = new String(cipher.doFinal(data, 13, data.length - 13), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);
        if (root.getInt("version") != 1) throw new IllegalStateException("Unsupported profile schema");
        JSONArray array = root.getJSONArray("locations");
        ArrayList<Location> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Location loc = new Location(obj.getString("id"), obj.getString("name"));
            loc.grid = CameraRules.checkedGrid(obj.optInt("grid", 4));
            loc.tcp = obj.optBoolean("tcp", true);
            JSONArray cams = obj.getJSONArray("cameras");
            for (int j = 0; j < cams.length(); j++) {
                JSONObject c = cams.getJSONObject(j);
                CameraRules.validateUrl(c.getString("sub"));
                CameraRules.validateUrl(c.getString("main"));
                loc.cameras.add(new Camera(c.getString("id"), c.getString("name"), c.getString("sub"), c.getString("main")));
            }
            result.add(loc);
        }
        return result;
    }

    public void save(List<Location> locations) throws Exception {
        JSONArray array = new JSONArray();
        for (Location loc : locations) {
            JSONArray cams = new JSONArray();
            for (Camera c : loc.cameras) cams.put(new JSONObject().put("id", c.id).put("name", c.name)
                    .put("sub", c.subUrl).put("main", c.mainUrl));
            array.put(new JSONObject().put("id", loc.id).put("name", loc.name).put("grid", loc.grid)
                    .put("tcp", loc.tcp).put("cameras", cams));
        }
        byte[] plain = new JSONObject().put("version", 1).put("locations", array).toString().getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(plain);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(1);
            output.write(cipher.getIV());
            output.write(encrypted);
            file.finishWrite(output);
        } catch (Exception e) {
            if (output != null) file.failWrite(output);
            throw e;
        }
    }
}
