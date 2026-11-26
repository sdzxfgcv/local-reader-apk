package com.ima.localreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1001;
    private List<Book> books = new ArrayList<>();
    private ArrayAdapter<Book> adapter;
    private File booksDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        booksDir = new File(getFilesDir(), "books");
        booksDir.mkdirs();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF7F1E3);

        TextView title = new TextView(this);
        title.setText("本地阅读");
        title.setTextSize(28);
        title.setTextColor(0xFF2B2B2B);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 40, 0, 8);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("纯本地阅读 · TXT / EPUB · 无网络 · 无广告");
        sub.setTextSize(13);
        sub.setTextColor(0xFF887F6E);
        sub.setGravity(android.view.Gravity.CENTER);
        sub.setPadding(0, 0, 0, 24);
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        Button importBtn = new Button(this);
        importBtn.setText("＋  导入本地书籍");
        importBtn.setTextSize(17);
        importBtn.setTextColor(0xFFFFFFFF);
        importBtn.setBackgroundColor(0xFF4A7C59);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(36, 0, 36, 16);
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                startActivityForResult(i, REQ_IMPORT);
            }
        });
        root.addView(importBtn, bp);

        ListView list = new ListView(this);
        list.setCacheColorHint(0xFFF7F1E3);
        list.setDividerHeight(1);
        adapter = new ArrayAdapter<Book>(this, android.R.layout.simple_list_item_2, android.R.id.text1, books) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) v = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                Book b = getItem(position);
                t1.setText(b.title);
                t1.setTextSize(17);
                t1.setTextColor(0xFF2B2B2B);
                int pct = (int) (b.ratio * 100);
                t2.setText(b.format.toUpperCase() + " · 已读 " + pct + "% · " + (b.chapter + 1) + " 章");
                t2.setTextSize(12);
                t2.setTextColor(0xFF8A8578);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> open(books.get(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            final Book b = books.get(pos);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(b.title)
                    .setItems(new String[]{"继续阅读", "删除此书"}, (d, w) -> {
                        if (w == 0) open(b);
                        else {
                            BookStore.remove(MainActivity.this, b.id);
                            new File(booksDir, b.file).delete();
                            books.remove(pos);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button help = new Button(this);
        help.setText("使用说明");
        help.setTextSize(14);
        help.setBackgroundColor(0x00000000);
        help.setTextColor(0xFF4A7C59);
        help.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("使用说明")
                .setMessage("1. 点「导入本地书籍」从手机文件管理器选择 TXT 或 EPUB。\n2. 单击书籍继续阅读，长按可删除。\n3. 阅读页顶部：目录 / 设置 / 书签；底部：上一章、下一章。\n4. 进度、书签、字号、主题自动保存在本机，完全离线。\n5. TXT 自动识别 UTF-8 / GBK 编码并按「第X章」分章。")
                .setPositiveButton("知道了", null)
                .show());
        root.addView(help, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        books = BookStore.load(this);
        adapter.notifyDataSetChanged();
    }

    private void open(Book b) {
        Intent i = new Intent(this, ReaderActivity.class);
        i.putExtra("id", b.id);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        books = BookStore.load(this);
        adapter.clear();
        adapter.addAll(books);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = queryName(uri);
        String lower = name.toLowerCase();
        String format = lower.endsWith(".epub") ? "epub" : "txt";
        String id = String.valueOf(System.currentTimeMillis());
        File dest = new File(booksDir, id + "." + format);
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            fos.close();
            is.close();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Book b = new Book();
        b.id = id;
        b.title = name.replaceFirst("(?i)\\.(txt|epub)$", "");
        b.file = dest.getName();
        b.format = format;
        b.chapter = 0;
        b.ratio = 0;
        b.updated = System.currentTimeMillis();
        books.add(0, b);
        BookStore.save(this, books);
        adapter.clear();
        adapter.addAll(books);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "已导入：" + b.title, Toast.LENGTH_SHORT).show();
    }

    private String queryName(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } finally {
            if (c != null) c.close();
        }
        return "book-" + System.currentTimeMillis();
    }
}
