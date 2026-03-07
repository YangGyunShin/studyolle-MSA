package com.studyolle.modules.event.validator;

import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.dto.EventForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.LocalDateTime;

/**
 * 이벤트(모임) 폼에 대한 커스텀 유효성 검사기
 *
 * [이 클래스의 역할]
 *
 * EventForm의 필드 간 상호 의존적인 비즈니스 규칙을 검증한다.
 * JSR-303 어노테이션(@NotBlank, @Min 등)은 개별 필드의 단순 규칙만 검증할 수 있고,
 * "신청 마감일이 모임 시작일보다 앞서야 한다" 같은 필드 간 관계는 표현할 수 없다.
 * 이런 복합 규칙을 검증하기 위해 Spring Validator를 직접 구현한다.
 *
 *
 * [Spring의 2단계 유효성 검증 구조]
 *
 * Controller에서 @Valid EventForm eventForm으로 선언하면,
 * Spring은 두 가지 검증을 동시에 실행한다:
 *
 *   1단계: JSR-303/380 어노테이션 검증 (EventForm 클래스에 선언)
 *     @NotBlank  → title이 비어있으면 에러
 *     @Length    → title이 50자 초과하면 에러
 *     @Min(2)   → limitOfEnrollments가 2 미만이면 에러
 *
 *   2단계: 커스텀 Validator 검증 (이 클래스가 담당)
 *     → endEnrollmentDateTime이 현재보다 과거이면 에러
 *     → startDateTime이 endEnrollmentDateTime보다 과거이면 에러
 *     → endDateTime이 startDateTime보다 과거이면 에러
 *
 *   두 단계의 에러가 모두 하나의 Errors 객체에 모이고,
 *   Controller에서 errors.hasErrors()로 한 번에 확인한다.
 *
 *
 * [Controller와의 연결 방식 - @InitBinder]
 *
 * EventController에 다음과 같이 등록되어 있다:
 *
 *   @InitBinder("eventForm")
 *   public void initBinder(WebDataBinder webDataBinder) {
 *       webDataBinder.addValidators(eventValidator);
 *   }
 *
 * 이 설정의 의미:
 *   - "eventForm"이라는 이름으로 바인딩되는 객체에만 이 Validator를 적용
 *   - @InitBinder는 해당 Controller 범위에서만 동작 (다른 Controller에는 영향 없음)
 *   - @Valid가 붙은 파라미터의 바인딩 시점에 WebDataBinder가 자동으로 이 Validator를 호출
 *
 *
 * [검증하는 시간 순서 규칙]
 *
 * 모임의 시간 필드는 반드시 아래 순서를 지켜야 한다:
 *
 *   현재 시각 < endEnrollmentDateTime < startDateTime < endDateTime
 *   (지금)      (신청 마감)              (모임 시작)      (모임 종료)
 *
 *   올바른 예시:
 *     현재:       2025-01-01 10:00
 *     신청 마감:  2025-01-05 23:59  ← 현재보다 미래 (검증1 통과)
 *     모임 시작:  2025-01-10 14:00  ← 신청 마감보다 미래 (검증3 통과)
 *     모임 종료:  2025-01-10 17:00  ← 시작/신청마감보다 미래 (검증2 통과)
 *
 *   잘못된 예시 1 - 이미 지난 마감일:
 *     현재:       2025-01-10 10:00
 *     신청 마감:  2025-01-05 23:59  ← 현재보다 과거 → 검증1 실패
 *
 *   잘못된 예시 2 - 시작 전에 끝나는 모임:
 *     모임 시작:  2025-01-10 17:00
 *     모임 종료:  2025-01-10 14:00  ← 시작보다 과거 → 검증2 실패
 *
 *   잘못된 예시 3 - 신청 마감 전에 시작하는 모임:
 *     신청 마감:  2025-01-10 23:59
 *     모임 시작:  2025-01-10 14:00  ← 마감보다 과거 → 검증3 실패
 *
 *
 * [생성 검증 vs 수정 검증]
 *
 * 이 클래스는 두 가지 검증 메서드를 제공한다:
 *
 *   1. validate() — 모임 생성 시 자동 호출
 *      → @InitBinder로 등록되어 @Valid와 함께 자동 실행
 *      → 시간 순서 규칙만 검증
 *
 *   2. validateUpdateForm() — 모임 수정 시 수동 호출
 *      → Controller에서 직접 호출: eventValidator.validateUpdateForm(eventForm, event, errors)
 *      → 시간 순서 규칙(validate) + 모집 정원 축소 제한 규칙을 검증
 *      → 기존 Event 엔티티가 필요하므로 @InitBinder 자동 호출로는 처리할 수 없다
 *         (Validator.validate()는 폼 객체만 받을 수 있고, 기존 엔티티를 참조할 방법이 없음)
 *
 *
 * [Errors 객체와 에러 처리 흐름]
 *
 * errors.rejectValue("필드명", "에러코드", "기본 메시지")로 에러를 등록하면:
 *
 *   1. Errors 객체에 FieldError가 추가된다
 *   2. Controller에서 errors.hasErrors()가 true를 반환한다
 *   3. 폼 뷰가 재렌더링되면서 Thymeleaf가 해당 필드 옆에 에러 메시지를 표시한다
 *      → th:errors="*{endEnrollmentDateTime}" → "모임 접수 종료 일시를 정확히 입력하세요."
 *
 *   파라미터 설명:
 *     - "필드명" (예: "endEnrollmentDateTime"): 에러가 발생한 폼 필드. Thymeleaf가 이 이름으로 에러를 매칭
 *     - "에러코드" (예: "wrong.datetime"): 국제화(i18n) 메시지 키. messages.properties에서 다국어 메시지 조회에 사용
 *     - "기본 메시지": 메시지 키에 해당하는 값이 없을 때 표시되는 기본 에러 메시지
 */
