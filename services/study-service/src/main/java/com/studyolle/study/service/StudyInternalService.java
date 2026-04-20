package com.studyolle.study.service;

import com.studyolle.study.dto.response.StudyAdminResponse;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내부 전용 (/internal/studies/**) API 가 필요로 하는 쓰기 비즈니스 로직 service.
 *
 * [왜 별도로 만들었는가]
 * 기존 StudyInternalController 는 단순 조회만 하므로 service 계층 없이 Repository 를 직접 호출했다.
 * 그러나 강제 비공개(forceClose) 는 다음 검증 규칙을 모아서 실행해야 한다:
 *   1) 요청자 정보 존재 여부 (X-Account-Id 헤더 필수)
 *   2) 자기가 소유한 스터디도 관리자 권한으로 "강제 비공개" 하는 것은 허용되는가?
 *      → 허용. 관리자는 자기 스터디라도 플랫폼 질서를 위해 차단할 권한이 있다.
 *      → 즉 "자기 자신" 검증은 없다. 회원 권한 변경과의 큰 차이점이다.
 *   3) 대상 스터디 존재 여부
 *   4) 도메인 상태 불변식 (이미 종료된 스터디 재종료 금지) — 엔티티 메서드에 위임
 *
 * 이런 규칙을 컨트롤러에 직접 늘어놓으면 응집도가 낮아지고 테스트도 번거롭다.
 * 그래서 AccountInternalService 와 동일한 패턴으로 분리했다.
 *
 * [왜 Kafka 이벤트 발행을 추가하지 않았는가]
 * 다른 상태 전이(publish/startRecruit/stopRecruit) 는 Kafka 로 이벤트를 발행하지만
 * 강제 비공개는 이번 작업의 핵심 범위(관리자가 스터디를 차단한다) 에 집중하기 위해 이벤트 발행을 생략했다.
 * 나중에 "관리자가 당신의 스터디를 차단했습니다" 같은 알림 요구가 생기면 StudyKafkaProducer 를 여기에 주입해 한 줄 추가하면 된다.
 */
@Service
@RequiredArgsConstructor
public class StudyInternalService {

    private final StudyRepository studyRepository;

    /*
     * 관리자가 스터디를 강제 종료(비공개 처리)한다.
     *
     * @param path        대상 스터디의 경로
     * @param requesterId 요청한 관리자의 id (감사 로그 기반이 생기면 남길 때 사용 가능)
     * @return 변경된 스터디의 관리자용 요약 DTO
     *
     * @throws IllegalArgumentException 요청자 정보 없음, 스터디 없음, 이미 종료된 경우
     *
     * [@Transactional 의 역할]
     * 메서드 진입 시 트랜잭션이 시작되고, 정상 종료 시 커밋된다.
     * study 엔티티는 findByPath 로 조회된 시점부터 영속 상태이므로 forceClose() 호출 후 별도의 save() 가 필요 없다.
     * Dirty Checking 이 변경을 감지해 자동으로 UPDATE 를 발행한다.
     *
     * 검증 단계에서 던지는 IllegalArgumentException 은 RuntimeException 계열이므로
     * Spring 기본 롤백 정책에 따라 자동 롤백된다.
     * 검증 단계에서는 아직 변경된 게 없으니 실질적으로 롤백할 것도 없지만 의미상 안전하다.
     *
     * forceClose() 가 던지는 RuntimeException ("이미 종료된 스터디") 는
     * GlobalExceptionHandler 에서 500 으로 떨어지지 않도록 IllegalArgumentException
     * 으로 잡아 감싸주는 것이 좋다. 아래에서 try-catch 처리.
     */
    @Transactional
    public StudyAdminResponse forceClose(String path, Long requesterId) {

        // 가드 1 — 요청자 헤더 필수.
        // 정상 경로로 들어왔다면 api-gateway 가 X-Account-Id 를 반드시 심어서 보낸다.
        // null 이면 헤더 없이 들어온 비정상 호출이다.
        if (requesterId == null) {
            throw new IllegalArgumentException("요청자 정보가 없습니다.");
        }

        // 가드 2 — 대상 스터디 조회. 없으면 400 으로 떨어뜨린다.
        // GlobalExceptionHandler(존재한다면) 가 IllegalArgumentException 을 400 으로 매핑한다.
        Study study = studyRepository.findByPath(path);
        if (study == null) {
            throw new IllegalArgumentException("스터디를 찾을 수 없습니다: path=" + path);
        }

        // 가드 3 — 도메인 메서드 호출.
        // 내부에서 "이미 종료됨" 을 RuntimeException 으로 던질 수 있다.
        // 메시지를 보존하면서 IllegalArgumentException 으로 감싸 400 으로 변환한다.
        try {
            study.forceClose();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        // 변환해서 반환. 트랜잭션 종료 시점에 UPDATE 가 발행된다.
        return StudyAdminResponse.from(study);
    }
}