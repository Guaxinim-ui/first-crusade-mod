import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Validador estático da Hive City — auditoria distrito -&gt; módulo -&gt; template -&gt; assets.
 *
 * <p>Porta Java de {@code tools/hive_city_validate.py} (mesma lógica, mesmo relatório) porque
 * este ambiente não tem um interpretador Python funcional instalado (apenas o stub da
 * Microsoft Store) — só {@code javac}/{@code java} (JDK 17, já usado pelo Gradle). Roda
 * inteiramente offline sobre os arquivos reais do repositório, sem depender do Minecraft/Forge.
 *
 * <p>Cobre a lista de erros do prompt mestre §19: missing texture, missing model, missing
 * blockstate, template not found, invalid NBT, duplicate placement, socket mismatch,
 * referência quebrada (district→module, module→template), + módulos/NBTs órfãos.
 *
 * <p>Convenções de resolução de ResourceLocation replicadas do código real (não reinventadas):
 * <ul>
 *   <li>Districts: {@code data/<ns>/hive_districts/<path>.json} ({@code HiveDistricts.java})</li>
 *   <li>Modules:   {@code data/<ns>/hive_modules/<path>.json} ({@code HiveModuleManager.java})</li>
 *   <li>Templates: {@code data/<ns>/structures/<path>.nbt} (convenção vanilla)</li>
 *   <li>Blockstates/models/textures: convenção vanilla de assets.</li>
 * </ul>
 *
 * <p>A checagem de costuras (seams) reimplementa exatamente {@code HiveCommands.touchingFace} +
 * {@code HiveModule.socketAt}/{@code fits}, à rotação de distrito 0 (a rotação global da cidade
 * gira um distrito inteiro rigidamente — não afeta o encaixe interno entre seus módulos).
 *
 * <p>A checagem de blockstate/model/textura é "best-effort" por bloco (não por combinação exata
 * de propriedades): confere que o blockstate existe e que TODOS os modelos que ele referencia
 * (variants ou multipart) resolvem, e que TODAS as texturas desses modelos resolvem. Não tenta
 * reconstruir a ordem exata de propriedades que o Minecraft usaria para casar uma variant
 * específica (exigiria introspecção da StateDefinition Java) — mas captura os três defeitos
 * reais que travam o carregamento do jogo: blockstate ausente, modelo ausente, textura ausente.
 *
 * <p>Rodar (a partir da raiz do repo): {@code javac tools/HiveCityValidate.java -d <tmp> &&
 * java -cp <tmp> HiveCityValidate}
 */
public final class HiveCityValidate {

    static final String DATA = "src/main/resources/data";
    static final String ASSETS = "src/main/resources/assets";
    static final String OUT_MD = "tools/generated/HIVE_CITY_VALIDATION_REPORT.md";

    static final String[] EXPECTED_DISTRICT_IDS = {
        "firstcrusade:south_ash_gate", "firstcrusade:hive_wall_line", "firstcrusade:hive_corner_bastion",
        "firstcrusade:manufactorum", "firstcrusade:hab_stacks", "firstcrusade:administratum",
        "firstcrusade:underhive", "firstcrusade:spire",
    };

    record Finding(String category, String message) {}

    static final List<Finding> errors = new ArrayList<>();
    static final List<Finding> warnings = new ArrayList<>();
    static final List<Finding> infos = new ArrayList<>();

    static void err(String cat, String msg) { errors.add(new Finding(cat, msg)); }
    static void warn(String cat, String msg) { warnings.add(new Finding(cat, msg)); }
    static void info(String cat, String msg) { infos.add(new Finding(cat, msg)); }

    // ------------------------------------------------------------------ ResourceLocation helpers

    static String[] rlSplit(String rl) {
        int idx = rl.indexOf(':');
        return idx < 0 ? new String[]{"minecraft", rl} : new String[]{rl.substring(0, idx), rl.substring(idx + 1)};
    }

