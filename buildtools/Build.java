import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * One-command build for SoundGen. Needs only a JDK (17 or newer); no Gradle, no Forge setup.
 *
 *   java buildtools/Build.java            build + self-test + package
 *   java buildtools/Build.java --no-test  skip the self-test
 *   java buildtools/Build.java --install  also install into the live game (see installLive)
 *
 * Steps:
 *  1. download the Meowtils jar into libs/ if missing (compile reference + ASM for step 4)
 *  2. compile the Minecraft stubs, then the extension, targeting Java 8
 *  3. compile and run the offline engine self-test
 *  4. translate Minecraft names to Forge runtime (SRG) names (Reobf + mappings.txt)
 *  5. package build/libs/SoundGen-<version>.meowtils
 *  6. assemble the install package and zip: build/dropin/ and build/SoundGen-<version>-dropin.zip
 *     It holds two folders, one per destination, so nothing has to be nested correctly:
 *       "Drop into extensions folder/SoundGen-<version>.meowtils"  -> <game dir>/meowtils/extensions/
 *       "Drop into meowtils folder/soundgen/packs/default/..."    -> <game dir>/meowtils/
 */
public final class Build {
    static final String MEOWTILS_VERSION = "2.0.1";
    static final String MEOWTILS_URL =
            "https://github.com/femboytatp/meowtils/releases/download/" + MEOWTILS_VERSION + "/Meowtils-" + MEOWTILS_VERSION + ".jar";
    static final String SEP = File.pathSeparator;
    static final String EXT_FOLDER = "Drop into extensions folder";
    static final String DATA_FOLDER = "Drop into meowtils folder";

    public static void main(String[] args) throws Exception {
        boolean test = !Arrays.asList(args).contains("--no-test");
        boolean install = Arrays.asList(args).contains("--install");
        Path root = Paths.get("").toAbsolutePath();
        if (!Files.isRegularFile(root.resolve("VERSION"))) {
            fail("run this from the project folder (the one containing VERSION)");
        }
        String version = new String(Files.readAllBytes(root.resolve("VERSION")), StandardCharsets.UTF_8).trim();
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) fail("no Java compiler found: install a JDK (17 or newer), not just a JRE");

        Path build = root.resolve("build");
        Path meowtils = root.resolve("libs/meowtils.jar");
        step("SoundGen " + version);

        // 1. Meowtils reference jar
        if (!Files.isRegularFile(meowtils)) {
            step("downloading Meowtils " + MEOWTILS_VERSION + " (compile reference only, never shipped)");
            Files.createDirectories(meowtils.getParent());
            Path tmp = meowtils.resolveSibling("meowtils.jar.part");
            try (InputStream in = java.net.URI.create(MEOWTILS_URL).toURL().openStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, meowtils, StandardCopyOption.REPLACE_EXISTING);
        }

        deleteTree(build);
        Path stubs = build.resolve("stubs"), classes = build.resolve("classes"), testClasses = build.resolve("test-classes");
        Path tools = build.resolve("tools"), reobf = build.resolve("reobf");

        // 2. compile
        step("compiling");
        compile(javac, sources(root.resolve("buildtools/stubs")), stubs, null, false);
        compile(javac, sources(root.resolve("src/main/java")), classes, stubs + SEP + meowtils, true);

        // 3. self-test (engine, input, tools: no Minecraft needed)
        if (test) {
            step("running self-test");
            List<String> testSources = sources(root.resolve("src/test/java"));
            compile(javac, testSources, testClasses, classes.toString(), false);
            run(classes + SEP + testClasses, "meowtils.extension.soundgen.engine.EngineSelfTest");
        }

        // 4. Minecraft names -> Forge runtime names
        step("translating Minecraft names (reobf)");
        compile(javac, Arrays.asList(root.resolve("buildtools/Reobf.java").toString()), tools, meowtils.toString(), false);
        run(meowtils + SEP + tools, "Reobf", classes.toString(), stubs.toString(),
                root.resolve("buildtools/mappings.txt").toString(), reobf.toString());

        // 5. package
        Path jar = build.resolve("libs/SoundGen-" + version + ".meowtils");
        step("packaging " + root.relativize(jar));
        Files.createDirectories(jar.getParent());
        writeJar(jar, version, reobf, root.resolve("src/main/resources"));

        // 6. install package (one folder per destination) + zip
        step("assembling install package");
        Path dropin = build.resolve("dropin");
        Path ext = dropin.resolve(EXT_FOLDER);
        Files.createDirectories(ext);
        Files.copy(jar, ext.resolve(jar.getFileName()));
        // Create the default pack layout with the built tool itself (also proves the jar runs).
        run(jar.toString(), "meowtils.extension.soundgen.tools.SoundTool", "init",
                dropin.resolve(DATA_FOLDER + "/soundgen/packs/default").toString());
        Files.write(dropin.resolve("HOW-TO-INSTALL.txt"), installText(version).getBytes(StandardCharsets.UTF_8));
        Path zip = build.resolve("SoundGen-" + version + "-dropin.zip");
        zipTree(dropin, zip);

        if (install) installLive(jar);

