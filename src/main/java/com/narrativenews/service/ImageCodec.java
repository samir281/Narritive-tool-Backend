package com.narrativenews.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** JPEG encode / downscale helpers using only the JDK (no native libraries, no paid services). */
final class ImageCodec {
    private ImageCodec() {}

    static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            p.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
            ios.flush();
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Scales to the given width (keeps aspect ratio). Returns the same image if it is already narrower.
     * Halves the image step by step before the final resize: one big bicubic jump skips most source pixels
     * and makes small newspaper text look jagged and blurry.
     */
    static BufferedImage scaleToWidth(BufferedImage src, int width) {
        BufferedImage cur = toRgb(src);
        if (cur.getWidth() <= width) return cur;
        float ratio = src.getHeight() / (float) src.getWidth();
        while (cur.getWidth() / 2 >= width) cur = resize(cur, cur.getWidth() / 2, ratio, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (cur.getWidth() != width) cur = resize(cur, width, ratio, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return cur;
    }

    private static BufferedImage resize(BufferedImage src, int width, float ratio, Object interpolation) {
        int h = Math.max(1, Math.round(width * ratio));
        BufferedImage out = new BufferedImage(width, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, width, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** JPEG cannot store alpha, so flatten onto white. */
    static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
