package com.narrativenews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narrativenews.config.AppProperties;
import com.narrativenews.web.Dtos.TextBlock;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.ITessAPI.TessPageSegMode;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text of a scanned page with Tesseract (free, runs locally; Hindi + English).
 * Output: text blocks in reading order with their position on the page, so readers can switch to a
 * text view or tap a story on the page.
 */
@Service
public class OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final AppProperties.Ocr cfg;
    private final ObjectMapper json;
    private final boolean available;

    public OcrService(AppProperties props, ObjectMapper json) {
        this.cfg = props.ocr();
        this.json = json;
        this.available = cfg.enabled() && languageFilesPresent();
    }

    /** False when OCR is switched off or its language files are missing; everything else works without it. */
    public boolean available() { return available; }

    /** JSON array of {@link TextBlock}s for one page image. */
    public String readBlocksJson(BufferedImage img) throws Exception {
        Tesseract t = new Tesseract();
        t.setDatapath(cfg.datapath());
        t.setLanguage(cfg.languages());
        t.setPageSegMode(TessPageSegMode.PSM_AUTO);
        double pageW = img.getWidth(), pageH = img.getHeight();

        // Tesseract blocks are usually single paragraphs. Join consecutive blocks of the same column
        // so a tap selects a whole piece of a story (headline + its paragraphs) instead of one paragraph.
        List<Rectangle> boxes = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        for (Word block : t.getWords(img, TessPageIteratorLevel.RIL_BLOCK)) {
            String text = block.getText().replaceAll("\\s*\\n\\s*", " ").replaceAll("\\s{2,}", " ").trim();
            if (text.length() < 2) continue;
            Rectangle r = block.getBoundingBox();
            int last = boxes.size() - 1;
            if (last >= 0 && continuesBelow(boxes.get(last), r, pageH)) {
                boxes.get(last).add(r);
                texts.get(last).append("\n\n").append(text);
            } else {
                boxes.add(new Rectangle(r));
                texts.add(new StringBuilder(text));
            }
        }
        List<TextBlock> out = new ArrayList<>(boxes.size());
        for (int i = 0; i < boxes.size(); i++) {
            Rectangle r = boxes.get(i);
            out.add(new TextBlock(frac(r.x / pageW), frac(r.y / pageH), frac(r.width / pageW), frac(r.height / pageH), texts.get(i).toString()));
        }
        return json.writeValueAsString(out);
    }

    /** b starts just below a and shares most of its column. */
    private static boolean continuesBelow(Rectangle a, Rectangle b, double pageH) {
        int overlap = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        int gap = b.y - (a.y + a.height);
        return overlap > 0.6 * Math.min(a.width, b.width) && gap > -10 && gap < pageH * 0.02;
    }

    private static double frac(double v) { return Math.round(v * 10000) / 10000.0; }

    private boolean languageFilesPresent() {
        for (String lang : cfg.languages().split("\\+")) {
            File f = new File(cfg.datapath(), lang + ".traineddata");
            if (!f.isFile()) {
                log.warn("Text recognition is off: {} not found (set TESSDATA_PATH)", f.getAbsolutePath());
                return false;
            }
        }
        return true;
    }
}
