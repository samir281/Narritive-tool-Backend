package com.narrativenews.service;

import com.narrativenews.domain.EditionPage;
import com.narrativenews.repo.EditionPageRepository;
import com.narrativenews.repo.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * One-shot text reader for when the web server is too small for OCR (the free 512 MB host).
 * Started with app.ocr.worker-run=true (see .github/workflows/ocr.yml): reads every page that has
 * no text yet, saves the result to the shared database, then the process exits.
 */
@Component
@ConditionalOnProperty(name = "app.ocr.worker-run", havingValue = "true")
public class OcrWorker implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OcrWorker.class);
    private final EditionPageRepository pages;
    private final MediaRepository media;
    private final PdfRenderService renderer;
    private final OcrService ocr;
    private final ConfigurableApplicationContext ctx;

    public OcrWorker(EditionPageRepository pages, MediaRepository media, PdfRenderService renderer,
                     OcrService ocr, ConfigurableApplicationContext ctx) {
        this.pages = pages; this.media = media; this.renderer = renderer; this.ocr = ocr; this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        if (!ocr.available()) {
            log.error("Text recognition is not available here (language files missing or OCR_ENABLED=false)");
            code = 1;
        } else {
            List<EditionPage> todo = pages.findWithoutText();
            log.info("{} pages need text", todo.size());
            for (EditionPage p : todo) {
                long t0 = System.currentTimeMillis();
                media.findById(p.getLargeMediaId()).ifPresent(m -> renderer.recognizePage(p.getId(), m.getData()));
                log.info("Page {} of edition {}: {} ms", p.getPageNo(), p.getEditionId(), System.currentTimeMillis() - t0);
            }
        }
        int exitCode = code;
        System.exit(SpringApplication.exit(ctx, () -> exitCode));
    }
}
