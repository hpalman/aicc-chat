package aicc.chat.util;

import java.net.URI;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public class UtilString {
    public static String left(String s, int length) {
        if (s == null || s.length() < length) return s;
        return s.substring(0, length);
    }

    public static String right(String s, int length) {
        if (s == null || s.length() < length) return s;
        return s.substring(s.length() - length);
    }

    /**
     * 문자열에서 처음 몇글자, ..., 그리고 뒤의 몇글자를 가져옴
     * @param str
     * @param left_length
     * @param right_length
     * @return
     * UtilString.leftRight
     */
    public static String leftRight(String str, int left_length, int right_length) {
        return left(str,left_length) + "..." + right(str, right_length);
    }

    /**
     * 걍 편하게
     * @param str
     * @return
     */
    public static String emitToken(String str) {
        return left(str,15) + "..." + right(str, 5);
    }

    public static void appendNameValue(StringBuilder sb, String name, String value) {
        if ( value != null) {
            if ( sb.length() > 0) {
                sb.append(",");
            }
            sb.append( String.format("%s='%s'", name, value) );
        }
    }
    public static void appendNameValue(StringBuilder sb, String name, Integer value) {
        if ( value != null) {
            if ( sb.length() > 0) {
                sb.append(",");
            }
            sb.append( String.format("%s=%d", name, value) );
        }
    }

    /**
     * UtilString.getUriPath
     * @return
     */
    public static String getUriPath() {
        String fullUrl = ServletUriComponentsBuilder.fromCurrentRequest().toUriString();
        URI uri = URI.create(fullUrl);
        return uri.getPath();  // "/api/v1/users/123"
    }
}
