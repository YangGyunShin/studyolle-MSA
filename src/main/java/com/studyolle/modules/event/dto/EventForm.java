package com.studyolle.modules.event.dto;

import com.studyolle.modules.event.entity.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 이벤트(모임) 생성/수정 폼 데이터를 담는 DTO (Data Transfer Object)
 *
 * [이 클래스의 역할]
 *
 * 사용자가 웹 폼에서 입력한 모임 정보를 Controller까지 전달하는 "운반 객체"이다.
 * Entity(Event)에 직접 바인딩하지 않고 DTO를 사용하는 이유는:
 *
 *   1. 폼 입력값과 엔티티 필드가 항상 1:1 대응하지 않는다
 *      → Event 엔티티에는 id, study, createdBy, createdDateTime 등
 *        폼에서 입력하지 않는 필드가 많다
 *   2. 유효성 검증(@Valid)을 엔티티가 아닌 폼 객체에 두어 관심사를 분리한다
 *   3. 엔티티 구조가 변경되어도 폼 바인딩에는 영향이 없다
 *
 * [데이터 흐름]
 *
 *   [생성 시]
 *   웹 폼 입력 → EventForm(DTO) → @Valid 검증 → ModelMapper → Event(Entity) → DB 저장
 *
 *   [수정 시]
 *   DB 조회 → Event(Entity) → ModelMapper → EventForm(DTO) → 웹 폼에 초기값 표시
 *   → 사용자 수정 → EventForm(DTO) → @Valid 검증 → ModelMapper → Event(Entity) → dirty checking 반영
 *
 *
 * [유효성 검증 - 2단계 구조]
 *
 * 이 폼 객체는 두 종류의 유효성 검증을 거친다:
 *
 *   1단계: JSR-303/380 어노테이션 기반 검증 (이 클래스에 선언된 @NotBlank, @Min 등)
 *     → Spring이 @Valid를 만나면 자동으로 실행
 *     → 단순한 필드별 규칙 검증 (빈값, 길이, 최솟값 등)
 *
 *   2단계: EventValidator 커스텀 검증
 *     → @InitBinder로 등록되어 1단계와 동시에 실행
 *     → 필드 간 상호 의존적인 복합 규칙 검증 (날짜 순서, 기존 인원과 비교 등)
 *     → JSR 어노테이션만으로는 표현할 수 없는 비즈니스 규칙을 검증
 *
 * [시간 필드의 순서 관계]
 *
 * 모임의 시간 흐름은 반드시 다음 순서를 지켜야 한다:
 *
 *   현재 시각 < endEnrollmentDateTime < startDateTime < endDateTime
 *   (현재)      (신청 마감)              (모임 시작)      (모임 종료)
 *
 *   예시:
 *     현재: 2025-01-01 10:00
 *     신청 마감: 2025-01-05 23:59  (현재보다 미래)
 *     모임 시작: 2025-01-10 14:00  (신청 마감보다 미래)
 *     모임 종료: 2025-01-10 17:00  (모임 시작보다 미래)
 *
 *   이 순서가 어긋나면 EventValidator에서 에러를 발생시킨다.
 */
@Data
public class EventForm {

    /** 모임 제목 - 필수 입력, 최대 50자 */
    @NotBlank
    @Length(max = 50)
    private String title;

    /** 모임 상세 설명 - 선택 입력 (제한 없음) */
    private String description;

    /**
     * 모집 방식 - 기본값 FCFS(선착순)
     *
     * - FCFS: 선착순 자동 승인
     * - CONFIRMATIVE: 관리자 수동 승인
     * - 생성 시 선택 가능하지만, 수정 시에는 변경 불가
     *   (EventController.updateEventSubmit()에서 기존값으로 강제 덮어씀)
     */
    private EventType eventType = EventType.FCFS;

    /**
     * 참가 신청 마감 일시
     *
     * - @DateTimeFormat(iso = ISO.DATE_TIME): HTML 폼의 datetime-local 입력값을
     *   ISO 8601 형식(예: "2025-01-05T23:59:00")으로 파싱하여 LocalDateTime으로 변환
     * - EventValidator 검증: 현재 시각보다 미래여야 함
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endEnrollmentDateTime;

    /**
     * 모임 시작 일시
     *
     * - EventValidator 검증: endEnrollmentDateTime보다 미래여야 함
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDateTime;

    /**
     * 모임 종료 일시
     *
     * - EventValidator 검증: startDateTime, endEnrollmentDateTime 모두보다 미래여야 함
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDateTime;

    /**
     * 모집 정원 - 최소 2명 이상
     *
     * - @Min(2): 1명만 모집하는 모임은 의미가 없으므로 최소 2명
     * - 수정 시 추가 검증: 현재 승인된 인원보다 작게 줄일 수 없음
     *   (EventValidator.validateUpdateForm()에서 검증)
     */
    @Min(2)
    private Integer limitOfEnrollments = 2;
}