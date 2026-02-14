import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class TextureGenerator {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, 64, 64);

        // Head (blue tones - friendly)
        g.setColor(new Color(52, 152, 219));
        g.fillRect(8, 0, 8, 8);   // top
        g.fillRect(8, 8, 8, 8);   // front
        g.setColor(new Color(41, 128, 185));
        g.fillRect(0, 8, 8, 8);   // right
        g.fillRect(16, 8, 8, 8);  // left
        g.fillRect(24, 8, 8, 8);  // back
        g.fillRect(16, 0, 8, 8);  // bottom
        // Eyes - bright cyan
        g.setColor(new Color(0, 255, 255));
        g.fillRect(10, 12, 2, 1);
        g.fillRect(14, 12, 2, 1);
        // Smile
        g.setColor(new Color(241, 196, 15));
        g.fillRect(11, 14, 4, 1);

        // Body (gold/yellow tones)
        g.setColor(new Color(241, 196, 15));
        g.fillRect(20, 20, 8, 12);  // front
        g.setColor(new Color(243, 156, 18));
        g.fillRect(16, 20, 4, 12);  // right
        g.fillRect(28, 20, 4, 12);  // left
        g.fillRect(32, 20, 8, 12);  // back
        g.setColor(new Color(230, 180, 20));
        g.fillRect(20, 16, 8, 4);   // top

        // Arms (blue)
        g.setColor(new Color(52, 152, 219));
        g.fillRect(44, 20, 4, 12);
        g.fillRect(40, 20, 4, 12);
        g.fillRect(48, 20, 4, 12);
        g.fillRect(52, 20, 4, 12);

        // Legs (dark blue)
        g.setColor(new Color(41, 128, 185));
        g.fillRect(4, 20, 4, 12);
        g.fillRect(0, 20, 4, 12);
        g.fillRect(8, 20, 4, 12);
        g.fillRect(12, 20, 4, 12);

        g.dispose();
        File out = new File("src/main/resources/assets/companion/textures/entity/companion.png");
        out.getParentFile().mkdirs();
        ImageIO.write(image, "png", out);
        System.out.println("Texture generated: " + out.getAbsolutePath());
    }
}
