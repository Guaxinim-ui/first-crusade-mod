import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Procedurally paints 128x128 entity textures for the elite Imperial units in their faction
 * colours. The top-left 64x64 is the standard humanoid skin (Ultramarines blue Space Marine,
 * golden Custodes, blue+gold Primarch). The right/lower area holds flat colour blocks that the
 * custom bulky model parts (pauldrons, backpack, cape, halo, crest) sample via their UVs:
 *   ARMOR (64,0,64,32)  TRIM (64,32,64,32)  DARK (64,64,64,32)  CLOTH (64,96,64,32)
 * These block coordinates must match the texOffs used in EliteModelLayers.
 *
 * Run: java TextureGen.java <outputDir>
 */
public class TextureGen {
    static BufferedImage img;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : ".";

        // base, trim, helmet, lens, dark, emblem, cloth, halo
        paint(0xFF2B50A8, 0xFFC8A23C, 0xFF1B2E66, 0xFF3CFF72, 0xFF18305F, 0xFFEDE3C0, 0xFFEDE3C0, false);
        write(out, "space_marine.png");

        paint(0xFFCBA32A, 0xFF7A1717, 0xFFB6901F, 0xFFE03A3A, 0xFF8A6A18, 0xFF5A0A0A, 0xFF7A1717, false);
        write(out, "custodes.png");

        paint(0xFF2550A8, 0xFFD8B53C, 0xFF1C3F8F, 0xFF9FC0FF, 0xFF16305F, 0xFFF0E6C0, 0xFFE9E9E9, true);
        write(out, "primarch.png");

        System.out.println("Textures generated in " + new File(out).getAbsolutePath());
    }

    static void paint(int base, int trim, int helmet, int lens, int dark, int emblem, int cloth, boolean halo) {
        img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

        // ---- Humanoid skin (top-left 64x64) ----
        // Head
        fill(0, 8, 32, 8, helmet);
        fill(8, 0, 16, 8, helmet);
        fill(8, 0, 8, 8, lighten(helmet, 0.12));
        fill(8, 8, 8, 8, helmet);
        fill(8, 11, 8, 2, darken(helmet, 0.35));   // visor
        set(9, 12, lens); set(10, 12, lens);
        set(13, 12, lens); set(14, 12, lens);
        fill(11, 9, 2, 1, trim);
        if (halo) {
            fill(24, 8, 8, 2, trim);
            fill(8, 0, 8, 1, trim);
        }

        // Body
        fill(16, 16, 24, 16, base);
        fill(20, 20, 8, 12, base);
        fill(23, 20, 2, 12, trim);
        fill(22, 21, 4, 3, emblem);
        fill(20, 30, 8, 2, darken(base, 0.25));
        fill(32, 20, 8, 12, darken(base, 0.12));
        fill(16, 20, 4, 12, darken(base, 0.2));
        fill(28, 20, 4, 12, darken(base, 0.2));

        // Right arm (40,16), left arm (32,48)
        paintArm(40, 16, base, trim, dark);
        paintArm(32, 48, base, trim, dark);
        // Right leg (0,16), left leg (16,48)
        paintLeg(0, 16, base, dark);
        paintLeg(16, 48, base, dark);

        // ---- Flat colour blocks for the bulky model parts ----
        fill(64, 0, 64, 32, base);    // ARMOR  -> pauldrons, backpack
        fill(64, 32, 64, 32, trim);   // TRIM   -> crest, halo
        fill(64, 64, 64, 32, dark);   // DARK
        fill(64, 96, 64, 32, cloth);  // CLOTH  -> cape
        fill(64, 0, 64, 3, lighten(base, 0.12));
        fill(64, 96, 64, 3, lighten(cloth, 0.1));
    }

    static void paintArm(int netX, int netY, int base, int trim, int dark) {
        fill(netX, netY, 16, 16, base);
        fill(netX, netY, 16, 4, trim);          // pauldron cap
        fill(netX + 4, netY + 4, 4, 12, base);  // front
        fill(netX, netY + 13, 16, 3, dark);     // cuff
    }

    static void paintLeg(int netX, int netY, int base, int dark) {
        fill(netX, netY, 16, 16, base);
        fill(netX + 4, netY + 4, 4, 12, base);
        fill(netX, netY + 13, 16, 3, dark);     // boot
    }

    static void fill(int x, int y, int w, int h, int argb) {
        for (int i = x; i < x + w && i < 128; i++) {
            for (int j = y; j < y + h && j < 128; j++) {
                if (i >= 0 && j >= 0) {
                    img.setRGB(i, j, argb);
                }
            }
        }
    }

    static void set(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < 128 && y < 128) {
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
