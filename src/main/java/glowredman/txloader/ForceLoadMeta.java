package glowredman.txloader;

class ForceLoadMeta {

    enum Priority {

        TOP,
        BOTTOM;

        static Priority fromString(String s) {
            return "top".equalsIgnoreCase(s) ? TOP : BOTTOM;
        }
    }

    private final boolean forceLoad;
    private final Priority priority;

    ForceLoadMeta(boolean forceLoad, Priority priority) {
        this.forceLoad = forceLoad;
        this.priority = priority;
    }

    boolean forceLoad() {
        return forceLoad;
    }

    Priority priority() {
        return priority;
    }
}
