package glowredman.txloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import net.minecraft.util.StringTranslate;

class ServerLangHelper {

    static void load() {
        try (Stream<Path> dirs = Files.list(TXLoaderCore.resourcesDir).filter(Files::isDirectory);
                Stream<Path> forcedDirs = Files.list(TXLoaderCore.forceResourcesDir).filter(Files::isDirectory)) {
            dirs.forEach(ServerLangHelper::inject);
            forcedDirs.forEach(ServerLangHelper::inject);
        } catch (IOException e) {
            TXLoaderCore.LOGGER.error("Failed to inject lang files", e);
        }
    }

    private static void inject(Path modDir) {
        Path langFile = modDir.resolve("lang").resolve("en_US.lang");
        if (Files.exists(langFile)) {
            try {
                StringTranslate.inject(Files.newInputStream(langFile));
            } catch (IOException e) {
                TXLoaderCore.LOGGER.error("Failed to create InputStream for {}", modDir, e);
            }
        }
    }
}
