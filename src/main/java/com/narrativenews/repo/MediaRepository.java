package com.narrativenews.repo;

import com.narrativenews.domain.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {}
