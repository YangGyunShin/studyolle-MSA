package com.studyolle.study.controller;

import com.studyolle.study.client.EventFeignClient;
import com.studyolle.study.client.MetadataFeignClient;
import com.studyolle.study.client.dto.EventSummaryDto;
import com.studyolle.study.client.dto.ZoneDto;
import com.studyolle.study.dto.response.*;
import com.studyolle.study.entity.JoinRequestStatus;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.JoinRequestRepository;
import com.studyolle.study.repository.StudyRepository;
import com.studyolle.study.service.StudyInternalService;
import com.studyolle.study.service.StudyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 서비스 간 내부 통신 전용 컨트롤러.
 * <p>
 * =============================================
 * 이 컨트롤러가 필요한 이유
 * =============================================
 * <p>
 * 모노리틱에서는 같은 JVM 프로세스 안에 있는 서비스들이 서로의 메서드를 직접 호출했다.
 * MSA 에서는 각 서비스가 독립적인 프로세스로 실행되므로 HTTP 를 통해서만 통신할 수 있다.
 * event-service 가 스터디 정보가 필요하면 study-service 의 이 컨트롤러로 HTTP 요청을 보낸다.
 * <p>
 * =============================================
 * /internal/** 보안 계층 구조
 * =============================================
 * <p>
 * 이 경로는 두 가지 방어막으로 외부 접근을 차단한다:
 * <p>
 * 1차 방어 — api-gateway:
 * application.yml 에서 /internal/** 경로를 전면 403 으로 차단한다.
 * 외부 클라이언트(브라우저, 앱)는 이 경로에 도달조차 할 수 없다.
 * <p>
 * 2차 방어 — InternalRequestFilter (각 서비스):
 * 서비스 내부 네트워크에서 들어오는 요청도 X-Internal-Service 헤더가 없으면 403 을 반환한다.
 * api-gateway 를 우회하더라도 헤더 없이는 데이터에 접근할 수 없다.
 * <p>
 * =============================================
 * Service 계층 없이 Repository 를 직접 호출하는 이유
 * =============================================
 * <p>
 * 이 컨트롤러는 단순 조회만 수행한다. 비즈니스 로직이 없다.
 * Service 를 거치면 불필요한 중간 계층이 추가되므로 Repository 를 직접 사용한다.
 * 단, 데이터를 변경하는 작업이 생기면 반드시 Service 를 추가해야 한다.
 *
 * @see com.studyolle.study.filter.InternalRequestFilter
 */
@RestController
@RequestMapping("/internal/studies")
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 모든 메서드가 읽기 전용. 컬렉션(@ElementCollection) 지연 로딩 지원
public class StudyInternalController {

    private final StudyRepository studyRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final MetadataFeignClient metadataFeignClient;
    private final StudyService studyService;
    private final EventFeignClient eventFeignClient;
    private final StudyInternalService studyInternalService;

