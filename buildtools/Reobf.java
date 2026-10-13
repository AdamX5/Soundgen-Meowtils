import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Renames Minecraft member references in compiled SoundGen classes from MCP (readable) names
 * to SRG (Forge runtime) names, using buildtools/mappings.txt. Equivalent to ForgeGradle's
 * reobf step for the members we use.
 *
 * A reference is resolved through the stub class hierarchy, so "EntityPlayerSP.onGround"
 * finds the mapping declared on Entity. Any Minecraft member without a mapping fails the
 * build, so a new API call can't silently ship with a name that breaks at runtime.
 *
 * Uses the ASM library bundled inside the Meowtils jar (run with it on the classpath).
 *
 *   java -cp meowtils.jar:<this> Reobf <classes dir> <stub classes dir> <mappings.txt> <out dir>
 */
public final class Reobf {
    private static final Map<String, String> fields = new HashMap<>();
    private static final Map<String, String> methods = new HashMap<>();
    private static final Map<String, String> superOf = new HashMap<>();
    private static final TreeSet<String> missing = new TreeSet<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: Reobf <classes> <stub classes> <mappings.txt> <out>");
            System.exit(2);
        }
        Path classes = Paths.get(args[0]), stubs = Paths.get(args[1]), out = Paths.get(args[3]);
        loadMappings(Paths.get(args[2]));
        loadHierarchy(stubs);

        Remapper remapper = new Remapper() {
            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return resolve(fields, "field", owner, name);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                if (name.startsWith("<")) return name; // constructors / static init keep their names
                return resolve(methods, "method", owner, name);
            }
        };

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classes)) {
            walk.filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        }
        for (Path file : files) {
            ClassReader reader = new ClassReader(Files.readAllBytes(file));
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, remapper), 0);
            Path target = out.resolve(classes.relativize(file).toString());
            Files.createDirectories(target.getParent());
            Files.write(target, writer.toByteArray());
        }
        if (!missing.isEmpty()) {
            System.err.println("Reobf: no SRG mapping for these Minecraft members:");
            for (String m : missing) System.err.println("  " + m);
            System.err.println("Add them to buildtools/mappings.txt (see the header there for how to find SRG names).");
            System.exit(1);
        }
        System.out.println("Reobf: " + files.size() + " classes remapped");
    }

    private static String resolve(Map<String, String> table, String kind, String owner, String name) {
        if (!owner.startsWith("net/minecraft/")) return name;
        for (String c = owner; c != null; c = superOf.get(c)) {
            String srg = table.get(c + "." + name);
            if (srg != null) return srg;
        }
        missing.add(kind + " " + owner + "." + name);
        return name;
    }

    private static void loadMappings(Path file) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+");
            if (p.length < 4) throw new IOException("bad mapping line: " + line);
            Map<String, String> table = p[0].equals("F") ? fields : p[0].equals("M") ? methods : null;
            if (table == null) throw new IOException("bad mapping kind: " + line);
            table.put(p[1] + "." + p[2], p[3]);
        }
    }

    /** Reads superclass links from the compiled stubs, so lookups can walk up the hierarchy. */
    private static void loadHierarchy(Path stubs) throws IOException {
        try (Stream<Path> walk = Files.walk(stubs)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".class"))::iterator) {
                ClassReader r = new ClassReader(Files.readAllBytes(p));
                superOf.put(r.getClassName(), r.getSuperName());
            }
        }
    }
}
