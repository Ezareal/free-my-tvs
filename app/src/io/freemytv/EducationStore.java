package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** File locking is intentional: the home UI and accessibility service use different processes. */
final class EducationStore {
    interface Change { void apply(JSONObject state) throws Exception; }
    static synchronized JSONObject access(Context c, Change change) {
        try (RandomAccessFile lock = new RandomAccessFile(new File(c.getFilesDir(), "education.lock"), "rw");
             FileLock ignored = lock.getChannel().lock()) {
            AtomicFile file = new AtomicFile(new File(c.getFilesDir(), "education.json"));
            JSONObject s;
            try { s = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8)); }
            catch (java.io.FileNotFoundException e) { s = new JSONObject(); }
            if (change != null) {
                change.apply(s);
                FileOutputStream out = file.startWrite();
                try { out.write(s.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
                catch (Exception e) { file.failWrite(out); throw e; }
            }
            return s;
        } catch (Exception e) {
            // Corrupt or inaccessible state must not silently expose the adult launcher.
            JSONObject locked = new JSONObject();
            try { locked.put("kids", true).put("error", true); } catch (Exception ignored) {}
            return locked;
        }
    }
    static JSONObject read(Context c) { return access(c, null); }
    static boolean kids(Context c) { return read(c).optBoolean("kids"); }
    static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    static void day(JSONObject s) throws Exception {
        String date = today();
        // Rolling the clock backwards cannot grant a fresh allowance.
        if (date.compareTo(s.optString("day")) > 0) {
            s.put("day", date).put("used", 0L).put("rewards", 0).put("extraTotal",0);
            s.remove("usage");s.remove("extra");s.remove("challengeKey");
        }
    }
    static long remaining(JSONObject s) { return EducationBudgets.ownRemaining(s,EducationBudgets.TOTAL); }
    static boolean guardReady(JSONObject s) {
        long age = SystemClock.elapsedRealtime() - s.optLong("heartbeat", -10000);
        return age >= 0 && age < 3500 && s.optBoolean("guard");
    }
    static boolean allowed(JSONObject s, String pkg) {
        return !pkg.isEmpty() && (pkg.equals(s.optString("youtube")) || pkg.equals(s.optString("abc")));
    }
    static void open(Context c) {
        c.startActivity(new Intent(c, EducationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
    static byte[] hash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    static boolean savePin(Context c, String pin) throws Exception {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        String encodedHash = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP);
        return !access(c, s -> s.put("salt", encodedSalt).put("pin", encodedHash).put("fails", 0).put("retry", 0)).optBoolean("error");
    }
    static boolean checkPin(Context c, String pin) throws Exception {
        JSONObject before = read(c);
        if (System.currentTimeMillis() < before.optLong("retry")) return false;
        boolean correct = MessageDigest.isEqual(hash(pin, Base64.decode(before.getString("salt"), Base64.NO_WRAP)),
                Base64.decode(before.getString("pin"), Base64.NO_WRAP));
        access(c, s -> {
            int attempts = correct ? 0 : s.optInt("fails") + 1;
            s.put("fails", attempts);
            if (attempts >= 5) s.put("retry", System.currentTimeMillis() + 30000).put("fails", 0);
        });
        return correct;
    }
}
