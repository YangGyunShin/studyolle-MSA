package com.studyolle.study.service;

import com.studyolle.study.client.MetadataFeignClient;
import com.studyolle.study.client.dto.TagDto;
import com.studyolle.study.client.dto.ZoneDto;
import com.studyolle.study.dto.request.UpdateStudyDescriptionRequest;
import com.studyolle.study.dto.request.ZoneRequest;
import com.studyolle.study.entity.JoinType;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 설정 변경 비즈니스 로직 담당 서비스.
 *
 * =============================================
 * 이 클래스의 책임 범위
 * =============================================
 *
 * StudySettingsController 가 사용하는 모든 설정 변경 기능을 담당한다:
 * - 스터디 소개/배너 수정
 * - 태그/지역 추가·제거
 * - 공개/종료/모집 상태 변경
 * - 경로/제목/가입방식 변경
 *
 * 스터디 생성·가입·검색 등 핵심 기능은 StudyService 가 담당한다.
 *
 * =============================================
 * MetadataFeignClient 와의 협력
 * =============================================
 *
 * study-service 의 DB 에는 tagIds, zoneIds 처럼 ID 만 저장되어 있다.
 * 태그/지역 이름으로 ID 를 찾아야 할 때는 metadata-service 에 요청해야 한다.
 * MetadataFeignClient 가 이 역할을 담당한다.
 *
 * Feign Client 란 HTTP 요청을 자바 메서드 호출처럼 쓸 수 있게 해주는 라이브러리다.
 * metadataFeignClient.findOrCreateTag(tagTitle, "study-service") 를 호출하면
 * 내부적으로 metadata-service 의 REST API 에 HTTP 요청을 보내고 결과를 받아온다.
 *
 * =============================================
 * 상태 변경 메서드의 패턴
 * =============================================
 *
 * publish(), close(), startRecruit(), stopRecruit() 는 모두 동일한 패턴을 따른다:
 *   1. Study 엔티티의 메서드를 호출하여 상태 변경 (study.publish() 등)
 *   2. Dirty Checking 으로 자동 DB 반영 (save() 호출 불필요)
 *   3. [Phase 5 TODO] 이벤트 발행 위치 표시
 *
 * 상태 검증 로직(이미 공개된 스터디를 다시 공개하려는 경우 등)은 Study 엔티티가 담당한다.
 * 서비스는 엔티티에 위임하고 트랜잭션 경계만 관리한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StudySettingsService {

    private final StudyRepository studyRepository;
    private final MetadataFeignClient metadataFeignClient;

    // ============================
    // 소개 수정
    // ============================

    /**
     * 스터디 소개(간략 소개 + 상세 소개)를 수정한다.
     *
     * studyRepository.findByPath() 로 조회된 영속 상태의 study 에
     * set 메서드를 호출하면 Dirty Checking 으로 자동 반영된다.
     */
    public void updateStudyDescription(Study study, UpdateStudyDescriptionRequest request) {
        study.setShortDescription(request.getShortDescription());
        study.setFullDescription(request.getFullDescription());

        // [Phase 5 TODO]
        // StudyUpdatedEvent 발행 → notification-service 가 구독자에게 알림
        // eventPublisher.publishEvent(new StudyUpdatedEvent(study, "소개를 수정했습니다."));
    }

    // ============================
    // 배너 이미지 수정
    // ============================

    /**
     * 배너 이미지를 변경한다.
     *
     * image 는 Base64 인코딩된 문자열 또는 이미지 URL 이다.
     */
    public void updateStudyImage(Study study, String image) {
        study.setImage(image);
    }

    /**
     * 배너 사용 여부를 활성화한다.
     */
    public void enableStudyBanner(Study study) {
        study.setUseBanner(true);
    }

    /**
     * 배너 사용 여부를 비활성화한다.
     */
    public void disableStudyBanner(Study study) {
        study.setUseBanner(false);
    }

    // ============================
    // 태그 추가/제거
    // ============================

    /**
     * 스터디에 태그를 추가한다.
     *
     * 처리 흐름:
     * 1. MetadataFeignClient 로 tagTitle 에 해당하는 태그 ID 를 조회한다.
     *    태그가 없으면 metadata-service 가 새로 생성한 뒤 ID 를 반환한다 (findOrCreate).
     * 2. 반환된 ID 를 study.tagIds 컬렉션에 추가한다.
     * 3. Dirty Checking 으로 INSERT INTO study_tag_ids 가 자동 실행된다.
     *
     * @param study    태그를 추가할 스터디 (영속 상태)
     * @param tagTitle 추가할 태그 이름
     */
    public void addTag(Study study, String tagTitle) {
        TagDto tagDto = metadataFeignClient.findOrCreateTag(tagTitle, "study-service");
        study.getTagIds().add(tagDto.getId()); // Dirty Checking 으로 자동 반영
    }

    /**
     * 스터디에서 태그를 제거한다.
     *
     * 태그 ID 를 얻기 위해 metadata-service 를 조회한다.
     * Set.remove() 는 해당 ID 가 없으면 조용히 무시하므로 별도 존재 확인이 필요 없다.
     */
    public void removeTag(Study study, String tagTitle) {
        TagDto tagDto = metadataFeignClient.findOrCreateTag(tagTitle, "study-service");
        study.getTagIds().remove(tagDto.getId()); // 없는 ID 제거 시도 → 조용히 무시
    }

    // ============================
    // 지역 추가/제거
    // ============================

    /**
     * 스터디에 활동 지역을 추가한다.
     *
     * 처리 흐름:
     * 1. ZoneRequest 에서 cityName, provinceName 을 꺼내 metadata-service 에 지역 ID 를 조회한다.
     * 2. 반환된 ID 를 study.zoneIds 컬렉션에 추가한다.
     *
     * ZoneRequest 파싱 예시: "Seoul(서울)/서울특별시" → cityName="Seoul", provinceName="서울특별시"
     *
     * @param study   지역을 추가할 스터디 (영속 상태)
     * @param request 추가할 지역 정보 (도시명, 지역명 포함)
     */
    public void addZone(Study study, ZoneRequest request) {
        ZoneDto zoneDto = metadataFeignClient.findZone(
                request.getCityName(), request.getProvinceName(), "study-service");
        if (zoneDto == null) {
            throw new IllegalArgumentException("존재하지 않는 지역입니다: " + request.getZoneName());
        }
        study.getZoneIds().add(zoneDto.getId());
    }

    /**
     * 스터디에서 활동 지역을 제거한다.
     *
     * zoneDto 가 null 이면(존재하지 않는 지역) 조용히 무시한다.
     * 이미 제거된 지역을 다시 제거하려는 요청에 예외를 던질 필요가 없기 때문이다.
     */
    public void removeZone(Study study, ZoneRequest request) {
        ZoneDto zoneDto = metadataFeignClient.findZone(
                request.getCityName(), request.getProvinceName(), "study-service");
        if (zoneDto != null) {
            study.getZoneIds().remove(zoneDto.getId());
        }
    }

    // ============================
    // 스터디 상태 변경
    // ============================

    /**
     * 스터디를 공개한다 (비가역적 — 한 번 공개하면 비공개로 되돌릴 수 없다).
     *
     * 상태 변경 유효성 검증(이미 공개 상태인지 등)은 Study.publish() 내부에서 수행한다.
     * 서비스는 트랜잭션 경계와 이벤트 발행 위치만 담당한다.
     */
    public void publish(Study study) {
        study.publish();
        // [Phase 5 TODO] StudyUpdatedEvent 발행 → notification-service 구독자 알림
    }

    /**
     * 스터디를 종료한다 (비가역적 — 종료 후 다시 활성화할 수 없다).
     */
    public void close(Study study) {
        study.close();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    /**
     * 팀원 모집을 시작한다.
     *
     * 너무 잦은 모집 상태 변경을 방지하기 위해 Study.startRecruit() 내부에서
     * 마지막 변경 시각 기준 1시간 이내 재변경을 제한한다.
     */
    public void startRecruit(Study study) {
        study.startRecruit();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    /**
     * 팀원 모집을 중단한다.
     */
    public void stopRecruit(Study study) {
        study.stopRecruit();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    // ============================
    // 경로/제목/가입방식 변경
    // ============================

    /**
     * 스터디 경로를 변경한다.
     *
     * 현재 경로와 다를 때만 중복 검증을 수행한다.
     * 같은 경로로 변경 요청이 들어오면 중복 검증을 생략한다 (경로가 이미 자신의 것이므로).
     */
    public void updateStudyPath(Study study, String newPath) {
        if (!study.getPath().equals(newPath) && studyRepository.existsByPath(newPath)) {
            throw new IllegalArgumentException("이미 사용 중인 경로입니다: " + newPath);
        }
        study.setPath(newPath);
    }

    /**
     * 스터디 제목을 변경한다.
     */
    public void updateStudyTitle(Study study, String newTitle) {
        study.setTitle(newTitle);
    }

    /**
     * 스터디 가입 방식을 변경한다.
     *
     * JoinType.valueOf(joinType) 은 파라미터 문자열이 JoinType enum 값과
     * 정확히 일치하지 않으면 IllegalArgumentException 을 던진다.
     * 이를 잡아서 더 명확한 메시지로 재던진다.
     *
     * @param joinType "OPEN" 또는 "APPROVAL_REQUIRED" (JoinType enum 이름)
     */
    public void updateJoinType(Study study, String joinType) {
        try {
            study.setJoinType(JoinType.valueOf(joinType));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 가입 방식입니다: " + joinType);
        }
    }
}

/*
 * ============================================================
 * [Feign Client 와 서비스 간 통신 원리]
 * ============================================================
 *
 * 1. Feign Client 란?
 * ------------------------------------------------------------
 * 일반적으로 다른 서버에 HTTP 요청을 보내려면 아래처럼 복잡한 코드가 필요하다:
 *
 *   HttpClient client = HttpClient.newHttpClient();
 *   HttpRequest request = HttpRequest.newBuilder()
 *       .uri(URI.create("http://metadata-service/api/tags?title=spring"))
 *       .GET().build();
 *   HttpResponse<String> response = client.send(request, ...);
 *   TagDto tagDto = objectMapper.readValue(response.body(), TagDto.class);
 *
 * Feign Client 는 이 모든 과정을 숨기고, 자바 인터페이스 메서드 호출처럼 쓸 수 있게 해준다:
 *
 *   TagDto tagDto = metadataFeignClient.findOrCreateTag("spring", "study-service");
 *
 * 내부적으로 HTTP 요청, 응답 파싱, 오류 처리를 자동으로 수행한다.
 * Spring Cloud OpenFeign 이 @FeignClient 인터페이스를 스캔하여 구현체를 런타임에 자동 생성한다.
 *
 *
 * 2. "study-service" 헤더를 함께 보내는 이유
 * ------------------------------------------------------------
 * metadataFeignClient.findOrCreateTag(tagTitle, "study-service") 에서
 * "study-service" 는 X-Internal-Service 헤더 값으로 전달된다.
 *
 * metadata-service 의 InternalRequestFilter 는 /internal/** 경로에 접근하는 요청의
 * X-Internal-Service 헤더를 검사하여 허용된 서비스인지 확인한다.
 * 이 헤더가 없거나 허용되지 않은 값이면 403 을 반환한다.
 *
 * 서비스 간 내부 통신 인증 흐름은 MSA_AUTH_FLOW.md 의 유형 4 를 참고한다.
 *
 *
 * 3. findOrCreate 패턴
 * ------------------------------------------------------------
 * metadataFeignClient.findOrCreateTag(tagTitle, "study-service") 는
 * "이 이름의 태그가 있으면 반환, 없으면 생성 후 반환"을 한 번의 호출로 처리한다.
 *
 * 이 패턴을 쓰는 이유:
 * - 클라이언트(study-service)가 태그의 존재 여부를 먼저 확인하고,
 *   없으면 생성 요청을 다시 보내는 2번의 HTTP 왕복을 1번으로 줄인다.
 * - 클라이언트가 태그 생성 권한을 가질 필요 없이 metadata-service 가 일관되게 관리한다.
 *
 *
 * 4. Feign 호출 실패 시 트랜잭션 처리
 * ------------------------------------------------------------
 * addTag() 는 @Transactional 메서드 안에서 실행된다.
 * metadataFeignClient 호출이 실패하면(네트워크 오류, metadata-service 다운 등)
 * FeignException 이 발생하고, 트랜잭션이 롤백된다.
 *
 * study.getTagIds().add() 가 이미 실행되었더라도 롤백으로 되돌아간다.
 * 이것이 트랜잭션의 "원자성(Atomicity)": 전부 성공하거나 전부 취소된다.
 *
 * 운영 환경에서는 Resilience4j 의 Circuit Breaker 나 Retry 를 사용하여
 * metadata-service 가 잠시 다운되어도 재시도하거나 fallback 응답을 제공한다.
 * (Phase 6 이후 적용 예정)
 */