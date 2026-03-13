package com.studyolle.study.client;

import com.studyolle.study.client.dto.TagDto;
import com.studyolle.study.client.dto.ZoneDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * metadata-service 내부 API 호출 Feign Client
 *
 * =============================================
 * 호출 규칙 (MSA_AUTH_FLOW.md 4장 참조)
 * =============================================
 * - /internal/** 경로로만 호출한다.
 * - X-Internal-Service: study-service 헤더를 반드시 포함한다.
 * - Authorization 헤더는 포함하지 않는다 (JWT 불필요).
 *
 * [TODO] metadata-service Phase 추가 시 아래 엔드포인트를 구현해야 한다:
 * - POST   /internal/tags              (태그 findOrCreate)
 * - GET    /internal/tags              (전체 태그 목록 - Tagify 자동완성용)
 * - GET    /internal/tags/search       (키워드 매칭 태그 ID 목록 - 검색용)
 * - GET    /internal/zones/search      (city+province 로 zone 조회)
 * - GET    /internal/zones             (전체 지역 목록)
 * - GET    /internal/zones/search-by-keyword (키워드 매칭 zone ID 목록 - 검색용)
 * - POST   /internal/tags/batch        (ID 목록으로 태그 다건 조회 - 응답 DTO 조립용)
 * - POST   /internal/zones/batch       (ID 목록으로 지역 다건 조회 - 응답 DTO 조립용)
 */
@FeignClient(name = "metadata-service")
public interface MetadataFeignClient {

    /**
     * 태그 제목으로 태그를 조회하고, 없으면 새로 생성한다.
     * 모노리틱의 TagService.findOrCreateNew() 와 동일한 역할.
     */
    @PostMapping("/internal/tags")
    TagDto findOrCreateTag(@RequestParam String title,
                           @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 전체 태그 제목 목록 조회 — Tagify 자동완성 whitelist 로 사용
     */
    @GetMapping("/internal/tags")
    List<String> getAllTagTitles(@RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 키워드를 포함하는 태그의 ID 목록 조회
     * findByKeyword 검색 시 QueryDSL where 조건에 사용
     */
    @GetMapping("/internal/tags/search")
    Set<Long> findTagIdsByKeyword(@RequestParam String keyword,
                                  @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * ID 목록으로 태그 다건 조회 — StudyResponse 조립 시 태그 이름 표시용
     */
    @PostMapping("/internal/tags/batch")
    List<TagDto> getTagsByIds(@RequestBody Set<Long> ids,
                              @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * city + province 로 지역 단건 조회
     * 모노리틱의 ZoneService.findByCityAndProvince() 와 동일한 역할.
     */
    @GetMapping("/internal/zones/search")
    ZoneDto findZone(@RequestParam String city,
                     @RequestParam String province,
                     @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 전체 지역 이름 목록 조회 ("Seoul(서울)/서울특별시" 형태)
     * Tagify 자동완성 whitelist 로 사용
     */
    @GetMapping("/internal/zones")
    List<String> getAllZoneNames(@RequestHeader("X-Internal-Service") String serviceName);

    /**
     * 키워드를 포함하는 지역의 ID 목록 조회
     * findByKeyword 검색 시 QueryDSL where 조건에 사용
     */
    @GetMapping("/internal/zones/search-by-keyword")
    Set<Long> findZoneIdsByKeyword(@RequestParam String keyword,
                                   @RequestHeader("X-Internal-Service") String serviceName);

    /**
     * ID 목록으로 지역 다건 조회 — StudyResponse 조립 시 지역 이름 표시용
     */
    @PostMapping("/internal/zones/batch")
    List<ZoneDto> getZonesByIds(@RequestBody Set<Long> ids,
                                @RequestHeader("X-Internal-Service") String serviceName);
}