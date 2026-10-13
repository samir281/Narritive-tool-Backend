package com.narrativenews.service;

import com.narrativenews.config.AppProperties;
import com.narrativenews.domain.Admin;
import com.narrativenews.domain.Edition;
import com.narrativenews.domain.Enums.PdfStatus;
import com.narrativenews.repo.AdminRepository;
import com.narrativenews.repo.EditionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On startup: create the first admin if none exists, and fail editions whose render was interrupted by a restart.
 * Not in the OCR worker, which runs next to the live server and must not touch editions it is rendering.
 */
@Component
@ConditionalOnProperty(name = "app.ocr.worker-run", havingValue = "false", matchIfMissing = true)
public class BootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private final AdminRepository admins;
    private final EditionRepository editions;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public BootstrapRunner(AdminRepository admins, EditionRepository editions, PasswordEncoder encoder, AppProperties props) {
        this.admins = admins; this.editions = editions; this.encoder = encoder; this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.count() == 0) {
            Admin a = new Admin();
            a.setName("Administrator");
            a.setEmail(props.bootstrap().adminEmail().toLowerCase());
            a.setPasswordHash(encoder.encode(props.bootstrap().adminPassword()));
            admins.save(a);
            log.warn("Created first admin '{}'. Change its password / create your own admin, then disable this one.", a.getEmail());
        }
        for (Edition e : editions.findByPdfStatus(PdfStatus.PROCESSING)) {
            e.markPdfFailed("Server restarted while processing. Please upload the PDF again.");
            editions.save(e);
        }
    }
}
