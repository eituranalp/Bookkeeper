import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Loads and saves entries to the JSON file.
public class Store {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Path file;

    public Store(Path file) {
        this.file = file;
    }

    // Reads all entries from the file. Returns an empty list if the file doesn't exist yet.
    public List<Entry> load() throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);

        Object root;
        try {
            root = parseJson(text);
        } catch (RuntimeException e) {
            throw new IOException(file + " is not valid JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof List<?> items)) {
            throw new IOException(file + " should contain a list of entries.");
        }

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                entries.add(toEntry(items.get(i)));
            } catch (RuntimeException e) {
                throw new IOException(file + ", entry " + (i + 1) + " is not valid: " + e.getMessage(), e);
            }
        }
        return entries;
    }

    // Writes all entries to the file, replacing what was there.
    public void save(List<Entry> entries) throws IOException {
        // Write to a temporary file first, so a crash mid-write can't damage the real file.
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, toJson(entries) + "\n", StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- Writing ----

    // A list of entries as JSON text (the same format as the file).
    public static String toJson(List<Entry> entries) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            out.append(i == 0 ? "\n" : ",\n");
            appendEntry(out, entries.get(i), "  ");
        }
        return out.append(entries.isEmpty() ? "]" : "\n]").toString();
    }

    // One entry as JSON text (the same format as in the file).
    public static String toJson(Entry e) {
        StringBuilder out = new StringBuilder();
        appendEntry(out, e, "");
        return out.toString();
    }

    // indent: spaces put before each line, so entries line up inside the file's list.
    private static void appendEntry(StringBuilder out, Entry e, String indent) {
        String field = indent + "  ";
        out.append(indent).append("{\n")
           .append(field).append("\"id\": ").append(e.getId()).append(",\n")
           .append(field).append("\"title\": ").append(quote(e.getTitle())).append(",\n")
           .append(field).append("\"mediaType\": ").append(quote(e.getMediaType().name())).append(",\n")
           .append(field).append("\"customType\": ").append(quote(e.getCustomType())).append(",\n")
           .append(field).append("\"rating\": ").append(e.getRating()).append(",\n") // a null rating is written as null
           .append(field).append("\"review\": ").append(quote(e.getReview())).append(",\n")
           .append(field).append("\"dateCreated\": ").append(quote(e.getDateCreated().format(DATE_FORMAT))).append(",\n")
           .append(field).append("\"dateUpdated\": ").append(quote(e.getDateUpdated().format(DATE_FORMAT))).append("\n")
           .append(indent).append("}");
    }

    // Turns text into a JSON string, escaping special characters. null becomes null.
    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ---- Reading ----

    // Reads any JSON text. Gives back Map (object), List (array), String, Long or Double (number),
    // Boolean, or null. Throws IllegalArgumentException if the text isn't valid JSON.
    public static Object parseJson(String text) {
        return new JsonReader(text).readDocument();
    }

    // Reads a text field from a JSON object. Missing or null gives null.
    public static String textField(Map<?, ?> fields, String name) {
        Object value = fields.get(name);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException(name + " must be text.");
        }
        return (String) value;
    }

    // Reads a whole-number field from a JSON object. Missing or null gives null.
    public static Integer wholeNumberField(Map<?, ?> fields, String name) {
        Object value = fields.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Long n)) {
            throw new IllegalArgumentException(name + " must be a whole number.");
        }
        return Math.toIntExact(n);
    }

    private static Entry toEntry(Object item) {
        if (!(item instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("expected an object");
        }
        Integer id = wholeNumberField(m, "id");
        if (id == null) {
            throw new IllegalArgumentException("id is missing");
        }
        String dateCreated = textField(m, "dateCreated");
        if (dateCreated == null) {
            throw new IllegalArgumentException("dateCreated is missing");
        }
        String dateUpdated = textField(m, "dateUpdated");
        // Entry's constructor checks the remaining rules (title, media type, rating range, custom type).
        return new Entry(
                id,
                textField(m, "title"),
                Entry.MediaType.parse(textField(m, "mediaType")),
                textField(m, "customType"),
                wholeNumberField(m, "rating"),
                textField(m, "review"),
                LocalDateTime.parse(dateCreated),
                dateUpdated == null ? null : LocalDateTime.parse(dateUpdated));
    }

    // Minimal JSON reader. Gives back Map (object), List (array), String, Long or Double (number),
    // Boolean, or null.
    private static class JsonReader {
        private final String text;
        private int pos = 0;

        JsonReader(String text) {
            this.text = text;
        }

        Object readDocument() {
            Object value = readValue();
            skipSpaces();
            if (pos < text.length()) {
                throw error("unexpected text after the end");
            }
            return value;
        }

        private Object readValue() {
            skipSpaces();
            char c = peek();
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (text.startsWith("null", pos)) { pos += 4; return null; }
            if (text.startsWith("true", pos)) { pos += 4; return true; }
            if (text.startsWith("false", pos)) { pos += 5; return false; }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipSpaces();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipSpaces();
                String key = readString();
                skipSpaces();
                expect(':');
                map.put(key, readValue());
                skipSpaces();
                if (peek() == '}') {
                    pos++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipSpaces();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipSpaces();
                if (peek() == ']') {
                    pos++;
                    return list;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("unexpected end of file");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("unknown escape \\" + escaped);
                }
            }
        }

        private Number readNumber() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            String number = text.substring(start, pos);
            if (number.isEmpty()) {
                throw error("unexpected character '" + peek() + "'");
            }
            if (number.contains(".") || number.contains("e") || number.contains("E")) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("unexpected end of file");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + pos);
        }
    }
}
