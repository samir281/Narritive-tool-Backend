package com.narrativenews.service;

import com.narrativenews.config.AppProperties;
import com.narrativenews.domain.Edition;
import com.narrativenews.domain.EditionPage;
import com.narrativenews.domain.Media;
import com.narrativenews.domain.PageText;
import com.narrativenews.repo.EditionPageRepository;
import com.narrativenews.repo.EditionRepository;
import com.narrativenews.repo.MediaRepository;
import com.narrativenews.repo.PageTextRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns an uploaded PDF into JPEG page images (two sizes) stored in the database.
 * Uses Apache PDFBox (free, pure Java). Runs on a single background thread.
 */
@Service
public class PdfRenderService {
    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);

    private final AppProperties props;
    private final EditionRepository editions;
    private final EditionPageRepository pages;
    private final MediaRepository media;
    private final PageTextRepository texts;
    private final OcrService ocr;
    private final TransactionTemplate tx;

    public PdfRenderService(AppProperties props, EditionRepository editions, EditionPageRepository pages,
                            MediaRepository media, PageTextRepository texts, OcrService ocr, TransactionTemplate tx) {
        this.props = props; this.editions = editions; this.pages = pages; this.media = media;
        this.texts = texts; this.ocr = ocr; this.tx = tx;
    }

    private record Rendered(int pageNo, byte[] thumb, byte[] small, byte[] large, int width, int height) {}

    @Async("renderExecutor")
    public void render(UUID editionId, byte[] pdf) {
        long t0 = System.currentTimeMillis();
        List<Rendered> rendered;
        List<UUID> pageIds;
        try {
            List<Rendered> r = renderAll(pdf);
            pageIds = tx.execute(s -> replacePages(editionId, r));
            rendered = r;
            log.info("Rendered {} pages for edition {} in {} ms", r.size(), editionId, System.currentTimeMillis() - t0);
        } catch (Throwable t) {
            log.error("PDF render failed for edition {}", editionId, t);
            String msg = t instanceof InvalidPasswordException ? "The PDF is password protected"
                    : (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            tx.executeWithoutResult(s -> editions.findById(editionId).ifPresent(e -> {
                e.markPdfFailed(msg);
                editions.save(e);
            }));
            return;
        }
        recognizeText(pageIds, rendered);
    }

    /**
     * Runs after the pages are already live (takes ~20 s per page). A failed page gets an empty result
     * instead of blocking the others. Same single render thread, so a newer upload waits until this finishes.
     */
    private void recognizeText(List<UUID> pageIds, List<Rendered> rendered) {
        if (!ocr.available()) return;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < pageIds.size(); i++) recognizePage(pageIds.get(i), rendered.get(i).large());
        log.info("Recognized text of {} pages in {} ms", pageIds.size(), System.currentTimeMillis() - t0);
    }

    /** Reads one page's text and stores it (an empty result if reading fails). Skips pages deleted meanwhile. */
    public void recognizePage(UUID pageId, byte[] largeJpeg) {
        if (!pages.existsById(pageId)) return; // edition deleted or PDF replaced meanwhile
        String blocks;
        try {
            blocks = ocr.readBlocksJson(ImageIO.read(new ByteArrayInputStream(largeJpeg)));
        } catch (Throwable t) {
            log.warn("Text recognition failed for page {}", pageId, t);
            blocks = "[]";
        }
        String result = blocks;
        tx.executeWithoutResult(s -> { if (pages.existsById(pageId)) texts.save(new PageText(pageId, result)); });
    }

    private List<Rendered> renderAll(byte[] pdf) throws Exception {
        AppProperties.Render cfg = props.render();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int n = doc.getNumberOfPages();
            if (n == 0) throw new IllegalArgumentException("The PDF has no pages");
            if (n > cfg.maxPages()) throw new IllegalArgumentException("The PDF has " + n + " pages; the limit is " + cfg.maxPages());
            PDFRenderer renderer = new PDFRenderer(doc);
            renderer.setRenderingHints(new RenderingHints(Map.of(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)));
            List<Rendered> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                PDPage page = doc.getPage(i);
                Rendered scanned = scannedPage(page, i + 1, cfg);
                if (scanned != null) { out.add(scanned); continue; }
                int rot = ((page.getRotation() % 360) + 360) % 360;
                float widthPts = (rot == 90 || rot == 270) ? page.getCropBox().getHeight() : page.getCropBox().getWidth();
                float scale = cfg.largeWidth() / widthPts;
                BufferedImage large = renderer.renderImage(i, scale, ImageType.RGB);
                BufferedImage small = ImageCodec.scaleToWidth(large, cfg.smallWidth());
                out.add(new Rendered(i + 1,
                        ImageCodec.jpeg(ImageCodec.scaleToWidth(small, cfg.thumbWidth()), cfg.jpegQuality()),
                        ImageCodec.jpeg(small, cfg.jpegQuality()),
                        ImageCodec.jpeg(large, cfg.jpegQuality()),
                        small.getWidth(), small.getHeight()));
                large.flush();
                small.flush();
            }
            return out;
        }
    }

    /**
     * Scanned papers are usually one JPEG per page. Then the JPEG itself is the zoom image (original quality,
     * no re-encoding) and it is decoded at reduced size for the other two images. This never holds a full-size
     * page in memory, which keeps uploads within a 512 MB server. Returns null for any other kind of page.
     */
    private Rendered scannedPage(PDPage page, int pageNo, AppProperties.Render cfg) {
        try {
            PDResources res = page.getResources();
            if (res == null || res.getFontNames().iterator().hasNext() || page.getRotation() % 360 != 0) return null;
            List<COSName> names = new ArrayList<>();
            res.getXObjectNames().forEach(names::add);
            if (names.size() != 1 || !(res.getXObject(names.get(0)) instanceof PDImageXObject img)) return null;
            if (!"jpg".equals(img.getSuffix()) || img.getSoftMask() != null || img.getWidth() < cfg.smallWidth()) return null;

            byte[] jpeg;
            try (InputStream in = img.getStream().createInputStream(List.of(COSName.DCT_DECODE.getName()))) { jpeg = in.readAllBytes(); }
            ImageReader reader = ImageIO.getImageReadersByFormatName("jpeg").next();
            BufferedImage reduced;
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
                reader.setInput(iis);
                ImageReadParam p = reader.getDefaultReadParam();
                int step = Math.max(1, Math.round(reader.getWidth(0) / (float) cfg.smallWidth()));
                p.setSourceSubsampling(step, step, 0, 0);
                reduced = reader.read(0, p);
            } finally {
                reader.dispose();
            }
            BufferedImage small = ImageCodec.scaleToWidth(reduced, cfg.smallWidth());
            return new Rendered(pageNo,
                    ImageCodec.jpeg(ImageCodec.scaleToWidth(small, cfg.thumbWidth()), cfg.jpegQuality()),
                    ImageCodec.jpeg(small, cfg.jpegQuality()),
                    jpeg, small.getWidth(), small.getHeight());
        } catch (Exception e) {
            log.info("Page {} is not a plain scanned JPEG ({}); rendering it instead", pageNo, e.toString());
            return null;
        }
    }

    /** One transaction: swap old pages for new ones, so readers never see a mix. Returns the new page ids in order. */
    private List<UUID> replacePages(UUID editionId, List<Rendered> rendered) {
        Edition e = editions.findById(editionId).orElseThrow();
        List<UUID> oldMedia = new ArrayList<>();
        List<UUID> oldPages = new ArrayList<>();
        for (EditionPage p : pages.findByEditionIdOrderByPageNo(editionId)) {
            oldPages.add(p.getId());
            oldMedia.add(p.getSmallMediaId());
            oldMedia.add(p.getLargeMediaId());
            if (p.getThumbMediaId() != null) oldMedia.add(p.getThumbMediaId());
        }
        texts.deleteAllById(oldPages);
        pages.deleteByEditionId(editionId);
        pages.flush();
        media.deleteAllById(oldMedia);

        List<UUID> ids = new ArrayList<>(rendered.size());
        for (Rendered r : rendered) {
            EditionPage p = new EditionPage();
            p.setEditionId(editionId);
            p.setPageNo(r.pageNo());
            p.setThumbMediaId(media.save(new Media("image/jpeg", r.thumb())).getId());
            p.setSmallMediaId(media.save(new Media("image/jpeg", r.small())).getId());
            p.setLargeMediaId(media.save(new Media("image/jpeg", r.large())).getId());
            p.setWidth(r.width());
            p.setHeight(r.height());
            ids.add(pages.save(p).getId());
        }
        e.markPdfReady(rendered.size());
        editions.save(e);
        return ids;
    }
}
