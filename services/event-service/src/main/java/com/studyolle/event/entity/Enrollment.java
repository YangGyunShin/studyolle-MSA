package com.studyolle.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    private Event event;

    @Column(nullable = false)
    private Long accountId;

    private LocalDateTime enrolledAt;
    private boolean accepted;
    private boolean attended;
}