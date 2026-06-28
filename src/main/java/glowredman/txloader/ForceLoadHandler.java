package glowredman.txloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class ForceLoadHandler {

    static void run(Path mcLocation, Path configDir) {
        try {
            Path packsDir = mcLocation.resolve("resourcepacks");
            if (!Files.isDirectory(packsDir)) return;

            ForceLoadState state = ForceLoadState.load(configDir.resolve("forceloaded.json"));
            List<String> top = new ArrayList<>();
            List<String> bottom = new ArrayList<>();
            String now = Instant.now().toString();

            try (Stream<Path> packs = Files.list(packsDir)) {
                for (Path pack : (Iterable<Path>) packs::iterator) {
                    String name = pack.getFileName().toString();
                    if (state.contains(name)) continue;

                    Optional<ForceLoadMeta> meta = PackMetaReader.read(pack);
                    if (!meta.isPresent() || !meta.get().forceLoad()) continue;

                    if (meta.get().priority() == ForceLoadMeta.Priority.TOP) {
                        top.add(name);
                    } else {
                        bottom.add(name);
                    }
                    state.record(name, now);
                    TXLoaderCore.LOGGER.info("Force-loading resource pack {} ({})", name, meta.get().priority());
                }
            }

            if (top.isEmpty() && bottom.isEmpty()) return;

            Path options = mcLocation.resolve("options.txt");
            OptionsEditor.enable(options, bottom, ForceLoadMeta.Priority.BOTTOM);
            OptionsEditor.enable(options, top, ForceLoadMeta.Priority.TOP);
            state.save();
        } catch (Exception e) {
            TXLoaderCore.LOGGER.error("Force-load handling failed", e);
        }
    }
}
