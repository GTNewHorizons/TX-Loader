package glowredman.txloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import net.minecraft.util.StringTranslate;

class ServerLangHelper {

    static void load() {
        for (File modDir : TXLoaderCore.resourcesDir.listFiles(File::isDirectory)) {
            inject(modDir);
        }
        for (File modDir : TXLoaderCore.forceResourcesDir.listFiles(File::isDirectory)) {
            inject(modDir);
        }
    }

    private static void inject(File modDir) {
        File langFile = new File(modDir, "lang" + File.separatorChar + "en_US.lang");
        if (langFile.exists()) {
            try {
                StringTranslate.inject(new FileInputStream(langFile));
            } catch (FileNotFoundException e) {
                TXLoaderCore.LOGGER.error("Quantum file detected! It exists and doesn't exist at the same time...", e);
            }
        }
    }
}
