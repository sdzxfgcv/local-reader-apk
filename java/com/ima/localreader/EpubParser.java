package com.ima.localreader;

import android.text.Html;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EpubParser {
    private static final Pattern ITEM = Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEMREF = Pattern.compile("<itemref\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULLPATH = Pattern.compile("full-path\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public static List<Chapter> parse(File file) {
        List<Chapter> out = new ArrayList<>();
        try {
            ZipFile zf = new ZipFile(file);
            String opfPath = findOpf(zf);
            Map<String, String> idToHref = new LinkedHashMap<>();
            List<String> ordered = new ArrayList<>();
            if (opfPath != null) {
                String opf = readEntry(zf, opfPath);
                if (opf != null) {
                    Matcher im = ITEM.matcher(opf);
                    while (im.find()) {
                        String tag = im.group();
                        String id = attr(tag, "id");
                        String href = attr(tag, "href");
                        String type = attr(tag, "media-type");
                        if (id != null && href != null && (type == null || type.contains("html"))) {
                            idToHref.put(id, href);
                        }
                    }
                    Matcher sm = ITEMREF.matcher(opf);
                    while (sm.find()) {
                        String href = idToHref.get(attr(sm.group(), "idref"));
                        if (href != null) ordered.add(href);
                    }
                }
            }
            if (ordered.isEmpty()) {
                List<String> names = new ArrayList<>();
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    String low = n.toLowerCase();
                    if (low.endsWith(".xhtml") || low.endsWith(".html") || low.endsWith(".htm")) names.add(n);
                }
                Collections.sort(names);
                ordered.addAll(names);
            }
            String base = "";
            if (opfPath != null && opfPath.contains("/")) base = opfPath.substring(0, opfPath.lastIndexOf('/') + 1);
            for (String href : ordered) {
                String full = resolve(base, href);
                String html = full == null ? null : readEntry(zf, full);
                if (html == null) html = readBySuffix(zf, norm(href));
                if (html == null) continue;
                String title = extractTitle(html);
                String body = extractBody(html).replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ");
                String text = Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString();
                text = text.replace('\u00a0', ' ').replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
                if (text.isEmpty()) text = body.replaceAll("<[^>]+>", " ").replaceAll("\\s{2,}", " ").trim();
                out.add(new Chapter(title == null || title.isEmpty() ? ("第 " + (out.size() + 1) + " 节") : title, text));
            }
            zf.close();
        } catch (Exception e) {
        }
        if (out.isEmpty()) out.add(new Chapter("提示", "无法解析该 EPUB（可能加密或格式不受支持），请换用标准 EPUB 文件。"));
        return out;
    }

    private static String findOpf(ZipFile zf) {
        String xml = readEntry(zf, "META-INF/container.xml");
        if (xml != null) {
            Matcher m = FULLPATH.matcher(xml);
            if (m.find()) return norm(m.group(1));
        }
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.toLowerCase().endsWith(".opf")) return norm(n);
        }
        return null;
    }

    private static String norm(String s) {
        try {
            return URLDecoder.decode(s.trim().replace('\\', '/').replaceFirst("^/+", ""), "UTF-8");
        } catch (Exception e) {
            return s.trim().replace('\\', '/').replaceFirst("^/+", "");
        }
    }

    private static String resolve(String base, String href) {
        String h = norm(href);
        if (h.startsWith("http")) return null;
        if (base.isEmpty() || h.startsWith(base)) return h;
        return base + h;
    }

    private static String readBySuffix(ZipFile zf, String href) {
        String tail = href.contains("/") ? href.substring(href.lastIndexOf('/') + 1) : href;
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory() && e.getName().endsWith(tail)) return readEntry(zf, e.getName());
        }
        return null;
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    private static String extractTitle(String html) {
        Matcher m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        if (m.find()) return Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return null;
    }

    private static String extractBody(String html) {
        Matcher m = Pattern.compile("(?is)<body[^>]*>(.*)</body>").matcher(html);
        return m.find() ? m.group(1) : html;
    }

    private static String readEntry(ZipFile zf, String name) {
        ZipEntry e = zf.getEntry(name);
        if (e == null) e = zf.getEntry("/" + name);
        if (e == null || e.isDirectory()) return null;
        try {
            return TextEncoder.decode(TextEncoder.readBytes(zf.getInputStream(e)));
        } catch (Exception e2) {
            return null;
        }
    }
}
