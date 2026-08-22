package glowredman.txloader.progress;

class NoneProgressBar implements ProgressBar {

    static final NoneProgressBar INSTANCE = new NoneProgressBar();

    @Override
    public void step(String message) {}

    @Override
    public void pop() {}
}
