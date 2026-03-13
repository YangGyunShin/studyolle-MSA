package com.studyolle.study.client;

import com.studyolle.study.client.dto.TagDto;
import com.studyolle.study.client.dto.ZoneDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * metadata-service 내부 API 호출 Feign Client.
 *
 * =============================================
 * @FeignClient 동작 원리
 * =============================================
 *
 * @FeignClient(name = "metadata-service") 를 선언하면
 * Spring Cloud OpenFeign 이 이 인터페이스의 구현체를 런타임에 자동 생성한다.
 *
 * name = "metadata-service" 는 Eureka 에 등록된 서비스 이름이다.
 * Feign 이 HTTP 요청을 보낼 때 Eureka 에서 "metadata-service" 의 실제 주소(IP:포트)를 조회하고
 * 로드 밸런싱을 적용하여 요청을 전달한다.
 * URL 을 하드코딩하지 않아도 되므로 서비스 주소가 바뀌어도 코드 수정이 필요 없다.
 *
 * =============================================
 * X-Internal-Service 헤더를 모든 메서드에 선언하는 이유
 * =============================================
 *
 * metadata-service 의 InternalRequestFilter 는 /internal/** 경로에 대해
 * X-Internal-Service 헤더를 검증한다. 이 헤더가 없으면 403 을 반환한다.
 * 따라서 모든 메서드 파라미터에 @RequestHeader("X-Internal-Service") 를 선언하고
 * 호출 시 "study-service" 를 전달해야 한다.
 *
 * =============================================
 * [TODO] metadata-service Phase 구현 대상 엔드포인트
 * =============================================
 *
 * metadata-service 에는 아래 엔드포인트들이 구현되어야 한다:
 * - POST   /internal/tags                (태그 findOrCreate)
 * - GET    /internal/tags                (전체 태그 이름 목록 — Tagify 자동완성용)
 * - GET    /internal/tags/search         (키워드 매칭 태그 ID 목록 — 검색용)
 * - POST   /internal/tags/batch          (ID 목록으로 태그 다건 조회 — 응답 DTO 조립용)
 * - GET    /internal/zones/search        (city + province 로 지역 단건 조회)
 * - GET    /internal/zones               (전체 지역 이름 목록 — Tagify 자동완성용)
 * - GET    /internal/zones/search-by-keyword (키워드 매칭 지역 ID 목록 — 검색용)
 * - POST   /internal/zones/batch         (ID 목록으로 지역 다건 조회 — 응답 DTO 조립용)
 */
@FeignClient(name = "metadata-service")
public interface MetadataFeignClient {

    /**
     * 태그 제목으로 태그를 조회하고, 없으면 새로 생성한다.
     *
     * 모노리틱의 TagService.findOrCreateNew() 와 동일한 역할이다.
     * 반환된 TagDto.getId() 를 study.tagIds 컬렉션에 추가한다.
     *
     * @param title       찾거나 생성할 태그 이름
     * @param serviceName 호출 서비스 식별자 ("study-service")
     */
    @PostMapping("/internal/tags")
    TagDto findOrCreateTag(@RequestParam String title,
                           @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 전체 태그 이름 목록을 조회한다.
     *
     * StudySettingsController 의 태그 설정 화면에서 Tagify 자동완성 whitelist 로 사용한다.
     * List<String> 으로 태그 이름만 반환하여 응답 크기를 최소화한다.
     */
    @GetMapping("/internal/tags")
    List<String> getAllTagTitles(@RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 키워드를 이름에 포함하는 태그의 ID 목록을 조회한다.
     *
     * StudyController.searchStudies() 에서 키워드 검색 시
     * QueryDSL 의 study.tagIds.any().in(tagIds) 조건에 사용한다.
     * 이름 검색이 불가능한 study-service DB 에서 태그 ID 를 통해 간접 검색하는 방식이다.
     *
     * @param keyword 검색 키워드 (태그 이름에 포함 여부 확인)
     */
    @GetMapping("/internal/tags/search")
    Set<Long> findTagIdsByKeyword(@RequestParam String keyword,
                                  @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * ID 목록으로 태그 정보를 일괄 조회한다.
     *
     * StudyResponse 조립 시 tagIds 를 태그 이름으로 변환하여 보여줄 때 사용한다.
     * N번 개별 호출 대신 한 번의 batch 호출로 N+1 API 호출 문제를 방지한다.
     *
     * @param ids 조회할 태그 ID Set
     */
    @PostMapping("/internal/tags/batch")
    List<TagDto> getTagsByIds(@RequestBody Set<Long> ids,
                              @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * city + province 로 지역 정보를 단건 조회한다.
     *
     * 모노리틱의 ZoneService.findByCityAndProvince() 와 동일한 역할이다.
     * 반환된 ZoneDto.getId() 를 study.zoneIds 컬렉션에 추가한다.
     * 존재하지 않는 지역이면 null 을 반환한다.
     *
     * @param city     도시 영문명 (예: "Seoul")
     * @param province 지역명 (예: "서울특별시")
     */
    @GetMapping("/internal/zones/search")
    ZoneDto findZone(@RequestParam String city,
                     @RequestParam String province,
                     @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 전체 지역 이름 목록을 조회한다.
     *
     * "Seoul(서울)/서울특별시" 형태의 문자열 목록을 반환한다.
     * StudySettingsController 의 지역 설정 화면에서 Tagify 자동완성 whitelist 로 사용한다.
     */
    @GetMapping("/internal/zones")
    List<String> getAllZoneNames(@RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 키워드를 이름에 포함하는 지역의 ID 목록을 조회한다.
     *
     * StudyController.searchStudies() 에서 키워드 검색 시
     * QueryDSL 의 study.zoneIds.any().in(zoneIds) 조건에 사용한다.
     *
     * @param keyword 검색 키워드 (지역 이름에 포함 여부 확인)
     */
    @GetMapping("/internal/zones/search-by-keyword")
    Set<Long> findZoneIdsByKeyword(@RequestParam String keyword,
                                   @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * ID 목록으로 지역 정보를 일괄 조회한다.
     *
     * StudyResponse 조립 시 zoneIds 를 지역 이름으로 변환하여 보여줄 때 사용한다.
     *
     * @param ids 조회할 지역 ID Set
     */
    @PostMapping("/internal/zones/batch")
    List<ZoneDto> getZonesByIds(@RequestBody Set<Long> ids,
                                @RequestHeader("X-Internal-Service") String serviceName);
}