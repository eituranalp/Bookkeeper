import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

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

    // Replaces the entry with this id, saves and returns the new version.
    // The id and dateCreated stay the same; dateUpdated becomes now.
    // Throws NoSuchElementException if there is no such id, IllegalArgumentException for bad details.
    public Entry update(int id, String title, Entry.MediaType mediaType, String customType, Integer rating,
                        String review) throws IOException {
        int index = indexOf(id);
        Entry old = list.get(index);
        Entry updated = old.withChanges(title, mediaType, customType, rating, review);
        list.set(index, updated);
        try {
            store.save(list);
        } catch (IOException e) {
            list.set(index, old); // keep memory the same as the file
            throw e;
        }
        return updated;
    }

    // Removes the entry with this id, saves and returns the removed entry.
    // Throws NoSuchElementException if there is no such id.
    public Entry delete(int id) throws IOException {
        int index = indexOf(id);
        Entry removed = list.remove(index);
        try {
            store.save(list);
        } catch (IOException e) {
            list.add(index, removed); // keep memory the same as the file
            throw e;
        }
        return removed;
    }

    // All entries, in the order they were added. The returned list is a copy and can't be changed.
    public List<Entry> getAll() {
        return List.copyOf(list);
    }

    // Entries whose title contains `search` (any letter case), in `sort` order.
    // search: null or blank gives every entry.
    // sort: "newest", "oldest", "title" (A-Z) or "rating" (high to low, unrated last).
    //       null or blank keeps the order they were added.
    // Throws IllegalArgumentException for any other sort value.
    public List<Entry> find(String search, String sort) {
        String text = (search == null) ? "" : search.trim().toLowerCase();
        List<Entry> found = new ArrayList<>();
        for (Entry e : list) {
            if (e.getTitle().toLowerCase().contains(text)) {
                found.add(e);
            }
        }

        if (sort != null && !sort.isBlank()) {
            Comparator<Entry> newest = Comparator.comparing(Entry::getDateCreated)
                    .thenComparing(Entry::getId)
                    .reversed();
            Comparator<Entry> order = switch (sort.trim()) {
                case "newest" -> newest;
                case "oldest" -> newest.reversed();
                case "title" -> Comparator.comparing(Entry::getTitle, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(newest);
                case "rating" -> Comparator.comparing(Entry::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(newest);
                default -> throw new IllegalArgumentException("Sort must be newest, oldest, title or rating.");
            };
            found.sort(order);
        }
        return List.copyOf(found);
    }

    // Position of the entry with this id in the list.
    private int indexOf(int id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                return i;
            }
        }
        throw new NoSuchElementException("No entry with id " + id + ".");
    }
}
