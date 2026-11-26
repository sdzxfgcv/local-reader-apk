package com.ima.localreader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class TextEncoder {
    public static byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    public static String decode(byte[] bytes) {
        int off = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            off = 3;
        }
        byte[] data = off == 0 ? bytes : java.util.Arrays.copyOfRange(bytes, off, bytes.length);
        String utf8 = strictDecode(data, StandardCharsets.UTF_8.name());
        if (utf8 != null) return utf8;
        String gbk = strictDecode(data, "GBK");
        if (gbk != null) return gbk;
        String gb18030 = strictDecode(data, "GB18030");
        return gb18030 != null ? gb18030 : new String(data, StandardCharsets.ISO_8859_1);
    }

    private static String strictDecode(byte[] data, String charset) {
        try {
            CharsetDecoder dec = java.nio.charset.Charset.forName(charset).newDecoder();
            dec.onMalformedInput(CodingErrorAction.REPORT);
            dec.onUnmappableCharacter(CodingErrorAction.REPORT);
            return dec.decode(ByteBuffer.wrap(data)).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