    /*
     * GET /internal/studies/{path}
     *
     * path 로 스터디 기본 정보를 조회한다.
     *
     * 주요 사용처:
     * - event-service: 모임 생성 전 스터디가 공개 상태인지, 모집 중인지 확인
     * - admin-service: 스터디 목록 관리 화면에서 데이터 조회
     *
     * 응답 타입이 StudyResponse 가 아닌 StudyInternalResponse 인 이유:
     * 외부에 노출하지 않아야 하는 내부 필드(managerIds 등)도 포함할 수 있고,
     * 반대로 내부 서비스에 불필요한 외부용 필드는 제외할 수 있다.
     * 내부/외부 응답 DTO 를 분리하면 각 용도에 맞게 독립적으로 변경할 수 있다.
     *
     * 스터디가 없으면 404 를 반환한다.
     * 호출한 서비스(event-service 등)가 404 를 받으면 적절한 오류 처리를 수행한다.
     */
    @GetMapping("/{path}")
    public ResponseEntity<StudyInternalResponse> getStudy(@PathVariable String path) {
        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build(); // 404 — 스터디 없음
        }
        return ResponseEntity.ok(StudyInternalResponse.from(study));
    }

    /*
     * GET /internal/studies/{path}/is-manager?accountId=123
     *
     * 특정 사용자가 이 스터디의 관리자인지 확인한다.
     *
     * 주요 사용처:
     * - event-service: 모임 생성 시 요청자가 스터디 관리자인지 권한 확인
     *
     * Boolean 을 직접 반환하여 응답을 최소화한다.
     * true/false 하나를 위해 CommonApiResponse 래퍼를 쓰면 불필요하게 복잡해진다.
     *
     * [Feign Client 호출 예시 — event-service 의 StudyFeignClient]
     *
     *   @FeignClient(name = "study-service")
     *   public interface StudyFeignClient {
     *
     *       @GetMapping("/internal/studies/{path}")
     *       StudyInternalResponse getStudy(
     *               @PathVariable String path,
     *               @RequestHeader("X-Internal-Service") String serviceName);
     *
     *       @GetMapping("/internal/studies/{path}/is-manager")
     *       Boolean isManager(
     *               @PathVariable String path,
     *               @RequestParam Long accountId,
     *               @RequestHeader("X-Internal-Service") String serviceName);
     *   }
     *
     *   // 사용 예시
     *   boolean isManager = studyFeignClient.isManager(path, accountId, "event-service");
     */
    @GetMapping("/{path}/is-manager")
    public ResponseEntity<Boolean> isManager(
            @PathVariable String path,
            @RequestParam Long accountId) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build(); // 404 — 스터디 없음
        }
        return ResponseEntity.ok(study.isManagerOf(accountId));
    }

    // GET /internal/studies/{path}/page-data?accountId=123
    //
    // 스터디 페이지 렌더링에 필요한 모든 데이터를 한 번에 반환한다 (BFF 집계 패턴).
    // accountId 는 optional — null 이면 비로그인 상태로 판단하고 권한 플래그를 모두 false 로 반환한다.
    //
    // @Transactional(readOnly = true) 가 클래스 레벨에 있으므로 managerIds, memberIds 등 @ElementCollection 컬렉션에 안전하게 접근할 수 있다.
    @GetMapping("/{path}/page-data")
    public ResponseEntity<StudyPageDataResponse> getStudyPageData(
            @PathVariable String path,
            @RequestParam(required = false) Long accountId) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isManager = accountId != null && study.isManagerOf(accountId);
        boolean isMember = accountId != null && study.getMemberIds().contains(accountId);
        boolean hasPendingRequest = accountId != null && joinRequestRepository.existsByStudyAndAccountIdAndStatus(study, accountId, JoinRequestStatus.PENDING);

        // =============================================
        // Set<Long> → List<MemberInfo> 변환이 필요한 이유
        // =============================================
        //
        // Study 엔티티는 멤버 정보를 managerIds, memberIds라는 Set<Long>으로만 저장한다.
        // study-service DB에는 숫자 ID(123, 456...)만 있고, 닉네임·프로필 이미지 등
        // 사람에 대한 정보는 모두 account-service가 소유한다.
        //
        // 그런데 frontend-service가 요구하는 응답 형태(StudyPageDataDto)의 managers/members 필드는
        // List<MemberDto> 타입이고, MemberDto에는 id뿐 아니라 nickname, profileImage, bio가 있다.
        // 즉, frontend-service는 "숫자 ID 목록"이 아니라 "사람 정보 목록"을 원한다.
        //
        // 이 불일치를 완전히 해결하려면 account-service에 아래와 같은 batch 조회 endpoint가 필요하다.
        //   POST /internal/accounts/batch  { ids: [123, 456] }  →  [{ id, nickname, profileImage, bio }, ...]
        // 그 결과로 MemberInfo의 nickname 등을 채워서 반환하는 것이 올바른 구현이다.
        //
        // 그러나 현재 account-service에 해당 endpoint가 아직 구현되지 않았으므로,
        // 일단 id만 채운 MemberInfo 객체를 만들어 보낸다.
        // Jdenticon 라이브러리는 id 값만 있어도 아바타를 자동 생성하므로
        // 화면에서 멤버 카드 자체는 나타나고, 닉네임 자리만 빈칸으로 표시된다.
        //
        // [Phase 5 TODO] account-service에 batch endpoint 추가 후 아래 두 블록을 교체한다.
        //   AccountFeignClient.getAccountsByIds(study.getManagerIds(), "study-service")
        //   → List<AccountSummaryDto>를 받아 MemberInfo.nickname, profileImage, bio까지 채운다.
        List<StudyPageDataResponse.MemberInfo> managers = study.getManagerIds()
                .stream()
                .map(id -> StudyPageDataResponse.MemberInfo
                        .builder()
                        .id(id)
                        .build())
                .collect(Collectors.toList());

        List<StudyPageDataResponse.MemberInfo> members = study.getMemberIds()
                .stream()
                .map(id -> StudyPageDataResponse.MemberInfo
                        .builder()
                        .id(id)
                        .build())
                .collect(Collectors.toList());

        StudyPageDataResponse response = StudyPageDataResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .fullDescription(study.getFullDescription())
                .image(study.getImage())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .joinType(study.getJoinType().name())  // enum -> String
                .removable(study.isRemovable())
                .memberCount(study.getMemberCount())
                .managers(managers)
                .members(members)
                .manager(isManager)
                .member(isMember)
                .hasPendingRequest(hasPendingRequest)
                .build();

        return ResponseEntity.ok(response);
    }

    // GET /internal/studies/{path}/join-requests
    //
    // PENDING 상태 가입 신청 목록을 반환한다 (승인 대기 중인 것만).
    // JoinRequest.accountNickname 이 비정규화 저장되어 있으므로 account-service 호출 없이 닉네임을 바로 포함할 수 있다.
    @GetMapping("/{path}/join-requests")
    public ResponseEntity<List<JoinRequestResponse>> getJoinRequests(@PathVariable String path) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        List<JoinRequestResponse> result = joinRequestRepository.findByStudyAndStatusOrderByRequestedAtAsc(study, JoinRequestStatus.PENDING)
                .stream()
                .map(JoinRequestResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /internal/studies/{path}/tags-str
    //
    // 현재 스터디의 태그 이름 목록을 반환한다.
    // study.tagIds(Set<Long>) → MetadataFeignClient.getTagsByIds() → List<String> 이름 변환.
    @GetMapping("/{path}/tags-str")
    public ResponseEntity<List<String>> getStudyTags(@PathVariable String path) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        if (study.getTagIds().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> tagNames = metadataFeignClient
                .getTagsByIds(study.getTagIds(), "study-service")
                .stream()
                .map(tagDto -> tagDto.getTitle())  // TagDto.getTitle() 사용 — 필드명 확인 필요
                .collect(Collectors.toList());

        return ResponseEntity.ok(tagNames);
    }

    // GET /internal/studies/{path}/zones-str
    //
    // 현재 스터디의 지역 이름 목록을 반환한다.
    // "Seoul(서울)/서울특별시" 형태의 문자열 목록.
    @GetMapping("/{path}/zones-str")
    public ResponseEntity<List<String>> getStudyZones(@PathVariable String path) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }

        if (study.getZoneIds().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> zoneNames = metadataFeignClient
                .getZonesByIds(study.getZoneIds(), "study-service")
                .stream()
                .map(ZoneDto::toDisplayString)
                .collect(Collectors.toList());
        // ZoneDto 의 실제 필드명은 MetadataFeignClient 의 ZoneDto 클래스를 확인해서 맞춰야 한다.

        return ResponseEntity.ok(zoneNames);
    }

    // GET /internal/studies/tag-whitelist
    //
    // 전체 태그 이름 목록. Tagify 자동완성 whitelist 용.
    // MetadataFeignClient.getAllTagTitles() 를 그대로 위임한다.
    @GetMapping("/tag-whitelist")
    public ResponseEntity<List<String>> getTagWhitelist() {
        return ResponseEntity.ok(metadataFeignClient.getAllTagTitles("study-service"));
    }

    // GET /internal/studies/zone-whitelist
    //
    // 전체 지역 이름 목록. Tagify 자동완성 whitelist 용.
    @GetMapping("/zone-whitelist")
    public ResponseEntity<List<String>> getZoneWhitelist() {
        return ResponseEntity.ok(metadataFeignClient.getAllZoneNames("study-service"));
    }

    // GET /internal/studies/dashboard?accountId=123
    //
    // 대시보드 렌더링에 필요한 집계 데이터를 반환한다.
    // studyEventsMap, enrolledEventIds 는 event-service 구현 후 채울 예정 (현재 빈 값).
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(@RequestParam Long accountId) {

        // StudyService 의 기존 메서드를 재사용한다.
        // getStudiesAsManager, getStudiesAsMember, getRecommendedStudies 는
        // 모두 @Transactional(readOnly = true) 로 선언되어 있으므로
        // 이 컨트롤러의 트랜잭션과 독립적으로 동작하거나 전파된다.
        //
        // [TODO] 태그/지역 기반 추천을 위해서는 accountId 로 account-service 에서
        // 사용자 관심 태그/지역 ID 를 가져와야 한다.
        // 현재는 빈 Set 을 전달하여 최신 공개 스터디 9개를 기본 추천으로 반환한다.
        List<StudySummaryResponse> managerOf = studyService.getStudiesAsManager(accountId)
                .stream()
                .map(StudySummaryResponse::from)
                .collect(Collectors.toList());

        List<StudySummaryResponse> memberOf = studyService.getStudiesAsMember(accountId)
                .stream()
                .map(StudySummaryResponse::from)
                .collect(Collectors.toList());

        List<StudySummaryResponse> recommended = studyService.getRecommendedStudies(Collections.emptySet(), Collections.emptySet())
                .stream()
                .map(StudySummaryResponse::from)
                .collect(Collectors.toList());

        // ---------------------------------------------------------------
        // event-service 에서 내가 신청한 모임 목록 가져오기
        //
        // [왜 event-service 를 직접 호출하는가]
        // 모임(Event) 데이터는 event-service 가 소유한다.
        // study-service DB 에는 모임 정보가 없으므로
        // Feign Client 로 event-service 에 물어봐야 한다.
        //
        // [왜 /internal/events/calendar 인가]
        // 이 엔드포인트는 "특정 사용자가 enrollment 한 모임 전체"를 반환한다.
        // 즉, 내가 신청한 모임 목록 = 대시보드에서 보여줄 모임 목록.
        //
        // [studyPath → studyId 변환이 필요한 이유]
        // index.html 의 studyEventsMap 은 Map<studyId, List<EventDto>> 구조다.
        // study.id(Long) 로 containsKey() 를 해야 하므로 studyPath → studyId 로 변환해야 한다.
        // event-service 의 EventResponse 에는 studyPath 가 있고 studyId 는 없다.
        // study-service 는 studyPath 로 studyId 를 알 수 있으므로 여기서 변환한다.
        // ---------------------------------------------------------------
        // 대시보드 모임 표시 로직
        //
        // [기존 방식의 문제]
        // /internal/events/calendar 는 "내가 enrollment 한 모임"만 반환한다.
        // 관리자는 자기 스터디 모임에 enrollment 를 하지 않으므로 항상 빈 목록이 된다.
        //
        // [올바른 방식]
        // 관리중인 스터디 + 참여중인 스터디 각각에 대해
        // /internal/events/by-study/{path} 로 그 스터디의 모임 전체를 가져온다.
        // enrolledEventIds 는 별도로 calendar 엔드포인트로 가져온다.
        // ---------------------------------------------------------------

        Map<Long, List<Object>> studyEventsMap = new HashMap<>();
        Set<Long> enrolledEventIds = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        // 관리중인 스터디 + 참여중인 스터디 합쳐서 처리
        List<Study> myStudies = new ArrayList<>();
        myStudies.addAll(studyService.getStudiesAsMember(accountId));
        myStudies.addAll(studyService.getStudiesAsManager(accountId));

        try {
            for (Study study : myStudies) {
                List<EventSummaryDto> events = eventFeignClient.getEventsByStudy(study.getPath(), "study-service");

                // 종료된 모임 제외, 예정된 모임만
                List<Object> upcomingEvents = events.stream()
                        .filter(e -> e.getEndDateTime() == null || e.getEndDateTime().isAfter(now))
                        .collect(Collectors.toList());

                if (!upcomingEvents.isEmpty()) {
                    studyEventsMap.put(study.getId(), upcomingEvents);
                }
            }

            // enrolledEventIds: 내가 신청(enrollment)한 모임 ID 수집
            // 대시보드에서 캘린더 아이콘 vs 초록 체크 표시 구분용
            List<EventSummaryDto> myEnrolledEvents = eventFeignClient.getCalendarEvents(accountId, "study-service");
            myEnrolledEvents.forEach(e -> enrolledEventIds.add(e.getId()));

        } catch (Exception e) {
            // event-service 장애 시 대시보드는 정상 표시, 모임만 빈 상태
        }

        DashboardResponse response = DashboardResponse.builder()
                .studyManagerOf(managerOf)
                .studyMemberOf(memberOf)
                .studyList(recommended)
                .studyEventsMap(studyEventsMap)
                .enrolledEventIds(enrolledEventIds)
                .build();

        return ResponseEntity.ok(response);
    }

    // GET /internal/studies/recent
    //
    // 최근 공개된 스터디 최대 9개. 비로그인 랜딩 페이지 하단 스터디 카드용.
    // studyRepository.findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc() 는
    // StudyService.getRecommendedStudies() 에서 이미 사용 중인 메서드다.
    @GetMapping("/recent")
    public ResponseEntity<List<StudySummaryResponse>> getRecentStudies() {
        List<StudySummaryResponse> result = studyRepository.findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(true, false)
                .stream()
                .map(StudySummaryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // 관리자용 엔드포인트 2종 — admin-service 의 StudyAdminClient 가 호출한다
    // =========================================================================

    /*
     * GET /internal/studies?keyword=xxx&page=0&size=20
     *
     * 관리자 전용 스터디 목록 조회. 공개·비공개·종료 여부에 관계없이 전체를 반환한다.
     *
     * [기존 /recent 와 경로가 충돌하지 않는 이유]
     * Spring 의 경로 매칭은 더 구체적인 것을 우선한다.
     * "/recent" 는 정확히 매칭되고, 루트 "" 는 그 외의 경우에만 매칭된다.
     * 따라서 GET /internal/studies/recent 는 여전히 getRecentStudies() 로 라우팅되고, GET /internal/studies 만 이 메서드에 도달한다.
     *
     * [클래스 레벨의 @Transactional(readOnly=true) 가 여기에도 적용된다]
     * 이 컨트롤러는 클래스 레벨에 @Transactional(readOnly=true) 가 선언되어 있어
     * Study 의 @ElementCollection 컬렉션에 지연 로딩이 필요한 경우 안전하다.
     * 단 StudyAdminResponse 는 memberIds/managerIds 에 접근하지 않으므로
     * 실제로는 컬렉션 접근 자체가 없고, 기본 컬럼만 조회하는 가벼운 쿼리가 실행된다.
     */
    @GetMapping
    public ResponseEntity<Page<StudyAdminResponse>> listStudiesForAdmin(
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {

        // keyword 가 비어있으면 전체 조회, 있으면 title/path LIKE 검색
        Page<Study> page;
        if (keyword == null || keyword.isBlank()) {
            page = studyRepository.findAll(pageable);
        } else {
            // 같은 키워드를 title 과 path 양쪽에 넣어 두 필드 어디에 있든 매칭되게 한다
            page = studyRepository.findByTitleContainingIgnoreCaseOrPathContainingIgnoreCase(keyword, keyword, pageable);
        }

        // Page.map() 은 totalElements 등 메타데이터를 유지한 채 요소만 변환한다
        return ResponseEntity.ok(page.map(StudyAdminResponse::from));
    }

    /*
     * POST /internal/studies/{path}/force-close
     *
     * 관리자가 특정 스터디를 강제 종료(비공개 처리) 한다.
     *
     * [왜 요청 본문이 비어있는가]
     * 이 엔드포인트는 "스터디를 강제 종료한다" 라는 단일 동작만 수행한다.
     * 본문에 담을 파라미터가 없어도 동작이 명확하다.
     * RESTful 관점에서 "상태 전이" 를 의도한 POST 는 본문이 필수가 아니다.
     *
     * [왜 PATCH 가 아니라 POST 인가]
     * 회원 권한 변경 때 겪은 RestTemplate 의 PATCH 미지원 문제 때문이다.
     * URL 경로(/force-close) 에 의도가 충분히 드러나므로 시맨틱 손실이 적다.
     * 전 계층 POST 로 통일해 의존성 최소화를 우선했다.
     *
     * [비즈니스 로직은 전부 service 로 위임]
     * 이 컨트롤러는 HTTP 변환만 담당한다.
     * 헤더 추출 → 파라미터 변환 → service 호출 → ResponseEntity 로 감싸기.
     * 나머지는 모두 StudyInternalService 안에서 일어난다.
     */
    @PostMapping("/{path}/force-close")
    public ResponseEntity<StudyAdminResponse> forceCloseStudy(
            @PathVariable String path,
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestHeader(value = "X-Account-Id", required = false) Long requesterId) {

        StudyAdminResponse response = studyInternalService.forceClose(path, requesterId);
        return ResponseEntity.ok(response);
    }
}