import java.nio.file.Path;

// Main starting point. Starts the app.
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        Entries entries = new Entries(new Store(Path.of("entries.json")));
        new Requests(entries, Path.of("UI")).start(PORT);
        System.out.println("Bookkeeper is running at http://localhost:" + PORT + " (Ctrl+C to stop)");
    }
}
