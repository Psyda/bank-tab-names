package psyda.banktabnames;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class IconSpriteExtractor {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private final Map<Integer, BufferedImage> extractedIcons = new HashMap<>();
    private boolean isExtracting = false;

    public CompletableFuture<Void> extractAllImages() {
        if (isExtracting) {
            log.debug("Already extracting images, skipping");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            clientThread.invoke(() -> {
                try {
                    isExtracting = true;
                    log.debug("Starting icon extraction...");
                    extractIcons();
                    log.debug("Icon extraction completed. Extracted {} icons", extractedIcons.size());
                } catch (Exception e) {
                    log.error("Error during icon extraction", e);
                } finally {
                    isExtracting = false;
                }
            });
        });
    }

    private void extractIcons() {
        try {
            IndexedSprite[] modIcons = client.getModIcons();
            if (modIcons != null) {
                log.debug("Found {} mod icons for extraction", modIcons.length);

                for (int i = 0; i < modIcons.length && i < 1000; i++) {
                    IndexedSprite sprite = modIcons[i];
                    if (sprite != null) {
                        try {
                            BufferedImage image = spriteToBufferedImage(sprite);
                            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                                extractedIcons.put(i, image);
                            }
                        } catch (Exception e) {
                            log.debug("Failed to extract icon {}: {}", i, e.getMessage());
                        }
                    }
                }
            }
            log.debug("Successfully extracted {} icons", extractedIcons.size());
        } catch (Exception e) {
            log.error("Error extracting icons", e);
        }
    }

    private BufferedImage spriteToBufferedImage(IndexedSprite sprite) {
        if (sprite == null) return null;

        try {
            int width = sprite.getWidth();
            int height = sprite.getHeight();

            if (width <= 0 || height <= 0 || width > 512 || height > 512) {
                return null;
            }

            byte[] pixels = sprite.getPixels();
            int[] palette = sprite.getPalette();

            if (pixels == null || palette == null) return null;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixelIndex = y * width + x;
                    if (pixelIndex < pixels.length) {
                        int paletteIndex = pixels[pixelIndex] & 0xFF;
                        if (paletteIndex < palette.length && paletteIndex > 0) { //Todo: Improve implementation, Something about the logic seems off
                            int color = palette[paletteIndex];
                            if (paletteIndex == 0) {
                                color = 0x00000000; // Transparent
                            } else {
                                color = 0xFF000000 | color; // Opaque
                            }
                            image.setRGB(x, y, color);
                        } else {
                            image.setRGB(x, y, 0x00000000);
                        }
                    }
                }
            }
            return image;
        } catch (Exception e) {
            log.debug("Error converting indexed sprite to BufferedImage: {}", e.getMessage());
            return null;
        }
    }

    public BufferedImage getIcon(int iconId) {
        return extractedIcons.get(iconId);
    }

    public static BufferedImage createPlaceholderImage(int id, int width, int height) {
        BufferedImage placeholder = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = placeholder.createGraphics();

        g2d.setColor(new java.awt.Color(64, 64, 64, 200));
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(java.awt.Color.GRAY);
        g2d.drawRect(0, 0, width - 1, height - 1);

        g2d.setColor(java.awt.Color.WHITE);
        g2d.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, Math.min(width / 4, 8)));
        String idText = String.valueOf(id);
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(idText);
        int textHeight = fm.getHeight();
        g2d.drawString(idText, (width - textWidth) / 2, (height + textHeight) / 2 - 2);

        g2d.dispose();
        return placeholder;
    }
}