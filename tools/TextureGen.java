import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Procedurally paints 64x64 humanoid-layout entity textures for the elite Imperial units, in
 * their faction colour schemes (Ultramarines blue Space Marine, golden Custodes, blue+gold
 * Primarch). Not figure-accurate, but a clear, colour-correct upgrade over flat placeholders.
 *
 * Run: java TextureGen.java <outputDir>
 */
public class TextureGen {
    static BufferedImage img;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : ".";

        // Space Marine (Ultramarines): deep blue armour, gold trim, dark helmet, green lenses.
        paint(0xFF2B50A8, 0xFFC8A23C, 0xFF1B2E66, 0xFF3CFF72, 0xFF18305F, 0xFFEDE3C0, 0, false);
        write(out, "space_marine.png");

        // Custodes: golden armour, dark-red accents, red lenses.
        paint(0xFFCBA32A, 0xFF7A1717, 0xFFB6901F, 0xFFE03A3A, 0xFF8A6A18, 0xFF5A0A0A, 0, false);
        write(out, "custodes.png");

        // Primarch (Guilliman): royal blue with heavy gold trim, white tabard, blue lenses, halo.
        paint(0xFF2550A8, 0xFFD8B53C, 0xFF1C3F8F, 0xFF9FC0FF, 0xFF16305F, 0xFFF0E6C0, 0xFFE9E9E9, true);
        write(out, "primarch.png");

        System.out.println("Textures generated in " + new File(out).getAbsolutePath());
    }

    // base = armour, trim = gold/accent, helmet = head colour, lens = eye lenses,
    // dark = shadow, emblem = chest crest, tabard = optional white cloth (0 = none), halo = ring above head.
    static void paint(int base, int trim, int helmet, int lens, int dark, int emblem, int tabard, boolean halo) {
        img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        // --- Head net (0..32, 0..16) ---
        fill(0, 8, 32, 8, helmet);   // sides/front/back row of head
        fill(8, 0, 16, 8, helmet);   // top + bottom of head
        // top of head slightly lighter
        fill(8, 0, 8, 8, lighten(helmet, 0.12));
        // Face (front 8,8,8,8): visor band + two lenses
        fill(8, 8, 8, 8, helmet);
        fill(8, 11, 8, 2, darken(helmet, 0.35));   // visor slot
        set(9, 12, lens); set(10, 12, lens);       // right lens
        set(13, 12, lens); set(14, 12, lens);      // left lens
        fill(11, 9, 2, 1, trim);                   // forehead ridge
        if (halo) {
            // suggest a halo with a gold band across the top-back of the head
            fill(24, 8, 8, 2, trim);               // back of head crown
            fill(8, 0, 8, 1, trim);
        }

        // --- Body net (16..40, 16..32) ---
        fill(16, 16, 24, 16, base);
        // front of torso (20,20,8,12)
        fill(20, 20, 8, 12, base);
        fill(23, 20, 2, 12, trim);                 // central gold column
        fill(22, 21, 4, 3, emblem);                // chest crest/eagle suggestion
        fill(20, 30, 8, 2, darken(base, 0.25));    // belt shadow
        // back/sides slightly darker
        fill(32, 20, 8, 12, darken(base, 0.12));   // back
        fill(16, 20, 4, 12, darken(base, 0.2));    // right side
        fill(28, 20, 4, 12, darken(base, 0.2));    // left side
        if (tabard != 0) {
            fill(22, 24, 4, 8, tabard);            // hanging tabard over the belt
            fill(23, 24, 2, 8, darken(tabard, 0.1));
        }

        // --- Right arm net (40..56, 16..32) ---
        paintArm(40, base, trim, dark);
        // --- Left arm net (32..48, 48..64) ---
        paintArm(32, base, trim, dark);

        // --- Right leg net (0..16, 16..32) ---
        paintLeg(0, base, dark, tabard);
        // --- Left leg net (16..32, 48..64) ---
        paintLeg(16, base, dark, tabard);
    }

    // Arms get a big gold-trimmed pauldron look on the top rows.
    static void paintArm(int netX, int base, int trim, int dark) {
        fill(netX, 16, 16, 16, base);
        fill(netX, 16, 16, 4, trim);               // pauldron cap (top of arm)
        fill(netX + 4, 20, 4, 12, base);           // front of arm
        fill(netX + 4, 20, 4, 3, trim);            // shoulder trim band
        fill(netX, 29, 16, 3, dark);               // glove/cuff shadow
    }

    static void paintLeg(int netX, int base, int dark, int tabard) {
        fill(netX, 16, 16, 16, base);
        fill(netX + 4, 20, 4, 12, base);           // front of leg
        fill(netX, 29, 16, 3, dark);               // boot
        if (tabard != 0) {
            fill(netX + 4, 20, 4, 6, tabard);      // white cloth over upper leg
        }
    }

    static void fill(int x, int y, int w, int h, int argb) {
        for (int i = x; i < x + w && i < 64; i++) {
            for (int j = y; j < y + h && j < 64; j++) {
                if (i >= 0 && j >= 0) {
                    img.setRGB(i, j, argb);
                }
            }
        }
    }

    static void set(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < 64 && y < 64) {
            img.setRGB(x, y, argb);
        }
    }

    static int lighten(int argb, double f) {
        return shade(argb, 1.0 + f);
    }

    static int darken(int argb, double f) {
        return shade(argb, 1.0 - f);
    }

    static int shade(int argb, double m) {
        int a = (argb >> 24) & 0xFF;
        int r = clamp((int) (((argb >> 16) & 0xFF) * m));
        int g = clamp((int) (((argb >> 8) & 0xFF) * m));
        int b = clamp((int) ((argb & 0xFF) * m));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static void write(String dir, String name) throws Exception {
        File f = new File(dir, name);
        ImageIO.write(img, "png", f);
        System.out.println("wrote " + f.getName());
    }
}
