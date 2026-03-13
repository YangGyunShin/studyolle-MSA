package com.studyolle.modules.event.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.event.entity.Event;
import com.studyolle.modules.event.dto.EventForm;
import com.studyolle.modules.event.repository.EventRepository;
import com.studyolle.modules.study.event.StudyUpdateEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 이벤트(모임) 엔티티의 생명주기(CRUD)를 관리하는 서비스
 *
 * [이 클래스의 역할]
 *
 * Event 엔티티의 생성, 수정, 삭제를 담당한다.
 * EventController가 이 서비스를 사용하며, 참가 신청(Enrollment) 관련 로직은
 * EnrollmentService에 분리되어 있다.
 *
 *
 * [서비스 분리 구조]
 *
 *   EventController ──────→ EventService (현재 클래스)
 *     (Event CRUD)           - createEvent()
 *                            - updateEvent()
 *                            - deleteEvent()
 *
 *   EnrollmentController ──→ EnrollmentService
 *     (참가 신청/취소)          - newEnrollment()
 *                            - cancelEnrollment()
 *
 *   EnrollmentManageController → EnrollmentService
 *     (승인/거절/출석 관리)          - acceptEnrollment()
 *                                - rejectEnrollment()
 *                                - checkInEnrollment()
 *                                - cancelCheckInEnrollment()
 *
 * [분리 기준]
 *
 * Controller는 사용자 역할(일반 vs 운영자) 기준으로 3개로 분리했지만,
 * Service는 대상 엔티티 기준으로 2개로 분리했다.
 *
 * EnrollmentController와 EnrollmentManageController가 별도 서비스를 가지지 않는 이유:
 *   1. 둘 다 동일한 Enrollment 엔티티를 대상으로 한다
 *   2. 참가 취소(cancelEnrollment) 시 대기자 자동 승격(acceptNextWaitingEnrollment)이
 *      필요한데, 이 두 로직이 서로 다른 서비스에 있으면 순환 참조가 발생할 수 있다
 *   3. 서비스 레벨에서는 "누가 호출하느냐"보다 "어떤 엔티티를 다루느냐"가
 *      응집도에 더 중요한 기준이다
 *
 *
 * [클래스 레벨 어노테이션 설명]
 *
 * @Service
 *   - 이 클래스를 스프링 빈으로 등록하며, 서비스 계층임을 명시한다.
 *   - @Component와 기능은 동일하지만, 계층적 역할을 명확히 표현한다.
 *
 * @Transactional
 *   - 이 클래스의 모든 public 메서드가 트랜잭션 내에서 실행된다.
 *   - 메서드 실행 중 예외가 발생하면 트랜잭션이 롤백되어
 *     "일부만 저장되는" 상황을 방지한다.
 *   - JPA dirty checking이 트랜잭션 커밋 시점에 동작하므로,
 *     엔티티 필드를 변경한 뒤 별도의 save() 호출 없이도 DB에 자동 반영된다.
 *
 * @RequiredArgsConstructor
 *   - final 필드에 대한 생성자를 자동 생성한다.
 *   - Spring이 생성자 주입(Constructor Injection)으로 의존성을 주입한다.
 *   - 생성자가 하나뿐이면 @Autowired를 생략해도 자동 주입된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    /** Event 엔티티 저장소 - Event의 CRUD 쿼리를 실행 */
    private final EventRepository eventRepository;

    /**
     * ModelMapper - EventForm(DTO) <-> Event(Entity) 간 필드 복사를 자동화
     *
     * 예: modelMapper.map(eventForm, event)
     *   → eventForm의 title, description, startDateTime 등의 값을
     *     event의 동일 이름 필드에 자동으로 복사한다.
     *   → 필드 이름과 타입이 일치하면 자동 매핑되므로
     *     수동으로 event.setTitle(form.getTitle()) 같은 코드를 작성할 필요가 없다.
     */
    private final ModelMapper modelMapper;

    /**
     * Spring ApplicationEvent 발행기
     *
     * 도메인 상태 변경 후 알림 등 후처리가 필요할 때 이벤트를 발행한다.
     * 발행된 이벤트는 @EventListener가 붙은 메서드가 자동으로 수신하여 처리한다.
     *
     *   이 서비스에서 발행하는 이벤트:
     *     StudyUpdateEvent → 모임 생성/수정/삭제 시 스터디 멤버들에게 알림
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 새로운 이벤트(모임)를 생성하여 DB에 저장한다
     *
     * [처리 흐름]
     *
     *   EventController.createNewEvent() 에서 호출
     *     → EventForm을 Event 엔티티로 변환 (Controller에서 ModelMapper로 처리)
     *     → 이 메서드에서 생성자/생성시각/소속 스터디 설정
     *     → DB 저장
     *     → 스터디 멤버들에게 알림 발행
     *
     * [왜 Controller가 아닌 Service에서 생성자/생성시각을 설정하는가?]
     *
     * createdBy와 createdDateTime은 비즈니스 규칙에 의해 결정되는 값이다:
     *   - createdBy: "현재 로그인한 사용자 = 생성자"라는 비즈니스 규칙
     *   - createdDateTime: "서버 시간 기준으로 기록"이라는 비즈니스 규칙
     *   - study: "해당 스터디에 소속된 모임으로 등록"이라는 비즈니스 규칙
     *
     * 이런 비즈니스 규칙의 적용은 Service 계층의 책임이다.
     * Controller는 사용자 입력을 받아 전달하는 역할만 한다.
     *
     * @param event   EventForm에서 변환된 Event 엔티티 (아직 id, createdBy 등이 비어있는 상태)
     * @param study   이 모임이 소속될 스터디
     * @param account 모임을 생성하는 사용자 (현재 로그인한 운영자)
     * @return 저장 완료된 Event (DB에서 발급된 id가 포함된 영속 상태 엔티티)
     */
    public Event createEvent(Event event, Study study, Account account) {

        // 생성자 정보 설정 - 이 모임을 만든 사용자가 누구인지 기록
        event.setCreatedBy(account);

        // 생성 일시 기록 - 서버 시간 기준 현재 시각
        event.setCreatedDateTime(LocalDateTime.now());

        // 소속 스터디 설정 - 이 모임이 어떤 스터디에 속하는지 연관관계 설정
        event.setStudy(study);

        // 스터디 멤버들에게 모임 생성 알림 발행
        // → StudyUpdateEvent 리스너가 수신하여 이메일/웹 알림 처리
        eventPublisher.publishEvent(
                new StudyUpdateEvent(event.getStudy(),
                        "'" + event.getTitle() + "' 모임을 만들었습니다.")
        );

        // DB 저장 (INSERT) - save() 반환값에 DB가 발급한 PK(id)가 포함되어 있음
        return eventRepository.save(event);
    }

    /**
     * 기존 이벤트(모임)의 정보를 수정한다
     *
     * [처리 흐름]
     *
     *   EventController.updateEventSubmit() 에서 호출
     *     → 폼 데이터(EventForm)를 기존 Event 엔티티에 덮어씀
     *     → 정원이 늘어났을 수 있으므로 대기자 자동 승인 시도
     *     → 스터디 멤버들에게 수정 알림 발행
     *
     * [별도의 save() 호출이 없는 이유 - JPA Dirty Checking]
     *
     * 이 메서드에서 event 객체의 필드를 변경하면,
     * 트랜잭션 커밋 시점에 JPA가 자동으로 변경 사항을 감지하여 UPDATE 쿼리를 실행한다.
     *
     *   동작 원리:
     *     1. 트랜잭션 시작 시 JPA가 event의 원본 스냅샷을 저장해둔다
     *     2. modelMapper.map()으로 event의 필드가 변경된다
     *     3. 트랜잭션 커밋 시 JPA가 현재 상태와 원본 스냅샷을 비교한다
     *     4. 변경된 필드가 있으면 자동으로 UPDATE 쿼리를 생성하여 실행한다
     *
     *   따라서 명시적인 eventRepository.save(event) 호출이 필요 없다.
     *
     * [대기자 자동 승인이 필요한 이유]
     *
     * 수정 시 모집 정원(limitOfEnrollments)이 늘어날 수 있다.
     * 예를 들어 정원이 5명 → 10명으로 변경되면:
     *   - 기존 승인: 5명, 대기: 3명
     *   - 변경 후 남은 자리: 10 - 5 = 5자리
     *   - 대기자 3명 모두 자동 승인 가능
     *
     * acceptWaitingList()가 이 로직을 자동으로 처리한다.
     * FCFS(선착순) 방식일 때만 동작하며, CONFIRMATIVE 방식이면 아무 일도 하지 않는다.
     *
     * @param event     DB에서 조회한 기존 Event 엔티티 (영속 상태)
     * @param eventForm 사용자가 수정 폼에서 입력한 새로운 값
     */
    public void updateEvent(Event event, EventForm eventForm) {

        // 폼 데이터를 기존 엔티티에 복사 (title, description, 날짜, 정원 등)
        modelMapper.map(eventForm, event);

        // 정원 변경에 따른 대기자 자동 승인 처리 (FCFS 방식인 경우에만 동작)
        event.acceptWaitingList();

        // 스터디 멤버들에게 수정 알림 발행
        eventPublisher.publishEvent(
                new StudyUpdateEvent(event.getStudy(),
                        "'" + event.getTitle() + "' 모임 정보를 수정했으니 확인하세요.")
        );
    }

    /**
     * 이벤트(모임)를 삭제한다
     *
     * [처리 흐름]
     *
     *   EventController.deleteEvent() 에서 호출
     *     → DB에서 Event 삭제 (연관된 Enrollment도 함께 삭제될 수 있음)
     *     → 스터디 멤버들에게 삭제 알림 발행
     *
     * [연관 엔티티 삭제 처리]
     *
     * Event를 삭제하면 이 모임에 대한 Enrollment(참가 신청)도 함께 처리해야 한다.
     * 처리 방식은 Event 엔티티의 @OneToMany 설정에 따라 달라진다:
     *
     *   - CascadeType.REMOVE 설정 시: Event 삭제 시 Enrollment도 자동 DELETE
     *   - orphanRemoval = true 설정 시: 부모(Event)가 삭제되면 고아가 된 Enrollment 자동 삭제
     *   - 두 설정 모두 없을 시: Enrollment의 event_id FK가 참조 무결성 위반으로 에러 발생
     *     → 이 경우 Enrollment를 먼저 삭제한 후 Event를 삭제해야 한다
     *
     * [알림 발행 순서]
     *
     * delete() 후에 이벤트를 발행한다.
     * 삭제된 event 객체는 영속성 컨텍스트에서는 제거되지만,
     * 자바 객체로서는 아직 메모리에 존재하므로 event.getStudy(), event.getTitle() 접근이 가능하다.
     *
     * @param event 삭제할 Event 엔티티
     */
    public void deleteEvent(Event event) {

        // DB에서 Event 삭제 (트랜잭션 커밋 시 DELETE 쿼리 실행)
        eventRepository.delete(event);

        // 스터디 멤버들에게 삭제 알림 발행
        eventPublisher.publishEvent(
                new StudyUpdateEvent(event.getStudy(),
                        "'" + event.getTitle() + "' 모임을 취소했습니다.")
        );
    }
}