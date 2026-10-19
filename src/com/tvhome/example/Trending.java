package com.tvhome.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

// Movies and series trending today, from TMDB (needs a free API key, saved with --es tmdb <key>).
// The raw JSON is what gets cached; parse() turns it into items, in TMDB's ranking order.
final class Trending {
    static final int COUNT = 8;
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMG = "https://image.tmdb.org/t/p/w342";

    static final class Item {
        int id;
        boolean series;
        String title, year, poster;
        double rating;
    }

    // TMDB gives two credentials: the short API key (goes in the URL) and the long read access token
    // (a JWT, starts with "eyJ", goes in the Authorization header). Either works here.
    private static boolean isToken(String key) {
        return key.startsWith("eyJ");
    }

    private static String get(String path, String key) {
        boolean token = isToken(key);
        String url = API + path + (token ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + key);
        return Net.get(url, token ? key : null);
    }

    static String fetchRaw(String key, boolean series) {
        return get("trending/" + (series ? "tv" : "movie") + "/day?language=pt-BR", key);
    }

    static List<Item> parse(String raw, boolean series) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray rs = new JSONObject(raw).getJSONArray("results");
            for (int i = 0; i < rs.length() && out.size() < COUNT; i++) {
                JSONObject r = rs.getJSONObject(i);
                if (r.isNull("poster_path")) continue;
                Item it = new Item();
                it.id = r.getInt("id");
                it.series = series;
                it.title = r.optString(series ? "name" : "title");
                String date = r.optString(series ? "first_air_date" : "release_date");
                it.year = date.length() >= 4 ? date.substring(0, 4) : "";
                it.rating = r.optDouble("vote_average", 0);
                it.poster = r.getString("poster_path");
                out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String posterName(Item it) {
        return "poster" + it.poster.replace('/', '_');
    }

    static File posterFile(File dir, Item it) {
        return new File(dir, posterName(it));
    }

    // The poster as a file in the cache folder, downloaded once. Null when it can't be had.
    static File poster(File dir, Item it) {
        File f = posterFile(dir, it);
        if (f.exists() && f.length() > 0) return f;
        byte[] b = Net.bytes(IMG + it.poster);
        if (b == null) return null;
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(b);
            return f;
        } catch (Exception e) {
            f.delete();
            return null;
        }
    }

    // The IMDb id, which is what Stremio opens a title by.
    static String imdbId(String key, Item it) {
        String body = get((it.series ? "tv/" : "movie/") + it.id + "/external_ids", key);
        try { return body == null ? null : new JSONObject(body).optString("imdb_id", null); }
        catch (Exception e) { return null; }
    }

    // Keeps the cache folder from growing: only the posters on screen stay.
    static void prune(File dir, List<Item> keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().startsWith("poster")) continue;
            boolean used = false;
            for (Item it : keep) if (f.getName().equals(posterName(it))) used = true;
            if (!used) f.delete();
        }
    }
}
