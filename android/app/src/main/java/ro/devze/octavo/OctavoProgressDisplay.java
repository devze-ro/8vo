package ro.devze.octavo;

/** The global, host-owned reader progress presentation choice. */
enum OctavoProgressDisplay {
    CHAPTER(0, "Chapter"),
    PAGE(1, "Page"),
    LOCATION(2, "Location"),
    PERCENTAGE(3, "Percentage");

    private final int nativeId;
    private final String label;

    OctavoProgressDisplay(int nativeId, String label) {
        this.nativeId = nativeId;
        this.label = label;
    }

    int nativeId() {
        return nativeId;
    }

    String label() {
        return label;
    }

    static OctavoProgressDisplay defaults() {
        return PERCENTAGE;
    }

    static OctavoProgressDisplay fromNativeId(int nativeId) {
        for (OctavoProgressDisplay display : values()) {
            if (display.nativeId == nativeId) {
                return display;
            }
        }
        return null;
    }
}
