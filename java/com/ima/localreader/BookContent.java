package com.ima.localreader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BookContent {
    private static final Pattern CHAPTER = Pattern.compile(
            "(?m)^\\s*(第[0-9零〇一二三四五六七八九十百千万两]+[章节回卷部篇集][^\\n]{0,60}|Chapter\\s+\\d+[^\\n]{0,60}|[0-9]{1,4}[、.．]\\s*[^\\n]{0,60})\\s*$");

    public static List<Chapter> split(String text) {
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        starts.add(0);
        titles.add("开篇");
        Matcher m = CHAPTER.matcher(text);
        while (m.find()) {
            int s = m.start(1);
            if (s > starts.get(starts.size() - 1) + 20) {
                starts.add(s);
                titles.add(m.group(1).trim());
            }
        }
        List<Chapter> out = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String content = text.substring(starts.get(i), end).trim();
            chunk(out, titles.get(i), content);
        }
        if (out.isEmpty()) out.add(new Chapter("全文", text));
        return out;
    }

    private static void chunk(List<Chapter> out, String title, String content) {
        final int max = 90000;
        if (content.length() <= max) {
            out.add(new Chapter(title, content));
            return;
        }
        int pos = 0;
        int part = 1;
        while (pos < content.length()) {
            int end = Math.min(content.length(), pos + max);
            int cut = end;
            if (end < content.length()) {
                int p = content.lastIndexOf('\n', end);
                if (p > pos + max / 2) cut = p + 1;
            }
            out.add(new Chapter(title + " (" + part++ + ")", content.substring(pos, cut)));
            pos = cut;
        }
    }
}
