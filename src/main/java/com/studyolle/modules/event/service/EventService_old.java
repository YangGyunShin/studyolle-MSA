//package com.studyolle.modules.event.service;
//
//import com.studyolle.account.entity.Account;
//import com.studyolle.modules.event.entity.Enrollment;
//import com.studyolle.modules.event.entity.Event;
//import com.studyolle.modules.event.event.EnrollmentAcceptedEvent;
//import com.studyolle.modules.event.event.EnrollmentRejectedEvent;
//import com.studyolle.modules.event.dto.EventForm;
//import com.studyolle.modules.event.repository.EnrollmentRepository;
//import com.studyolle.modules.event.repository.EventRepository;
//import com.studyolle.modules.study.Study;
//import com.studyolle.modules.study.event.StudyUpdateEvent;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import org.modelmapper.ModelMapper;
//import org.springframework.context.ApplicationEventPublisher;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//
///**
// * Event (모임) 관련 핵심 비즈니스 로직을 담당하는 서비스 클래스
// *
// * - 트랜잭션 단위로 도메인 상태 변경을 안전하게 처리
// * - 이벤트 생성, 수정, 삭제 등 주로 도메인 중심의 작업 수행
// * - Controller → Service → Repository 계층 구조에서 Service 계층 역할
// */
//@Service  // Spring Bean 으로 등록 (Service 역할 명시)
//@Transactional  // 모든 public 메서드는 트랜잭션으로 실행됨 (기본: 읽기/쓰기)
//@RequiredArgsConstructor  // 생성자 주입 자동 생성 (final 필드)
//public class EventService {
//
//    private final EventRepository eventRepository; // Repository: DB 작업 담당
//    private final ModelMapper modelMapper; // ModelMapper: Form ↔ Entity 변환 담당 (이미 Controller 에서 변환된 Entity 사용됨)
//    private final EnrollmentRepository enrollmentRepository; // Repository: 참가신청(Enrollment) 저장소 (참가 신청 비즈니스에 활용될 수 있음)
//    private final ApplicationEventPublisher eventPublisher; // Application Event Publisher: 도메인 이벤트 발행 담당 (비동기 알림 등과 연계 가능)
//
//
//    /**
//     * 신규 Event(모임)를 생성하는 비즈니스 로직
//     * - 도메인 객체의 책임을 최대한 보존하고 Service는 상태변경 흐름만 제어
//     *
//     * @param event 이미 Form → Entity 변환이 완료된 Event 객체
//     * @param study 소속 Study 객체 (생성된 이벤트와 연결)
//     * @param account 이벤트 생성자 (현재 로그인 사용자)
//     * @return 저장된 Event (PK 포함된 영속화된 엔티티)
//     */
//    public Event createEvent(Event event, Study study, Account account) {
//        /*
//          ① 생성자 정보 설정
//          - 이 이벤트를 만든 사람이 누구인지 도메인 상태에 반영
//         */
//        event.setCreatedBy(account);
//
//        /*
//          ② 생성 일시 기록
//          - 이벤트 생성 시각을 현재 시각으로 기록
//         */
//        event.setCreatedDateTime(LocalDateTime.now());
//
//        /*
//          ③ Study 와의 연관관계 설정 (양방향 연관이 설정되어 있을 가능성 존재)
//          - 이벤트가 어떤 스터디 소속인지 명시
//         */
//        event.setStudy(study);
//
//        /*
//          ④ 도메인 이벤트 발행 (옵셔널이지만 확장성 중요)
//          - 이 시점에서 StudyUpdateEvent 이벤트를 발행
//          - 비동기로 Slack 알림, Email 발송, Notification 저장 등 다양한 후처리 가능
//          - 도메인 이벤트 패턴 적용 (Aggregate 상태 변경과 후처리 분리)
//         */
//        eventPublisher.publishEvent(
//                new StudyUpdateEvent(event.getStudy(), "'" + event.getTitle() + "' 모임을 만들었습니다.")
//        );
//
//        /*
//          ⑤ 이벤트 영속화 (DB 저장)
//          - JPA save 호출 → insert 쿼리 실행
//          - save() 호출 결과: id (PK) 포함된 완전한 Entity 반환
//         */
//        return eventRepository.save(event);
//    }
//
//    /**
//     * 기존 이벤트를 수정하는 메서드
//     *
//     * - Form 데이터를 도메인 객체에 반영
//     * - 상태 변경 이후 대기자 자동 승인 규칙 적용
//     * - 수정 알림 이벤트 발행
//     *
//     * ================
//     * 🔍 전체 흐름 시각화
//     * ================
//     * 폼 제출 → updateEvent 호출
//     *     → 폼 값 Entity에 반영
//     *     → acceptWaitingList()
//     *         → 모집정원 확인
//     *         → 부족 인원 계산
//     *         → 대기자 승격 (승인 처리)
//     *     → 도메인 이벤트 발행
//     */
//    public void updateEvent(Event event, EventForm eventForm) {
//
//        /*
//          ① Form → Entity 매핑 수행
//          - ModelMapper를 활용하여 폼의 값들을 Event 엔티티 필드에 복사
//          - 이미 존재하는 엔티티라서 save 호출은 필요 없음 (JPA dirty checking 활용)
//         */
//        modelMapper.map(eventForm, event);
//
//        /*
//          ② 대기자 자동 승인 로직 호출
//          - 선착순(FCFS) 방식일 경우, 현재 모집인원 제한에 맞춰 대기 중인 신청자 자동 승인
//         */
//        event.acceptWaitingList();
//
//        /*
//          ③ 수정 알림 이벤트 발행
//          - 스터디 업데이트 알림 이벤트 발송 (이벤트리스너 → 알림 전송 등으로 활용 가능)
//         */
//        eventPublisher.publishEvent(
//                new StudyUpdateEvent(event.getStudy(), "'" + event.getTitle() + "' 모임 정보를 수정했으니 확인하세요.")
//        );
//    }
//
//    /**
//     * 이벤트 삭제 비즈니스 로직
//     *
//     * - 이벤트를 삭제하고, 스터디 업데이트 이벤트를 발행한다.
//     * - Controller 에서는 단순히 이 서비스 메서드를 호출하기만 하면 된다.
//     *
//     * ================
//     * 🔍 전체 흐름 시각화
//     * ================
//     * ① deleteEvent() 호출 (Controller → Service 호출)
//     * ② eventRepository.delete() 호출 → JPA 영속성 컨텍스트에서 삭제 예약
//     * ③ 트랜잭션 커밋 시점에서 실제 DB DELETE 쿼리 실행
//     * ④ eventPublisher.publishEvent() 호출 → StudyUpdateEvent 발행
//     * ⑤ 스프링 이벤트 시스템이 StudyUpdateEvent를 수신하여 비동기 알림 후처리 (예: 이메일, Slack, DB Notification 등)
//     */
//    public void deleteEvent(Event event) {
//
//        /*
//          ① 이벤트 삭제 (JPA Repository 활용)
//
//          - JPA에서 delete() 호출 시:
//             1. 영속성 컨텍스트에서 해당 엔티티 제거
//             2. 트랜잭션 커밋 시점에 실제 DB에서 DELETE 쿼리 실행됨
//             3. 연관관계 설정에 따라 Enrollment 등 자식 엔티티들도 자동 삭제 가능
//                (CascadeType.REMOVE 또는 orphanRemoval = true 설정 여부에 따라)
//         */
//        eventRepository.delete(event);
//
//        /*
//          ② 스터디 업데이트 알림 이벤트 발행
//
//          - 이벤트 삭제 이후 StudyUpdateEvent를 발행하여 관련 알림, Slack, 이메일 등을 비동기로 전달 가능
//          - ApplicationEventPublisher를 활용한 도메인 이벤트 패턴 적용
//          - 이 이벤트는 별도의 @EventListener를 통해 비동기 후처리 가능
//         */
//        eventPublisher.publishEvent(new StudyUpdateEvent(event.getStudy(), "'" + event.getTitle() + "' 모임을 취소했습니다."));
//    }
//
//    public void newEnrollment(Event event, Account account) {
//
//        // ① 중복 신청 여부 확인
//        if (!enrollmentRepository.existsByEventAndAccount(event, account)) {
//
//            // ② 새로운 참가 신청 엔티티 생성
//            Enrollment enrollment = new Enrollment();
//            enrollment.setEnrolledAt(LocalDateTime.now());  // 신청 시각 기록
//
//            // ③ 승인 여부 결정
//            //    - 선착순(FCFS)이고 모집정원이 남아있으면 즉시 승인
//            enrollment.setAccepted(event.isAbleToAcceptWaitingEnrollment());
//
//            enrollment.setAccount(account);
//            event.addEnrollment(enrollment);  // 이벤트 엔티티에 연관관계 설정
//
//            // ④ DB 저장 (Repository를 통해 저장소에 반영)
//            enrollmentRepository.save(enrollment);
//        }
//    }
//
//    public void cancelEnrollment(Event event, Account account) {
//
//        // ① 신청 내역 조회
//        Enrollment enrollment = enrollmentRepository.findByEventAndAccount(event, account);
//
//        // ② 참가 상태 확인 (이미 출석 처리된 경우 취소 불가)
//        if (!enrollment.isAttended()) {
//
//            // ③ 기존 신청 내역 삭제
//            event.removeEnrollment(enrollment);
//            enrollmentRepository.delete(enrollment);
//
//            // ④ 취소 이후 대기자 승격 시도
//            event.acceptNextWaitingEnrollment();
//        }
//    }
//
//    /**
//     * 참가 신청 승인 비즈니스 로직
//     *
//     * - Event 도메인 객체에 승인 처리 요청을 위임하고,
//     * - 이후 승인 이벤트 발행 → 알림 등 후처리를 비동기로 처리 가능.
//     *
//     * ================
//     * 🔍 전체 흐름 시각화
//     * ================
//     * 1️⃣ 관리자 승인 클릭 (Controller GET /accept)
//     * 2️⃣ 권한 검증 → 스터디 조회
//     * 3️⃣ eventService.acceptEnrollment() 호출
//     * 4️⃣ event.accept() 호출 (도메인 상태전이)
//     * 5️⃣ 정원 확인 → 승인 처리 (Enrollment.setAccepted(true))
//     * 6️⃣ 이벤트 발행 → EnrollmentAcceptedEvent
//     * 7️⃣ 리다이렉트 → 상세화면
//     */
//    public void acceptEnrollment(Event event, Enrollment enrollment) {
//        event.accept(enrollment);  // 도메인 상태 전이 (실제 승인 여부 검증 포함)
//        eventPublisher.publishEvent(new EnrollmentAcceptedEvent(enrollment));  // 알림 발행
//    }
//
//    /**
//     * 참가 신청 거절 비즈니스 로직
//     *
//     * - Event 도메인 객체에 거절 요청 위임 후,
//     * - 거절 이벤트 발행 → 알림 시스템 연계 가능.
//     *
//     * ================
//     * 🔍 전체 흐름 시각화
//     * ================
//     * 1️⃣ 관리자 거절 클릭 (Controller GET /reject)
//     * 2️⃣ 권한 검증 → 스터디 조회
//     * 3️⃣ eventService.rejectEnrollment() 호출
//     * 4️⃣ event.reject() 호출 (도메인 상태전이)
//     * 5️⃣ 승인 상태 취소 (Enrollment.setAccepted(false))
//     * 6️⃣ 이벤트 발행 → EnrollmentRejectedEvent
//     * 7️⃣ 리다이렉트 → 상세화면
//     */
//    public void rejectEnrollment(Event event, Enrollment enrollment) {
//        event.reject(enrollment);
//        eventPublisher.publishEvent(new EnrollmentRejectedEvent(enrollment));
//    }
//
//    /**
//     * 출석 처리 비즈니스 로직
//     *
//     * - 해당 참가 신청의 출석 여부를 true로 설정
//     * - 단순 상태 변경 (DB의 FK나 연관관계 변경 아님)
//     *
//     * ✅ 핵심: attended 필드는 단순 상태 필드
//     * 	•   FK나 양방향 연관관계와는 전혀 무관
//     * 	•	단순히 출석 여부를 표현하는 Boolean 값 (비즈니스 상태 관리용)
//     * 	•	JPA dirty checking이 자동으로 변경사항 감지 후 update 쿼리 실행
//     */
//    public void checkInEnrollment(Enrollment enrollment) {
//        enrollment.setAttended(true);
//    }
//
//    /**
//     * 출석 취소 비즈니스 로직
//     *
//     * - 출석 처리된 신청자의 출석 여부를 다시 false로 변경
//     */
//    public void cancelCheckInEnrollment(Enrollment enrollment) {
//        enrollment.setAttended(false);
//    }
//}
