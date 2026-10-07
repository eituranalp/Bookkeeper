import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// The collection of entries: search, sort, etc.
// The only place that changes the list. Every change is saved through Store right away.
public class Entries {

    private final Store store;
    private final List<Entry> list;
    private int nextId;

    // Loads the saved entries. The next id is one more than the highest saved id.
    public Entries(Store store) throws IOException {
        this.store = store;
        this.list = new ArrayList<>(store.load());
        this.nextId = 1;
        for (Entry e : list) {
            nextId = Math.max(nextId, e.getId() + 1);
        }
    }

    // Creates a new entry with the next id, saves it and returns it.
    // Throws IllegalArgumentException if the details break Entry's rules (nothing is added then).
    public Entry add(String title, Entry.MediaType mediaType, String customType, Integer rating, String review)
            throws IOException {
        Entry entry = new Entry(nextId, title, mediaType, customType, rating, review);
        list.add(entry);
        try {
            store.save(list);
        } catch (IOException e) {
            list.remove(entry); // keep memory the same as the file
            throw e;
        }
        nextId++;
        return entry;
    }

    // All entries, in the order they were added. The returned list is a copy and can't be changed.
    public List<Entry> getAll() {
        return List.copyOf(list);
    }
}
