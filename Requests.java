import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

// Handles HTTP requests and returns JSON.
//   GET  /entries   -> all entries
//   POST /entries   -> add an entry (JSON body), returns the new entry
//   GET  /anything  -> a file from the UI folder ("/" gives Index.html)
public class Requests {

    private final Entries entries;
    private final Path uiFolder;

    public Requests(Entries entries, Path uiFolder) {
        this.entries = entries;
        this.uiFolder = uiFolder.toAbsolutePath().normalize();
    }

    // Starts the server. It handles one request at a time, so Entries is never used by two at once.
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/entries", this::handleEntries);
        server.createContext("/", this::handleUi);
        server.start();
    }

    // ---- /entries ----

    private void handleEntries(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals("/entries")) {
                sendError(exchange, 404, "Not found.");
                return;
            }
            switch (exchange.getRequestMethod()) {
                case "GET" -> sendJson(exchange, 200, Store.toJson(entries.getAll()));
                case "POST" -> sendJson(exchange, 201, Store.toJson(addFromBody(exchange)));
                default -> sendError(exchange, 405, "Method not allowed.");
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage()); // bad input: the message says what's wrong
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    // Reads the JSON body, for example {"title": "Dune", "mediaType": "BOOK", "rating": 9},
    // and adds it as a new entry.
    private Entry addFromBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Object json;
        try {
            json = Store.parseJson(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Body is not valid JSON: " + e.getMessage());
        }
        if (!(json instanceof Map<?, ?> fields)) {
            throw new IllegalArgumentException("Send one entry as a JSON object.");
        }
        // Entry's constructor checks the rules (title required, rating 1-10, ...).
        return entries.add(
                Store.textField(fields, "title"),
                Entry.MediaType.parse(Store.textField(fields, "mediaType")),
                Store.textField(fields, "customType"),
                Store.wholeNumberField(fields, "rating"),
                Store.textField(fields, "review"));
    }

    // ---- UI files ----

    private void handleUi(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String name = path.equals("/") ? "Index.html" : path.substring(1);

        Path file;
        try {
            file = uiFolder.resolve(name).normalize();
        } catch (Exception e) {
            file = null;
        }
        // Only files inside the UI folder can be sent.
        if (file == null || !file.startsWith(uiFolder) || !Files.isRegularFile(file)) {
            sendError(exchange, 404, "Not found.");
            return;
        }
        send(exchange, 200, contentType(name), Files.readAllBytes(file));
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        return "application/octet-stream";
    }

    // ---- Sending responses ----

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    // Errors are sent as {"error": "message"}.
    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\": " + Store.quote(message) + "}");
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
