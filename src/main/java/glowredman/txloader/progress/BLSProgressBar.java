package glowredman.txloader.progress;

import java.io.IOException;

import alexiil.mods.load.ProgressDisplayer;
import cpw.mods.fml.common.FMLCommonHandler;

class BLSProgressBar implements IProgressBar {

    private final String name;
    private final int maxSteps;
    private int currentStep = 0;

    BLSProgressBar(String name, int maxSteps) {
        this.name = name;
        this.maxSteps = maxSteps;
    }

    @Override
    public void step(String message) {
        this.currentStep++;
        try {
            ProgressDisplayer
                    .displayProgress(this.name + ": " + message, (float) this.currentStep / (float) this.maxSteps);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FMLCommonHandler.instance().processWindowMessages();
    }

    @Override
    public void pop() {}

}
