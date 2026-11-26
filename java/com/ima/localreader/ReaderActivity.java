package com.ima.localreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReaderActivity extends Activity {
    private static final Map<String, List<Chapter>> CACHE = new HashMap<>();
    private static final int[] BG = {0xFFF7F1E3, 0xFF181818, 0xFFDDE9D8, 0xFFF0E4CC};
    private static final int[] FG = {0xFF2B2B2B, 0xFFCFCFCF, 0xFF26402A, 0xFF4B3A27};
    private static final int[] BAR = {0xFFEDE3CE, 0xFF242424, 0xFFCEDCC8, 0xFFE7D7B8};

    private Book book;
    private List<Chapter> chapters = new ArrayList<>();
    private int idx;
    private SharedPreferences sp;
    private ObservableScrollView scroll;
    private TextView content;
    private TextView info;
    private LinearLayout root;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private long lastSave = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("id");
        book = BookStore.find(this, id);
        if (book == null) {
            finish();
            return;
        }
        sp = getSharedPreferences("local_reader_settings", MODE_PRIVATE);
        chapters = loadChapters();
        idx = Math.max(0, Math.min(book.chapter, chapters.size() - 1));
        buildUi();
        showChapter(idx, true);
        applySettings();
    }

    private List<Chapter> loadChapters() {
        List<Chapter> cached = CACHE.get(book.id);
        if (cached != null) return cached;
        List<Chapter> list;
        try {
            File f = new File(getFilesDir(), "books/" + book.file);
            if ("epub".equals(book.format)) {
                list = EpubParser.parse(f);
            } else {
                String text = TextEncoder.decode(TextEncoder.readBytes(new FileInputStream(f)));
                list = BookContent.split(text);
            }
        } catch (Exception e) {
            list = new ArrayList<>();
            list.add(new Chapter("错误", "读取书籍失败：" + e.getMessage()));
        }
        CACHE.put(book.id, list);
        return list;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(8, 12, 8, 12);
        topBar.addView(btn("‹ 书架", 0, v -> finish()), new LinearLayout.LayoutParams(0, -2, 0.8f));
        TextView name = new TextView(this);
        name.setText(book.title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextSize(15);
        name.setGravity(android.view.Gravity.CENTER);
        topBar.addView(name, new LinearLayout.LayoutParams(0, -2, 2.2f));
        topBar.addView(btn("目录", 1, v -> showCatalog()), new LinearLayout.LayoutParams(0, -2, 0.8f));
        topBar.addView(btn("设置", 2, v -> showSettings()), new LinearLayout.LayoutParams(0, -2, 0.8f));
        topBar.addView(btn("书签", 3, v -> showBookmarks()), new LinearLayout.LayoutParams(0, -2, 0.8f));
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ObservableScrollView(this);
        content = new TextView(this);
        content.setTextIsSelectable(true);
        content.setPadding(48, 24, 48, 48);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        scroll.setListener((l, t, oldT) -> {
            long now = System.currentTimeMillis();
            if (now - lastSave > 900) {
                lastSave = now;
                saveProgress();
                updateInfo();
            }
        });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(8, 10, 8, 10);
        bottomBar.addView(btn("← 上一章", 4, v -> showChapter(idx - 1, false)), new LinearLayout.LayoutParams(0, -2, 1.3f));
        info = new TextView(this);
        info.setTextSize(12);
        info.setGravity(android.view.Gravity.CENTER);
        bottomBar.addView(info, new LinearLayout.LayoutParams(0, -2, 1.8f));
        bottomBar.addView(btn("下一章 →", 5, v -> showChapter(idx + 1, false)), new LinearLayout.LayoutParams(0, -2, 1.3f));
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private Button btn(String text, int id, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setId(100 + id);
        b.setTextSize(id == 0 || id == 4 || id == 5 ? 13 : 12);
        b.setMinimumWidth(0);
        b.setPadding(4, 4, 4, 4);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(listener);
        return b;
    }

    private void showChapter(int i, boolean restore) {
        if (i < 0 || i >= chapters.size()) {
            Toast.makeText(this, i < 0 ? "已经是第一章" : "已经是最后一章", Toast.LENGTH_SHORT).show();
            return;
        }
        idx = i;
        content.setText(chapters.get(idx).text);
        scroll.post(() -> {
            int max = maxScroll();
            if (restore && idx == book.chapter) scroll.scrollTo(0, (int) (book.ratio * max));
            else scroll.scrollTo(0, 0);
            updateInfo();
        });
        book.chapter = idx;
        if (!restore) book.ratio = 0;
    }

    private int maxScroll() {
        return Math.max(1, content.getHeight() - scroll.getHeight());
    }

    private void updateInfo() {
        if (info == null) return;
        int max = maxScroll();
        double r = max > 1 ? (double) scroll.getScrollY() / max : 0;
        info.setText(String.format(Locale.CHINA, "%d/%d  %.0f%%", idx + 1, chapters.size(), r * 100));
    }

    private void saveProgress() {
        if (book == null) return;
        int max = maxScroll();
        book.ratio = max > 1 ? (double) scroll.getScrollY() / max : 0;
        if (book.ratio < 0) book.ratio = 0;
        if (book.ratio > 1) book.ratio = 1;
        book.chapter = idx;
        book.updated = System.currentTimeMillis();
        BookStore.update(this, book);
    }

    private void applySettings() {
        int font = sp.getInt("fontsize", 19);
        float spacing = sp.getFloat("spacing", 1.35f);
        int theme = sp.getInt("theme", 0);
        if (theme < 0 || theme >= BG.length) theme = 0;
        content.setTextSize(font);
        content.setLineSpacing(0, spacing);
        root.setBackgroundColor(BG[theme]);
        content.setTextColor(FG[theme]);
        topBar.setBackgroundColor(BAR[theme]);
        bottomBar.setBackgroundColor(BAR[theme]);
        int barText = theme == 1 ? 0xFFDDDDDD : 0xFF3B3B3B;
        for (int i = 0; i < topBar.getChildCount(); i++) {
            View v = topBar.getChildAt(i);
            if (v instanceof Button) ((Button) v).setTextColor(barText);
            else if (v instanceof TextView) ((TextView) v).setTextColor(barText);
        }
        for (int i = 0; i < bottomBar.getChildCount(); i++) {
            View v = bottomBar.getChildAt(i);
            if (v instanceof Button) ((Button) v).setTextColor(barText);
            else if (v instanceof TextView) ((TextView) v).setTextColor(barText);
        }
        updateInfo();
    }

    private void showSettings() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(32, 16, 32, 16);
        TextView t1 = new TextView(this);
        t1.setText("字号");
        t1.setTextSize(16);
        t1.setTextColor(0xFF333333);
        p.addView(t1);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = new Button(this);
        minus.setText("A －");
        Button plus = new Button(this);
        plus.setText("A ＋");
        row1.addView(minus, new LinearLayout.LayoutParams(0, -2, 1f));
        row1.addView(plus, new LinearLayout.LayoutParams(0, -2, 1f));
        p.addView(row1);

        TextView t2 = new TextView(this);
        t2.setText("行距");
        t2.setTextSize(16);
        t2.setTextColor(0xFF333333);
        p.addView(t2);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"紧凑", "标准", "宽松"};
        final float[] values = {1.1f, 1.35f, 1.6f};
        for (int i = 0; i < 3; i++) {
            Button b = new Button(this);
            final float val = values[i];
            b.setText(labels[i]);
            b.setOnClickListener(v -> {
                sp.edit().putFloat("spacing", val).apply();
                applySettings();
            });
            row2.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        p.addView(row2);

        TextView t3 = new TextView(this);
        t3.setText("背景主题");
        t3.setTextSize(16);
        t3.setTextColor(0xFF333333);
        p.addView(t3);
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        String[] themes = {"纸张", "夜间", "护眼", "米黄"};
        for (int i = 0; i < 4; i++) {
            Button b = new Button(this);
            final int th = i;
            b.setText(themes[i]);
            b.setOnClickListener(v -> {
                sp.edit().putInt("theme", th).apply();
                applySettings();
            });
            row3.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        p.addView(row3);

        minus.setOnClickListener(v -> {
            int f = Math.max(13, sp.getInt("fontsize", 19) - 1);
            sp.edit().putInt("fontsize", f).apply();
            applySettings();
        });
        plus.setOnClickListener(v -> {
            int f = Math.min(36, sp.getInt("fontsize", 19) + 1);
            sp.edit().putInt("fontsize", f).apply();
            applySettings();
        });

        new AlertDialog.Builder(this).setTitle("阅读设置").setView(p)
                .setPositiveButton("完成", null).show();
    }

    private void showCatalog() {
        String[] names = new String[chapters.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = String.format(Locale.CHINA, "%02d  %s", i + 1, chapters.get(i).title);
        }
        new AlertDialog.Builder(this).setTitle("目录")
                .setSingleChoiceItems(names, idx, (d, w) -> {
                    d.dismiss();
                    showChapter(w, false);
                })
                .setNegativeButton("取消", null).show();
    }

    private String bookmarkKey() {
        return "bookmarks_" + book.id;
    }

    private JSONArray bookmarks() {
        try {
            return new JSONArray(sp.getString(bookmarkKey(), "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void showBookmarks() {
        JSONArray arr = bookmarks();
        String[] labels = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            labels[i] = (o == null ? "书签" : o.optString("label", "书签")) + " · " + (int) (o == null ? 0 : o.optDouble("r", 0) * 100) + "%";
        }
        new AlertDialog.Builder(this).setTitle("书签")
                .setItems(labels.length == 0 ? new String[]{"（暂无书签）"} : labels, (d, w) -> {
                    if (labels.length == 0) return;
                    JSONObject o = arr.optJSONObject(w);
                    if (o == null) return;
                    book.chapter = o.optInt("c", 0);
                    book.ratio = o.optDouble("r", 0);
                    saveProgress();
                    showChapter(book.chapter, true);
                })
                .setNeutralButton("＋ 添加当前位置", (d, w) -> {
                    JSONArray a = bookmarks();
                    JSONObject o = new JSONObject();
                    try {
                        int max = maxScroll();
                        double r = max > 1 ? (double) scroll.getScrollY() / max : 0;
                        o.put("c", idx);
                        o.put("r", r);
                        o.put("label", chapters.get(idx).title);
                    } catch (Exception e) {
                    }
                    a.put(o);
                    sp.edit().putString(bookmarkKey(), a.toString()).apply();
                    Toast.makeText(this, "书签已添加", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("清空", (d, w) -> {
                    sp.edit().putString(bookmarkKey(), "[]").apply();
                    Toast.makeText(this, "书签已清空", Toast.LENGTH_SHORT).show();
                }).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    public static class ObservableScrollView extends ScrollView {
        interface ScrollListener {
            void onScroll(int l, int t, int oldT);
        }

        private ScrollListener listener;

        public ObservableScrollView(Context c) {
            super(c);
        }

        public ObservableScrollView(Context c, AttributeSet a) {
            super(c, a);
        }

        void setListener(ScrollListener l) {
            listener = l;
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldL, int oldT) {
            super.onScrollChanged(l, t, oldL, oldT);
            if (listener != null) listener.onScroll(l, t, oldT);
        }
    }
}
