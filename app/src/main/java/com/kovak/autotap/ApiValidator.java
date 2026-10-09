package com.kovak.autotap;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiValidator {

    public interface Callback {
        void onResult(boolean valid, String message, long expiryEpochMs);
    }

    public static void validate(final String serverUrl, final String apiKey, final Callback cb) {
        final Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean valid = false;
            String msg = "Unknown error";
            long expiry = 0;
            try {
                // Use /api/check — NOT /api/validate (which tracks IPs)
                String endpoint = serverUrl.replaceAll("/+$", "") + "/api/check";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");

                OutputStream os = conn.getOutputStream();
                os.write("{}".getBytes());
                os.flush();

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.optBoolean("valid", false)) {
                    valid = true;
                    msg = "Valid";
                    String exp = json.optString("expires_at", "");
                    if (!exp.isEmpty() && !exp.equals("null")) {
                        try {
                            java.text.SimpleDateFormat sdf =
                                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            java.util.Date d = sdf.parse(exp);
                            if (d != null) expiry = d.getTime();
                        } catch (Exception e) {}
                    }
                } else {
                    msg = json.optString("error", "invalid");
                }
            } catch (Exception e) {
                msg = "Network: " + e.getMessage();
            }
            final boolean fV = valid;
            final String fM = msg;
            final long fE = expiry;
            h.post(() -> cb.onResult(fV, fM, fE));
        }).start();
    }
}
