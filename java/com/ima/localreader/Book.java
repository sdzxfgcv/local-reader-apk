package com.ima.localreader;

import org.json.JSONObject;

public class Book {
    public String id;
    public String title;
    public String file;
    public String format;
    public int chapter;
    public double ratio;
    public long updated;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title);
            o.put("file", file);
            o.put("format", format);
            o.put("chapter", chapter);
            o.put("ratio", ratio);
            o.put("updated", updated);
        } catch (Exception e) {
        }
        return o;
    }

    public static Book fromJson(JSONObject o) {
        Book b = new Book();
        b.id = o.optString("id", "");
        b.title = o.optString("title", "未命名");
        b.file = o.optString("file", "");
        b.format = o.optString("format", "txt");
        b.chapter = o.optInt("chapter", 0);
        b.ratio = o.optDouble("ratio", 0.0);
        b.updated = o.optLong("updated", 0L);
        return b;
    }
}
