package glowredman.txloader.progress;

public interface ProgressBar {

    void step(String message);

    void pop();

}