@Component
public class EventValidator implements Validator {

    /**
     * 이 Validator가 검증할 수 있는 객체 타입을 지정
     *
     * Spring의 WebDataBinder는 바인딩 시점에 이 메서드를 먼저 호출하여
     * 현재 바인딩되는 객체가 이 Validator의 검증 대상인지 확인한다.
     *
     *   호출 흐름:
     *     WebDataBinder.validate()
     *       → EventValidator.supports(EventForm.class)  → true 반환
     *       → EventValidator.validate(eventForm, errors) → 실제 검증 실행
     *
     * isAssignableFrom()을 사용하는 이유:
     *   - clazz == EventForm.class: 정확히 EventForm만 통과
     *   - EventForm.class.isAssignableFrom(clazz): EventForm 또는 그 하위 클래스도 통과
     *   - Spring 공식 가이드에서 isAssignableFrom() 사용을 권장 (확장성 고려)
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return EventForm.class.isAssignableFrom(clazz);
    }

    /**
     * 모임 생성 시 시간 순서 규칙을 검증하는 메서드
     *
     * @InitBinder로 등록되어 @Valid와 함께 자동으로 호출된다.
     * JSR-303 어노테이션 검증이 먼저 실행된 후, 이 메서드가 추가로 실행된다.
     *
     * [검증 규칙 요약]
     *
     *   검증1: 신청 마감일 > 현재 시각
     *     → 이미 지난 시간으로 마감일을 설정할 수 없다
     *
     *   검증2: 모임 종료일 > 모임 시작일 AND 모임 종료일 > 신청 마감일
     *     → 모임이 시작하기 전에 끝날 수 없다
     *     → 모임이 신청 마감보다 먼저 끝날 수 없다
     *
     *   검증3: 모임 시작일 > 신청 마감일
     *     → 신청을 받는 중에 모임이 시작될 수 없다
     *
     * [세 검증을 모두 독립적으로 실행하는 이유]
     *
     * 검증1이 실패해도 검증2, 검증3은 계속 실행된다.
     * 이렇게 하면 사용자가 폼을 제출했을 때 모든 에러를 한 번에 볼 수 있다.
     * (하나씩 고치고 재제출하는 반복을 줄여주는 UX 고려)
     *
     * @param target 검증 대상 객체 (Object 타입으로 받아서 내부에서 EventForm으로 캐스팅)
     * @param errors 검증 실패 시 에러를 담는 객체 (Controller에서 errors.hasErrors()로 확인)
     */
    @Override
    public void validate(Object target, Errors errors) {
        EventForm eventForm = (EventForm) target;

        // 검증1: 신청 마감일이 현재 시각보다 과거이면 에러
        if (isNotValidEndEnrollmentDateTime(eventForm)) {
            errors.rejectValue("endEnrollmentDateTime", "wrong.datetime", "모임 접수 종료 일시를 정확히 입력하세요.");
        }

        // 검증2: 모임 종료일이 시작일 또는 신청 마감일보다 과거이면 에러
        if (isNotValidEndDateTime(eventForm)) {
            errors.rejectValue("endDateTime", "wrong.datetime", "모임 종료 일시를 정확히 입력하세요.");
        }

        // 검증3: 모임 시작일이 신청 마감일보다 과거이면 에러
        if (isNotValidStartDateTime(eventForm)) {
            errors.rejectValue("startDateTime", "wrong.datetime", "모임 시작 일시를 정확히 입력하세요.");
        }
    }