    static Path resolve(String base, String rl, String ext) {
        String[] ns = rlSplit(rl);
        return Paths.get(base, ns[0], ns[1] + ext);
    }

    static Path districtJsonPath(String rl) { return resolveSub(DATA, rl, "hive_districts", ".json"); }
    static Path moduleJsonPath(String rl)   { return resolveSub(DATA, rl, "hive_modules", ".json"); }
    static Path templateNbtPath(String rl)  { return resolveSub(DATA, rl, "structures", ".nbt"); }
    static Path blockstatePath(String rl)   { return resolveSub(ASSETS, rl, "blockstates", ".json"); }
    static Path modelPath(String rl)        { return resolveSub(ASSETS, rl, "models", ".json"); }
    static Path texturePath(String rl)      { return resolveSub(ASSETS, rl, "textures", ".png"); }

    static Path resolveSub(String base, String rl, String sub, String ext) {
        String[] ns = rlSplit(rl);
        return Paths.get(base, ns[0], sub, ns[1] + ext);
    }

    // ------------------------------------------------------------------ minimal JSON parser

    static final class JsonParser {
        private final String s;
        private int i;
        private JsonParser(String s) { this.s = s; }

        static Object parse(String s) {
            JsonParser p = new JsonParser(s);
            p.ws();
            Object v = p.value();
            p.ws();
            return v;
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { expect("true"); return Boolean.TRUE; }
            if (c == 'f') { expect("false"); return Boolean.FALSE; }
            if (c == 'n') { expect("null"); return null; }
            return number();
        }

