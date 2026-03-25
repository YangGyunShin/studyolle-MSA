package com.studyolle.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
public class Enrollment {

    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    private Event event;

    // MSA: Account 엔티티 대신 accountId로 참조
    @Column(nullable = false)
    private Long accountId;

    private LocalDateTime enrolledAt;
    private boolean accepted;
    private boolean attended;
}