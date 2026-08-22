package glowredman.txloader.progress;

import cpw.mods.fml.common.ProgressManager;

@SuppressWarnings("deprecation")
class FMLProgressBar implements ProgressBar {

    private final ProgressManager.ProgressBar instance;

    FMLProgressBar(String name, int maxSteps) {
        this.instance = ProgressManager.push(name, maxSteps);
    }

    @Override
    public void step(String message) {
        this.instance.step(message);
    }

    @Override
    public void pop() {
        ProgressManager.pop(this.instance);
    }
}
