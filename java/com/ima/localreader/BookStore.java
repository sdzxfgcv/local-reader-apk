package com.ima.localreader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BookStore {
    private static final String PREF = "local_reader_data";
    private static final String KEY_BOOKS = "books";

    public static List<Book> load(Context c) {
        List<Book> out = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_BOOKS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Book.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
        }
        return out;
    }

    public static void save(Context c, List<Book> books) {
        JSONArray arr = new JSONArray();
        for (Book b : books) arr.put(b.toJson());
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_BOOKS, arr.toString()).apply();
    }

    public static Book find(Context c, String id) {
        for (Book b : load(c)) if (b.id.equals(id)) return b;
        return null;
    }

    public static void update(Context c, Book target) {
        List<Book> books = load(c);
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).id.equals(target.id)) {
                books.set(i, target);
                break;
            }
        }
        save(c, books);
    }

    public static void remove(Context c, String id) {
        List<Book> books = load(c);
        List<Book> keep = new ArrayList<>();
        for (Book b : books) if (!b.id.equals(id)) keep.add(b);
        save(c, keep);
    }
}
