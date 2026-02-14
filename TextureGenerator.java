import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class TextureGenerator {
    public static void main(String[] args) throws Exception {
        int width = 64;
        int height = 64;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Transparent background
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, width, height);

        // === HEAD (top: 0,0 front: 8,8 size 8x8) ===
        // Head top
        g.setColor(new Color(20, 20, 25));
        g.fillRect(8, 0, 8, 8);
        // Head bottom
        g.setColor(new Color(15, 15, 20));
        g.fillRect(16, 0, 8, 8);
        // Head front
        g.setColor(new Color(25, 25, 30));
        g.fillRect(8, 8, 8, 8);
        // Head right
        g.setColor(new Color(22, 22, 28));
        g.fillRect(0, 8, 8, 8);
        // Head left
        g.setColor(new Color(22, 22, 28));
        g.fillRect(16, 8, 8, 8);
        // Head back
        g.setColor(new Color(18, 18, 22));
        g.fillRect(24, 8, 8, 8);

        // Red glowing eyes on face
        g.setColor(new Color(255, 0, 0));
        g.fillRect(10, 12, 2, 1);  // Left eye
        g.fillRect(14, 12, 2, 1);  // Right eye
        // Eye glow
        g.setColor(new Color(200, 0, 0));
        g.fillRect(10, 11, 2, 1);
        g.fillRect(14, 11, 2, 1);

        // Mouth - sinister grin
        g.setColor(new Color(100, 0, 0));
        g.fillRect(11, 14, 1, 1);
        g.fillRect(14, 14, 1, 1);

        // === BODY (20,20 size 8x12x4) ===
        // Body front
        g.setColor(new Color(15, 15, 18));
        g.fillRect(20, 20, 8, 12);
        // Body right
        g.setColor(new Color(12, 12, 16));
        g.fillRect(16, 20, 4, 12);
        // Body left
        g.fillRect(28, 20, 4, 12);
        // Body back
        g.setColor(new Color(10, 10, 14));
        g.fillRect(32, 20, 8, 12);
        // Body top
        g.setColor(new Color(18, 18, 22));
        g.fillRect(20, 16, 8, 4);

        // Dark veins on body
        g.setColor(new Color(80, 0, 0));
        g.fillRect(22, 22, 1, 4);
        g.fillRect(25, 24, 1, 6);

        // === ARMS (44,20 size 4x12x4) ===
        // Right arm
        g.setColor(new Color(18, 18, 22));
        g.fillRect(44, 20, 4, 12);
        g.fillRect(40, 20, 4, 12);
        g.fillRect(48, 20, 4, 12);
        g.fillRect(52, 20, 4, 12);

        // Claws on arm
        g.setColor(new Color(120, 0, 0));
        g.fillRect(44, 31, 1, 1);
        g.fillRect(46, 31, 1, 1);

        // === LEGS (4,20 size 4x12x4) ===
        // Right leg
        g.setColor(new Color(12, 12, 16));
        g.fillRect(4, 20, 4, 12);
        g.fillRect(0, 20, 4, 12);
        g.fillRect(8, 20, 4, 12);
        g.fillRect(12, 20, 4, 12);

        // Add noise/texture detail
        java.util.Random rand = new java.util.Random(42);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                if (alpha > 0 && rand.nextDouble() > 0.7) {
                    int r = Math.max(0, ((rgb >> 16) & 0xFF) - rand.nextInt(8));
                    int gx = Math.max(0, ((rgb >> 8) & 0xFF) - rand.nextInt(8));
                    int b = Math.max(0, (rgb & 0xFF) - rand.nextInt(8));
                    image.setRGB(x, y, (alpha << 24) | (r << 16) | (gx << 8) | b);
                }
            }
        }

        g.dispose();

        File outputFile = new File("src/main/resources/assets/stalker/textures/entity/stalker.png");
        outputFile.getParentFile().mkdirs();
        ImageIO.write(image, "png", outputFile);

        System.out.println("Texture generated: " + outputFile.getAbsolutePath());
    }
}
