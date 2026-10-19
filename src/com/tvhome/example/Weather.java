package com.tvhome.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

// Weather for the TV's own city: the city comes from its public IP, the forecast from Open-Meteo.
// Neither needs a key. The raw JSON is what gets cached; parse() turns it into what the screen shows.
final class Weather {
    static final int DAYS = 3;

    String city, description;
    int temp, code;
    final String[] dow = new String[DAYS];
    final int[] dayCode = new int[DAYS], max = new int[DAYS], min = new int[DAYS];

    // Network. Returns the JSON to cache, or null.
    static String fetchRaw() {
        try {
            JSONObject where = locate();
            if (where == null) return null;
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + where.getDouble("lat")
                    + "&longitude=" + where.getDouble("lon")
                    + "&current=temperature_2m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=" + (DAYS + 1);
            String body = Net.get(url);
            if (body == null) return null;
            return new JSONObject().put("city", where.getString("city")).put("om", new JSONObject(body)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    // City and coordinates from the public IP, trying a second service if the first is down.
    private static JSONObject locate() throws Exception {
        String a = Net.get("https://ipapi.co/json/");
        if (a != null) {
            JSONObject j = new JSONObject(a);
            if (j.has("latitude") && j.has("city"))
                return new JSONObject().put("city", j.getString("city")).put("lat", j.getDouble("latitude")).put("lon", j.getDouble("longitude"));
        }
        String b = Net.get("https://ipwho.is/");
        if (b != null) {
            JSONObject j = new JSONObject(b);
            if (j.optBoolean("success") && j.has("latitude"))
                return new JSONObject().put("city", j.getString("city")).put("lat", j.getDouble("latitude")).put("lon", j.getDouble("longitude"));
        }
        return null;
    }

    static Weather parse(String raw) {
        try {
            JSONObject root = new JSONObject(raw), om = root.getJSONObject("om");
            JSONObject cur = om.getJSONObject("current"), daily = om.getJSONObject("daily");
            Weather w = new Weather();
            w.city = root.getString("city");
            w.temp = (int) Math.round(cur.getDouble("temperature_2m"));
            w.code = cur.getInt("weather_code");
            w.description = describe(w.code);
            JSONArray time = daily.getJSONArray("time"), codes = daily.getJSONArray("weather_code");
            JSONArray hi = daily.getJSONArray("temperature_2m_max"), lo = daily.getJSONArray("temperature_2m_min");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", new Locale("pt", "BR"));
            for (int i = 0; i < DAYS; i++) {
                String d = out.format(in.parse(time.getString(i + 1))).replace(".", "");
                w.dow[i] = d.substring(0, 1).toUpperCase() + d.substring(1);
                w.dayCode[i] = codes.getInt(i + 1);
                w.max[i] = (int) Math.round(hi.getDouble(i + 1));
                w.min[i] = (int) Math.round(lo.getDouble(i + 1));
            }
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    // WMO weather codes, folded into the four pictures we draw.
    static int[] icon(int code) {
        if (code == 0) return LineIcon.SUN;
        if (code <= 2) return LineIcon.PARTLY;
        if (code == 3 || code == 45 || code == 48) return LineIcon.CLOUD;
        return LineIcon.RAIN;
    }

    private static String describe(int code) {
        if (code == 0) return "Céu limpo";
        if (code == 1) return "Poucas nuvens";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Neblina";
        if (code >= 51 && code <= 57) return "Garoa";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Chuva";
        if (code >= 71 && code <= 77) return "Neve";
        if (code >= 95) return "Tempestade";
        return "Instável";
    }
}