        step("done");
        System.out.println("  extension : " + root.relativize(jar));
        System.out.println("  package   : " + root.relativize(dropin) + "  (zipped: " + root.relativize(zip) + ")");
    }

    static String installText(String version) {
        return String.join(System.lineSeparator(),
                "SoundGen " + version + " - install",
                "",
                "Your meowtils folder is <game dir>\\meowtils. For Lunar: %APPDATA%\\.minecraft\\meowtils",
                "(paste that into the Explorer address bar). It contains meowtils.json and an 'extensions' folder.",
                "Not the lunarclient\\Meowtils-Lunar folder: that one holds the Meowtils mod itself.",
                "",
                "1. Close Minecraft / Lunar.",
                "",
                "2. '" + EXT_FOLDER + "'",
                "   Drag SoundGen-" + version + ".meowtils into  meowtils\\extensions",
                "   Delete any older SoundGen-*.meowtils there (keep only one version).",
                "",
                "3. '" + DATA_FOLDER + "'",
                "   Drag the 'soundgen' folder into  meowtils  and merge if asked.",
                "   This holds the sound folders you record into. Optional on first install:",
                "   SoundGen creates the same folders itself the first time it is enabled.",
                "   It never overwrites your recordings (the folders only contain a README).",
                "",
                "   Result:",
                "     meowtils\\extensions\\SoundGen-" + version + ".meowtils",
                "     meowtils\\soundgen\\packs\\default\\<sound folders>",
                "",
                "4. Start the game, open the Meowtils menu, enable SoundGen, press Sounds > Test Sounds.",
                "",
                "Full guide: docs/GUIDE.md in the GitHub repository.",
                "");
    }

    /**
     * Copies the extension into the live game's meowtils/extensions folder, replacing older
     * SoundGen-*.meowtils files only; other extensions are never touched.
     * Game directory: SOUNDGEN_GAME_DIR if set, else %APPDATA%\.minecraft (Lunar's default),
     * else ~/.minecraft. Refuses unless meowtils/meowtils.json exists there, so it can't
     * write into the wrong place.
     */
    static void installLive(Path jar) throws IOException {
        String override = System.getenv("SOUNDGEN_GAME_DIR");
        String appData = System.getenv("APPDATA");
        Path game = override != null && !override.trim().isEmpty() ? Paths.get(override.trim())
                : appData != null ? Paths.get(appData, ".minecraft")
                : Paths.get(System.getProperty("user.home"), ".minecraft");
        Path meowtils = game.resolve("meowtils");
        if (!Files.isRegularFile(meowtils.resolve("meowtils.json"))) {
            fail("--install: no meowtils/meowtils.json under " + game
                    + " (set SOUNDGEN_GAME_DIR to the folder that contains your meowtils folder)");
        }
        Path ext = meowtils.resolve("extensions");
        Files.createDirectories(ext);
        step("installing into " + ext);
        try (Stream<Path> old = Files.list(ext)) {
            for (Path p : (Iterable<Path>) old::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith("SoundGen-") && n.endsWith(".meowtils") && !n.equals(jar.getFileName().toString())) {
                    System.out.println("  removing old " + n);
                    Files.delete(p);
                }
            }
        }
        try {
            Files.copy(jar, ext.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            fail("--install: could not write " + ext.resolve(jar.getFileName())
                    + " (is the game running? close it and try again): " + e.getMessage());
        }
        System.out.println("  installed " + jar.getFileName() + ". Restart the game to load it.");
    }

    // ------------------------------------------------------------------ helpers

    static void compile(JavaCompiler javac, List<String> sources, Path out, String classpath, boolean lint) throws IOException {
        if (sources.isEmpty()) return;
        Files.createDirectories(out);
        List<String> opts = new ArrayList<>(Arrays.asList("--release", "8", "-proc:none", "-encoding", "UTF-8", "-d", out.toString()));
        if (lint) {
            // Meowtils' constructor-time settings API makes "this-escape" (a JDK 21+ lint) unavoidable.
            opts.add(Runtime.version().feature() >= 21 ? "-Xlint:all,-options,-this-escape" : "-Xlint:all,-options");
            opts.add("-Werror");
        } else {
            opts.add("-nowarn");
        }
        if (classpath != null) opts.addAll(Arrays.asList("-cp", classpath));
        opts.addAll(sources);
        if (javac.run(null, null, null, opts.toArray(new String[0])) != 0) fail("compilation failed");
    }

    static List<String> sources(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().collect(Collectors.toList());
        }
    }

    static void run(String classpath, String mainClass, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classpath, mainClass));
        cmd.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        if (p.waitFor() != 0) fail(mainClass + " failed");
    }

    static void writeJar(Path jar, String version, Path classes, Path resources) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Implementation-Title", "SoundGen");
        mf.getMainAttributes().putValue("Implementation-Version", version);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            for (Path base : new Path[]{classes, resources}) {
                if (!Files.isDirectory(base)) continue;
                try (Stream<Path> walk = Files.walk(base)) {
                    for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                        out.putNextEntry(new JarEntry(base.relativize(f).toString().replace('\\', '/')));
                        Files.copy(f, out);
                        out.closeEntry();
                    }
                }
            }
        }
    }

    static void zipTree(Path dir, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) walk.sorted()::iterator) {
                    if (p.equals(dir)) continue;
                    String name = dir.relativize(p).toString().replace('\\', '/');
                    if (Files.isDirectory(p)) {
                        out.putNextEntry(new ZipEntry(name + "/")); // keep empty sound folders
                    } else {
                        out.putNextEntry(new ZipEntry(name));
                        Files.copy(p, out);
                    }
                    out.closeEntry();
                }
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void step(String s) {
        System.out.println("==> " + s);
    }

    static void fail(String s) {
        System.err.println("BUILD FAILED: " + s);
        System.exit(1);
    }
}