        private void expect(String lit) {
            if (!s.startsWith(lit, i)) throw new RuntimeException("expected " + lit + " at " + i);
            i += lit.length();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (s.charAt(i) != ':') throw new RuntimeException("expected : at " + i);
                i++; ws();
                m.put(k, value());
                ws();
                char ch = s.charAt(i);
                if (ch == ',') { i++; continue; }
                if (ch == '}') { i++; break; }
                throw new RuntimeException("expected , or } at " + i);
            }
            return m;
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char ch = s.charAt(i);
                if (ch == ',') { i++; continue; }
                if (ch == ']') { i++; break; }
                throw new RuntimeException("expected , or ] at " + i);
            }
            return l;
        }

        private String string() {
            if (s.charAt(i) != '"') throw new RuntimeException("expected string at " + i);
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: throw new RuntimeException("bad escape " + e);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }

        private Object number() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            boolean isDouble = false;
            if (i < s.length() && s.charAt(i) == '.') {
                isDouble = true; i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                isDouble = true; i++;
                if (s.charAt(i) == '+' || s.charAt(i) == '-') i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            String numStr = s.substring(start, i);
            return isDouble ? (Object) Double.parseDouble(numStr) : (Object) Long.parseLong(numStr);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) { return (List<Object>) o; }
    static String asString(Object o) { return (String) o; }
    static int asInt(Object o) { return ((Number) o).intValue(); }

    static int[] asIntArray(Object o) {
        List<Object> l = asList(o);
        int[] out = new int[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = asInt(l.get(i));
        return out;
    }

    static Map<String, Object> readJson(Path path) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return asMap(JsonParser.parse(text));
    }

    // ------------------------------------------------------------------ NBT reader (generic, gzip)

    static final class NbtReader {
        private final DataInputStream in;
        NbtReader(InputStream is) { this.in = new DataInputStream(is); }

        Map<String, Object> readRoot() throws IOException {
            int t = in.readUnsignedByte();
            if (t != 10) throw new IOException("root is not TAG_Compound (got " + t + ")");
            readUtf();
            return readCompound();
        }

        private Map<String, Object> readCompound() throws IOException {
            Map<String, Object> m = new LinkedHashMap<>();
            while (true) {
                int t = in.readUnsignedByte();
                if (t == 0) break;
                String k = readUtf();
                m.put(k, readPayload(t));
            }
            return m;
        }

        private Object readPayload(int tag) throws IOException {
            switch (tag) {
                case 1: return in.readByte();
                case 2: return in.readShort();
                case 3: return in.readInt();
                case 4: return in.readLong();
                case 5: return in.readFloat();
                case 6: return in.readDouble();
                case 7: {
                    int n = in.readInt();
                    byte[] b = new byte[n];
                    in.readFully(b);
                    return b;
                }
                case 8: return readUtf();
                case 9: {
                    int et = in.readUnsignedByte();
                    int n = in.readInt();
                    List<Object> l = new ArrayList<>(Math.max(0, n));
                    for (int k = 0; k < n; k++) l.add(readPayload(et));
                    return l;
                }
                case 10: return readCompound();
                case 11: {
                    int n = in.readInt();
                    int[] a = new int[n];
                    for (int k = 0; k < n; k++) a[k] = in.readInt();
                    return a;
                }
                case 12: {
                    int n = in.readInt();
                    long[] a = new long[n];
                    for (int k = 0; k < n; k++) a[k] = in.readLong();
                    return a;
                }
                default: throw new IOException("unknown NBT tag " + tag);
            }
        }

        private String readUtf() throws IOException {
            int len = in.readUnsignedShort();
            byte[] buf = new byte[len];
            in.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    record StructureNbt(int[] size, List<String> palette) {}

    static StructureNbt readStructureNbt(Path path) throws IOException {
        try (InputStream fis = Files.newInputStream(path);
             GZIPInputStream gz = new GZIPInputStream(fis)) {
            Map<String, Object> root = new NbtReader(gz).readRoot();
            int[] size = asIntArray(root.get("size"));
            List<String> palette = new ArrayList<>();
            for (Object pe : asList(root.get("palette"))) {
                Map<String, Object> comp = asMap(pe);
                String name = asString(comp.get("Name"));
                if (comp.containsKey("Properties")) {
                    Map<String, Object> props = asMap(comp.get("Properties"));
                    TreeMap<String, Object> sorted = new TreeMap<>(props);
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, Object> e : sorted.entrySet()) {
                        if (sb.length() > 0) sb.append(';');
                        sb.append(e.getKey()).append('=').append(e.getValue());
                    }
                    palette.add(name + "|" + sb);
                } else {
                    palette.add(name);
                }
            }
            return new StructureNbt(size, palette);
        }
    }

    // ------------------------------------------------------------------ 1. load districts + modules

    static Map<String, Map<String, Object>> loadAllDistricts() throws IOException {
        Map<String, Map<String, Object>> out = new TreeMap<>();
        Path base = Paths.get(DATA, "firstcrusade", "hive_districts");
        try (Stream<Path> files = Files.list(base)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(".json")) continue;
                String rl = "firstcrusade:" + fn.substring(0, fn.length() - 5);
                try {
                    out.put(rl, readJson(p));
                } catch (Exception e) {
                    err("invalid-json", "district " + rl + ": " + e);
                }
            }
        }
        return out;
    }

    static Map<String, Map<String, Object>> loadAllModules() throws IOException {
        Map<String, Map<String, Object>> out = new TreeMap<>();
        Path base = Paths.get(DATA, "firstcrusade", "hive_modules");
        try (Stream<Path> files = Files.walk(base)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".json")).sorted()::iterator) {
                String rel = base.relativize(p).toString().replace('\\', '/');
                String rl = "firstcrusade:" + rel.substring(0, rel.length() - 5);
                try {
                    out.put(rl, readJson(p));
                } catch (Exception e) {
                    err("invalid-json", "module " + rl + ": " + e);
                }
            }
        }
        return out;
    }

    static Set<String> allTemplateFiles() throws IOException {
        Set<String> out = new TreeSet<>();
        Path base = Paths.get(DATA, "firstcrusade", "structures");
        try (Stream<Path> files = Files.walk(base)) {
            files.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".nbt"))
                 .forEach(p -> {
                     String rel = base.relativize(p).toString().replace('\\', '/');
                     out.add("firstcrusade:" + rel.substring(0, rel.length() - 4));
                 });
        }
        return out;
    }

    // ------------------------------------------------------------------ 2. reference resolution

    static void checkReferences(Map<String, Map<String, Object>> districts,
                                 Map<String, Map<String, Object>> modules,
                                 Set<String> templatesOnDisk) {
        Set<String> referencedModules = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> de : districts.entrySet()) {
            String did = de.getKey();
            Map<String, String> seenOffsets = new LinkedHashMap<>();
            Object modulesObj = de.getValue().get("modules");
            if (modulesObj == null) continue;
            for (Object me : asList(modulesObj)) {
                Map<String, Object> entry = asMap(me);
                String mrl = asString(entry.get("module"));
                referencedModules.add(mrl);
                if (!modules.containsKey(mrl)) {
                    err("missing-module", "district " + did + " -> module " + mrl + " has no hive_modules/*.json");
                    continue;
                }
                int[] off = asIntArray(entry.get("offset"));
                String offKey = off[0] + "," + off[1] + "," + off[2];
                if (seenOffsets.containsKey(offKey)) {
                    err("duplicate-placement", "district " + did + ": modules " + seenOffsets.get(offKey)
                            + " and " + mrl + " share offset [" + offKey + "]");
                }
                seenOffsets.put(offKey, mrl);
            }
        }

        Set<String> referencedTemplates = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> me : modules.entrySet()) {
            String mid = me.getKey();
            Object trlObj = me.getValue().get("template");
            if (trlObj == null) {
                err("bad-module", "module " + mid + " has no 'template' field");
                continue;
            }
            String trl = asString(trlObj);
            referencedTemplates.add(trl);
            if (!templatesOnDisk.contains(trl)) {
                err("template-not-found", "module " + mid + " -> template " + trl
                        + " (expected " + templateNbtPath(trl) + ")");
            }
        }

        for (String mid : modules.keySet()) {
            if (!referencedModules.contains(mid)) {
                warn("orphan-module", "module " + mid + " exists but is not referenced by any district");
            }
        }
        for (String trl : templatesOnDisk) {
            if (!referencedTemplates.contains(trl)) {
                warn("orphan-template", "template " + trl + " exists but is not referenced by any module");
            }
        }
        for (String expected : EXPECTED_DISTRICT_IDS) {
            if (!districts.containsKey(expected)) {
                err("missing-district", "HiveCityLayout expects district " + expected + " but it has no JSON");
            }
        }
    }

    // ------------------------------------------------------------------ 3. NBT size + palette collection

    static Set<String> checkNbtAndCollectPalette(Map<String, Map<String, Object>> modules) {
        Set<String> usedBlocks = new TreeSet<>();
        for (Map.Entry<String, Map<String, Object>> me : modules.entrySet()) {
            String mid = me.getKey();
            Map<String, Object> mj = me.getValue();
            Object trlObj = mj.get("template");
            if (trlObj == null) continue;
            String trl = asString(trlObj);
            Path path = templateNbtPath(trl);
            if (!Files.isRegularFile(path)) continue; // already reported
            StructureNbt s;
            try {
                s = readStructureNbt(path);
            } catch (Exception e) {
                err("invalid-nbt", "module " + mid + " -> " + trl + ": failed to parse (" + e + ")");
                continue;
            }
            Object declaredObj = mj.get("size");
            if (declaredObj != null) {
                int[] declared = asIntArray(declaredObj);
                if (!java.util.Arrays.equals(declared, s.size())) {
                    err("size-mismatch", "module " + mid + ": declared size " + java.util.Arrays.toString(declared)
                            + " != actual NBT size " + java.util.Arrays.toString(s.size()));
                }
            }
            for (String key : s.palette()) {
                String base = key.split("\\|")[0];
                String[] ns = rlSplit(base);
                if (!ns[0].equals("firstcrusade")) continue;
                String pathPart = ns[1];
                String name = pathPart.contains("/") ? pathPart.substring(pathPart.lastIndexOf('/') + 1) : pathPart;
                if (name.equals("air")) continue;
                usedBlocks.add(name);
            }
        }
        return usedBlocks;
    }

    // ------------------------------------------------------------------ 4. blockstate/model/texture chain

    @SuppressWarnings("unchecked")
    static Set<String> resolveModelRefsFromBlockstate(Map<String, Object> bs) {
        Set<String> models = new LinkedHashSet<>();
        if (bs.containsKey("variants")) {
            for (Object v : asMap(bs.get("variants")).values()) takeApply(v, models);
        }
        if (bs.containsKey("multipart")) {
            for (Object caseObj : asList(bs.get("multipart"))) {
                Map<String, Object> c = asMap(caseObj);
                if (c.containsKey("apply")) takeApply(c.get("apply"), models);
            }
        }
        return models;
    }

    static void takeApply(Object apply, Set<String> models) {
        List<Object> entries = apply instanceof List ? asList(apply) : List.of(apply);
        for (Object e : entries) {
            if (e instanceof Map) {
                Object model = asMap(e).get("model");
                if (model != null) models.add(asString(model));
            }
        }
    }

    static Set<String> resolveTexturesFromModel(Map<String, Object> model) {
        Set<String> out = new LinkedHashSet<>();
        Object texturesObj = model.get("textures");
        if (texturesObj == null) return out;
        for (Object v : asMap(texturesObj).values()) {
            if (v instanceof String && !((String) v).startsWith("#")) out.add((String) v);
        }
        return out;
    }

    static void checkAssets(Set<String> usedBlocks) {
        Map<String, Set<String>> modelCache = new LinkedHashMap<>();
        for (String name : usedBlocks) {
            String bsRl = "firstcrusade:" + name;
            Path bsPath = blockstatePath(bsRl);
            if (!Files.isRegularFile(bsPath)) {
                err("missing-blockstate", "block " + bsRl + " used in a template but no blockstate at " + bsPath);
                continue;
            }
            Map<String, Object> bsJson;
            try {
                bsJson = readJson(bsPath);
            } catch (Exception e) {
                err("invalid-json", "blockstate " + bsRl + ": " + e);
                continue;
            }
            Set<String> modelRls = resolveModelRefsFromBlockstate(bsJson);
            if (modelRls.isEmpty()) {
                warn("empty-blockstate", "blockstate " + bsRl + " declares no variants/multipart models");
            }
            for (String mrl : modelRls) {
                String[] ns = rlSplit(mrl);
                if (!ns[0].equals("firstcrusade")) continue;
                Set<String> textures;
                if (modelCache.containsKey(mrl)) {
                    textures = modelCache.get(mrl);
                    if (textures == null) {
                        err("missing-model", "block " + bsRl + " -> model " + mrl + " (expected " + modelPath(mrl) + ")");
                        continue;
                    }
                } else {
                    Path mpath = modelPath(mrl);
                    if (!Files.isRegularFile(mpath)) {
                        err("missing-model", "block " + bsRl + " -> model " + mrl + " (expected " + mpath + ")");
                        modelCache.put(mrl, null);
                        continue;
                    }
                    Map<String, Object> modelJson;
                    try {
                        modelJson = readJson(mpath);
                    } catch (Exception e) {
                        err("invalid-json", "model " + mrl + ": " + e);
                        modelCache.put(mrl, null);
                        continue;
                    }
                    if (modelJson.containsKey("parent") && !modelJson.containsKey("textures")) {
                        info("uses-parent", "model " + mrl + " inherits textures via parent=" + modelJson.get("parent"));
                        modelCache.put(mrl, Set.of());
                        continue;
                    }
                    textures = resolveTexturesFromModel(modelJson);
                    modelCache.put(mrl, textures);
                }
                for (String trl : textures) {
                    String[] tns = rlSplit(trl);
                    if (!tns[0].equals("firstcrusade")) continue;
                    Path tpath = texturePath(trl);
                    if (!Files.isRegularFile(tpath)) {
                        err("missing-texture", "block " + bsRl + " -> model " + mrl + " -> texture " + trl
                                + " (expected " + tpath + ")");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 5. socket seam validation

    static final String[] RING = {"north", "east", "south", "west"};

    static int ringIndex(String d) {
        for (int i = 0; i < 4; i++) if (RING[i].equals(d)) return i;
        return -1;
    }

    static int[] rotatedSize(int[] size, int rot) {
        if (rot == 1 || rot == 3) return new int[]{size[2], size[1], size[0]};
        return size.clone();
    }

    static String localFaceForWorldFace(String worldFace, int rot) {
        int idx = ringIndex(worldFace);
        if (idx < 0) return worldFace; // up/down unaffected by Y rotation
        int inverseSteps = (4 - rot) % 4;
        return RING[(idx + inverseSteps) % 4];
    }

    @SuppressWarnings("unchecked")
    static String socketAt(Object socketsObj, String worldFace, int rot) {
        Map<String, String> sockets = socketsObj == null ? Map.of() : (Map<String, String>) (Map<?, ?>) asMap(socketsObj);
        if (worldFace.equals("up") || worldFace.equals("down")) {
            return sockets.getOrDefault(worldFace, "sealed");
        }
        String local = localFaceForWorldFace(worldFace, rot);
        return sockets.getOrDefault(local, "sealed");
    }

    static boolean fits(String a, String b) { return !a.equals("sealed") && a.equals(b); }

    static String touchingFace(int[] aMin, int[] aSize, int[] bMin, int[] bSize) {
        int ax0 = aMin[0], ay0 = aMin[1], az0 = aMin[2];
        int ax1 = ax0 + aSize[0], ay1 = ay0 + aSize[1], az1 = az0 + aSize[2];
        int bx0 = bMin[0], by0 = bMin[1], bz0 = bMin[2];
        int bx1 = bx0 + bSize[0], by1 = by0 + bSize[1], bz1 = bz0 + bSize[2];
        boolean xo = ax0 < bx1 && bx0 < ax1;
        boolean yo = ay0 < by1 && by0 < ay1;
        boolean zo = az0 < bz1 && bz0 < az1;
        if (ax1 == bx0 && yo && zo) return "east";
        if (bx1 == ax0 && yo && zo) return "west";
        if (az1 == bz0 && xo && yo) return "south";
        if (bz1 == az0 && xo && yo) return "north";
        if (ay1 == by0 && xo && zo) return "up";
        if (by1 == ay0 && xo && zo) return "down";
        return null;
    }

    record PlacedModule(String id, int[] min, int[] size, int rot, Object sockets) {}

    static int[] seamCounts(Map<String, Map<String, Object>> districts, Map<String, Map<String, Object>> modules) {
        int seamTotal = 0, seamBad = 0;
        for (Map.Entry<String, Map<String, Object>> de : districts.entrySet()) {
            String did = de.getKey();
            List<PlacedModule> placed = new ArrayList<>();
            Object modulesObj = de.getValue().get("modules");
            if (modulesObj == null) continue;
            for (Object me : asList(modulesObj)) {
                Map<String, Object> entry = asMap(me);
                String mrl = asString(entry.get("module"));
                Map<String, Object> mj = modules.get(mrl);
                if (mj == null) continue; // already reported
                int rot = entry.containsKey("rotation") ? asInt(entry.get("rotation")) : 0;
                int[] size = rotatedSize(asIntArray(mj.get("size")), rot);
                int[] off = asIntArray(entry.get("offset"));
                placed.add(new PlacedModule(mrl, off, size, rot, mj.get("sockets")));
            }
            for (int i = 0; i < placed.size(); i++) {
                for (int j = 0; j < placed.size(); j++) {
                    if (i == j) continue;
                    PlacedModule a = placed.get(i), b = placed.get(j);
                    String face = touchingFace(a.min(), a.size(), b.min(), b.size());
                    if (face == null || face.equals("west") || face.equals("north") || face.equals("down")) continue;
                    seamTotal++;
                    String sa = socketAt(a.sockets(), face, a.rot());
                    String opposite = switch (face) {
                        case "north" -> "south"; case "south" -> "north";
                        case "east" -> "west"; case "west" -> "east";
                        case "up" -> "down"; default -> "up";
                    };
                    String sb = socketAt(b.sockets(), opposite, b.rot());
                    if (!fits(sa, sb)) {
                        seamBad++;
                        err("socket-mismatch", "district " + did + ": " + a.id() + " " + face
                                + " [" + sa + "] <-> [" + sb + "] " + b.id());
                    }
                }
            }
        }
        return new int[]{seamTotal, seamBad};
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        Map<String, Map<String, Object>> districts = loadAllDistricts();
        Map<String, Map<String, Object>> modules = loadAllModules();
        Set<String> templatesOnDisk = allTemplateFiles();

        checkReferences(districts, modules, templatesOnDisk);
        Set<String> usedBlocks = checkNbtAndCollectPalette(modules);
        checkAssets(usedBlocks);
        int[] seams = seamCounts(districts, modules);

        StringBuilder md = new StringBuilder();
        md.append("# Hive City — static validation report\n\n");
        md.append("Districts scanned: ").append(districts.size())
          .append("  |  Modules scanned: ").append(modules.size())
          .append("  |  Templates on disk: ").append(templatesOnDisk.size())
          .append("  |  Unique firstcrusade blocks in use: ").append(usedBlocks.size()).append("\n\n");
        md.append("Seams checked: ").append(seams[0]).append("  |  Seam mismatches: ").append(seams[1]).append("\n\n");

        md.append("## Errors (").append(errors.size()).append(")\n");
        appendByCategory(md, errors);
        md.append("\n## Warnings (").append(warnings.size()).append(")\n");
        appendByCategory(md, warnings);
        if (!infos.isEmpty()) {
            md.append("\n## Info (").append(infos.size()).append(")\n");
            for (Finding f : infos) md.append("- [").append(f.category()).append("] ").append(f.message()).append('\n');
        }

        Path outPath = Paths.get(OUT_MD);
        Files.createDirectories(outPath.getParent());
        Files.write(outPath, md.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("Districts: " + districts.size() + "  Modules: " + modules.size()
                + "  Templates: " + templatesOnDisk.size() + "  Unique blocks: " + usedBlocks.size());
        System.out.println("Seams checked: " + seams[0] + "  mismatches: " + seams[1]);
        System.out.println("ERRORS: " + errors.size() + "   WARNINGS: " + warnings.size());
        if (!errors.isEmpty()) {
            System.out.println("\n-- ERROR SUMMARY --");
            for (Map.Entry<String, Long> e : countByCategory(errors).entrySet()) {
                System.out.println("  " + e.getKey() + ": " + e.getValue());
            }
        }
        if (!warnings.isEmpty()) {
            System.out.println("\n-- WARNING SUMMARY --");
            for (Map.Entry<String, Long> e : countByCategory(warnings).entrySet()) {
                System.out.println("  " + e.getKey() + ": " + e.getValue());
            }
        }
        System.out.println("\nFull report: " + OUT_MD);
        System.exit(errors.isEmpty() ? 0 : 1);
    }

    static Map<String, Long> countByCategory(List<Finding> findings) {
        Map<String, Long> out = new TreeMap<>();
        for (Finding f : findings) out.merge(f.category(), 1L, Long::sum);
        return out;
    }

    static void appendByCategory(StringBuilder md, List<Finding> findings) {
        Map<String, List<String>> byCat = new TreeMap<>();
        for (Finding f : findings) byCat.computeIfAbsent(f.category(), k -> new ArrayList<>()).add(f.message());
        for (Map.Entry<String, List<String>> e : byCat.entrySet()) {
            md.append("\n### ").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
            for (String msg : e.getValue()) md.append("- ").append(msg).append('\n');
        }
    }
}
