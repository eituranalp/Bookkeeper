import java.time.LocalDateTime;

// One movie, book or game (the data model).
public class Entry {

    public enum MediaType {
        BOOK, MOVIE, GAME, CUSTOM;

        // Turns text like "book" or "BOOK" into a MediaType. null stays null.
        public static MediaType parse(String text) {
            if (text == null) {
                return null;
            }
            try {
                return valueOf(text.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Media type must be BOOK, MOVIE, GAME or CUSTOM.");
            }
        }
    }

    private final int id;
    private final String title;
    private final MediaType mediaType;
    private final String customType;   // only used when mediaType is CUSTOM
    private final Integer rating;      // 1-10, or null if not rated
    private final String review;
    private final LocalDateTime dateCreated;
    private final LocalDateTime dateUpdated;

    // New entry: both dates are set to now (whole seconds).
    public Entry(int id, String title, MediaType mediaType, String customType, Integer rating, String review) {
        this(id, title, mediaType, customType, rating, review, now(), null);
    }

    // Existing entry (for example, loaded from the JSON file).
    public Entry(int id, String title, MediaType mediaType, String customType, Integer rating, String review,
                 LocalDateTime dateCreated, LocalDateTime dateUpdated) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (mediaType == null) {
            throw new IllegalArgumentException("Media type is required.");
        }
        if (mediaType == MediaType.CUSTOM && (customType == null || customType.isBlank())) {
            throw new IllegalArgumentException("Custom media type needs a name.");
        }
        checkRating(rating);

        this.id = id;
        this.title = title.trim();
        this.mediaType = mediaType;
        this.customType = (mediaType == MediaType.CUSTOM) ? customType.trim() : null;
        this.rating = rating;
        this.review = review;
        this.dateCreated = dateCreated;
        this.dateUpdated = (dateUpdated != null) ? dateUpdated : dateCreated;
    }

    // An edited copy: new details, same id and dateCreated, dateUpdated set to now.
    // The same rules apply as for a new entry.
    public Entry withChanges(String title, MediaType mediaType, String customType, Integer rating, String review) {
        return new Entry(id, title, mediaType, customType, rating, review, dateCreated, now());
    }

    // Dates are kept to whole seconds, the same as in the JSON file.
    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    // The rating rule: null (not rated) or 1-10.
    public static void checkRating(Integer rating) {
        if (rating != null && (rating < 1 || rating > 10)) {
            throw new IllegalArgumentException("Rating must be a whole number from 1 to 10.");
        }
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public MediaType getMediaType() { return mediaType; }
    public String getCustomType() { return customType; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    public LocalDateTime getDateCreated() { return dateCreated; }
    public LocalDateTime getDateUpdated() { return dateUpdated; }

    @Override
    public String toString() {
        String type = (mediaType == MediaType.CUSTOM) ? customType : mediaType.toString();
        String stars = (rating != null) ? rating + "/10" : "not rated";
        return "#" + id + " " + title + " (" + type + ", " + stars + ")";
    }
}
