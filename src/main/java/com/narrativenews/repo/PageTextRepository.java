package com.narrativenews.repo;

import com.narrativenews.domain.PageText;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PageTextRepository extends JpaRepository<PageText, UUID> {}
