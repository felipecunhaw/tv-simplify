package com.tvhome.example;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Minimal GET over HTTPS. Returns null on any failure: the home screen then keeps showing what it had.
// Always call from a background thread.
final class Net {
    private Net() {}

    static byte[] bytes(String url) {
        return bytes(url, null);
    }

    // bearer, when not null, goes in the Authorization header.
    static byte[] bytes(String url, String bearer) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "tvhome/1.0");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String get(String url) {
        return get(url, null);
    }

    static String get(String url, String bearer) {
        byte[] b = bytes(url, bearer);
        try { return b == null ? null : new String(b, "UTF-8"); }
        catch (Exception e) { return null; }
    }
}