    /**
     * 검증1: 신청 마감일이 현재 시각보다 과거인지 확인
     *
     * 이미 지난 시간을 마감일로 설정하면 아무도 신청할 수 없으므로 의미가 없다.
     *
     *   정상: endEnrollmentDateTime = 2025-01-10 (미래) → false 반환 (통과)
     *   에러: endEnrollmentDateTime = 2024-12-01 (과거) → true 반환 (에러)
     */
    private boolean isNotValidEndEnrollmentDateTime(EventForm eventForm) {
        return eventForm.getEndEnrollmentDateTime().isBefore(LocalDateTime.now());
    }

    /**
     * 검증2: 모임 종료일이 시작일 또는 신청 마감일보다 과거인지 확인
     *
     * 두 가지 조건을 OR로 검사한다:
     *
     *   조건 A: 종료일 < 시작일
     *     → 모임이 시작도 하기 전에 끝날 수 없다
     *     → 에러: 시작 14:00, 종료 10:00
     *
     *   조건 B: 종료일 < 신청 마감일
     *     → 신청을 받고 있는데 모임이 이미 끝났을 수 없다
     *     → 에러: 마감 1월 15일, 종료 1월 10일
     *
     * 둘 중 하나라도 해당되면 에러이다.
     */
    private boolean isNotValidEndDateTime(EventForm eventForm) {
        LocalDateTime endDateTime = eventForm.getEndDateTime();
        return endDateTime.isBefore(eventForm.getStartDateTime())
                || endDateTime.isBefore(eventForm.getEndEnrollmentDateTime());
    }

    /**
     * 검증3: 모임 시작일이 신청 마감일보다 과거인지 확인
     *
     * 신청 접수가 아직 진행 중인데 모임이 시작되면 논리적으로 모순이다.
     * 신청 마감 → 모임 시작 순서가 지켜져야 한다.
     *
     *   정상: 마감 1월 5일, 시작 1월 10일 → false 반환 (통과)
     *   에러: 마감 1월 10일, 시작 1월 5일 → true 반환 (에러)
     */
    private boolean isNotValidStartDateTime(EventForm eventForm) {
        return eventForm.getStartDateTime().isBefore(eventForm.getEndEnrollmentDateTime());
    }

