package glowredman.txloader;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureWrapper<T> {

    public enum State {
        /**
         * No file exists at the target destination and no other {@link Asset} was scheduled for it.
         */
        NEW,

        /**
         * The requested asset already exists at the target destination.
         */
        FILE_EXISTS,

        /**
         * A different {@link Asset} for the same target path has already been added or scheduled.
         */
        DUPLICATE_ASSET;
    }

    public final CompletableFuture<T> future;
    public final State state;

    public CompletableFutureWrapper(CompletableFuture<T> future, State state) {
        this.future = future;
        this.state = state;
    }
}
