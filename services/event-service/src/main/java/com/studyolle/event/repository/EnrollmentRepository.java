package com.studyolle.event.repository;

import com.studyolle.event.entity.Enrollment;
import com.studyolle.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByEventAndAccountId(Event event, Long accountId);

    Optional<Enrollment> findByEventAndAccountId(Event event, Long accountId);

    List<Enrollment> findByAccountId(Long accountId);
}