    /**
     * 모임 수정 시 모집 정원 축소 제한을 검증하는 메서드
     *
     * [왜 별도 메서드로 분리되어 있는가?]
     *
     * 이 검증은 "기존에 승인된 참가자 수"와 "수정하려는 정원"을 비교해야 한다.
     * 즉, 현재 DB에 저장된 Event 엔티티의 상태가 필요하다.
     *
     * 그런데 Validator 인터페이스의 validate(Object target, Errors errors) 메서드는
     * 폼 객체(target)와 에러 객체(errors)만 파라미터로 받을 수 있다.
     * 기존 Event 엔티티를 전달할 방법이 없으므로 @InitBinder 자동 호출로는 처리할 수 없다.
     *
     * 따라서 Controller에서 직접 호출하는 방식을 사용한다:
     *
     *   // EventController.updateEventSubmit() 내부
     *   eventValidator.validateUpdateForm(eventForm, event, errors);
     *
     *
     * [검증 규칙]
     *
     * 수정하려는 모집 정원(limitOfEnrollments)이
     * 현재 이미 승인된 참가자 수(numberOfAcceptedEnrollments)보다 작으면 에러.
     *
     *   예시 1 - 정상:
     *     현재 승인 인원: 5명
     *     수정 정원: 10명 → 5 < 10 → 통과 (정원을 늘리는 것은 문제 없음)
     *
     *   예시 2 - 정상:
     *     현재 승인 인원: 5명
     *     수정 정원: 5명 → 5 < 5 = false → 통과 (딱 맞는 것도 허용)
     *
     *   예시 3 - 에러:
     *     현재 승인 인원: 5명
     *     수정 정원: 3명 → 3 < 5 = true → 에러
     *     → 이미 5명이 승인되었는데 정원을 3명으로 줄이면
     *       2명의 승인을 취소해야 하는 모순이 발생
     *
     * @param eventForm 수정 폼 데이터 (사용자가 입력한 새로운 정원)
     * @param event 기존 Event 엔티티 (현재 승인된 참가자 수를 조회하기 위해 필요)
     * @param errors 검증 실패 시 에러를 담는 객체
     */
    public void validateUpdateForm(EventForm eventForm, Event event, Errors errors) {
        if (eventForm.getLimitOfEnrollments() < event.getNumberOfAcceptedEnrollments()) {
            errors.rejectValue("limitOfEnrollments", "wrong.value", "확인된 참가 신청보다 모집 인원 수가 커야 합니다.");
        }
    }
}


/*

## EventValidator의 검증 흐름 전체 정리

[모임 생성 시]

사용자가 폼 제출 (POST /new-event)
    │
    ▼
Spring WebDataBinder 동작
    ├── 1단계: JSR-303 어노테이션 검증 (EventForm에 선언)
    │     @NotBlank title     → 빈값이면 에러
    │     @Length(max=50)     → 50자 초과면 에러
    │     @Min(2) limit       → 2 미만이면 에러
    │
    └── 2단계: EventValidator.validate() 자동 호출 (@InitBinder 등록)
    │         검증1: 신청 마감 > 현재 시각     → 과거면 에러
    │         검증2: 모임 종료 > 시작, 마감    → 순서 어긋나면 에러
    │         검증3: 모임 시작 > 신청 마감     → 순서 어긋나면 에러
    │
    ▼
 errors.hasErrors() 확인
    ├── true  → 폼 재렌더링 (에러 메시지 표시)
    └── false → EventService.createEvent() 호출


[모임 수정 시]

사용자가 수정 폼 제출 (POST /events/{id}/edit)
    │
    ▼
Spring WebDataBinder 동작
    ├── 1단계: JSR-303 어노테이션 검증 (위와 동일)
    └── 2단계: EventValidator.validate() 자동 호출 (위와 동일)
    │
    ▼
Controller에서 추가 검증 수동 호출
    └── eventValidator.validateUpdateForm(eventForm, event, errors)
    │   검증4: 수정 정원 >= 현재 승인 인원 → 줄이면 에러
    │
    ▼
 errors.hasErrors() 확인
    ├── true  → 수정 폼 재렌더링 (에러 메시지 표시)
    └── false → EventService.updateEvent() 호출

 */