package com.studyolle.event.repository;

import com.studyolle.event.entity.Event;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // enrollments를 함께 조회 (N+1 방지)
    @EntityGraph(attributePaths = "enrollments")
    List<Event> findByStudyPath(String studyPath);

    @EntityGraph(attributePaths = "enrollments")
    Optional<Event> findWithEnrollmentsById(Long id);
}