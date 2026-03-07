package com.studyolle.modules.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.CurrentUser;
import com.studyolle.modules.account.service.TagZoneService;
import com.studyolle.modules.tag.Tag;
import com.studyolle.modules.tag.TagForm;
import com.studyolle.modules.tag.TagService;
import com.studyolle.modules.zone.Zone;
import com.studyolle.modules.zone.ZoneForm;
import com.studyolle.modules.zone.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ✅ 관심 태그 및 활동 지역 설정을 담당하는 컨트롤러
 *
 * 담당 기능:
 *   - 태그 설정 페이지 렌더링 (GET /settings/tags)
 *   - 태그 추가/삭제 (POST /settings/tags/add, /settings/tags/remove) — AJAX
 *   - 지역 설정 페이지 렌더링 (GET /settings/zones)
 *   - 지역 추가/삭제 (POST /settings/zones/add, /settings/zones/remove) — AJAX
 *
 * 설계 의도:
 *   - 태그와 지역은 둘 다 "사용자의 관심사/활동 범위"를 정의하는 유사한 도메인
 *   - 구현 패턴이 거의 동일: Tagify UI + AJAX 요청 + @ManyToMany 연관관계 조작
 *   - 따라서 하나의 컨트롤러로 묶어 응집도를 높임
 *
 * AJAX 통신 방식:
 *   - 태그/지역 추가·삭제는 전체 페이지 리로드 없이 AJAX 요청으로 처리
 *   - @ResponseBody를 사용하여 ResponseEntity(상태 코드)만 반환
 *   - @RequestBody로 JSON 데이터를 TagForm/ZoneForm DTO에 바인딩
 *   - 프론트엔드에서 Tagify 라이브러리와 jQuery AJAX를 사용하여 통신
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/settings")
public class TagZoneController {

    private final TagZoneService tagZoneService;
    private final TagService tagService;
    private final ZoneService zoneService;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────
    // 태그 (Tag) 관련 엔드포인트
    // ──────────────────────────────────────────

    /**
     * ✅ 태그 설정 페이지 렌더링
     *
     * 뷰에 전달하는 데이터:
     *   - account: 현재 로그인한 사용자 (네비게이션 표시용)
     *   - tags: 현재 사용자가 등록한 태그 목록 (List<String>) — Tagify 초기값
     *   - whitelist: 전체 태그 목록을 JSON 문자열로 직렬화 — Tagify 자동완성용
     *
     * Tagify 동작 원리:
     *   - 프론트엔드에서 Tagify 라이브러리가 input 필드를 태그 입력 UI로 변환
     *   - whitelist로 전달된 JSON이 자동완성(auto-suggest) 데이터로 사용됨
     *   - 사용자가 태그를 추가/삭제하면 AJAX로 서버에 실시간 반영
     *
     * View: resources/templates/settings/tags.html
     *
     * @throws JsonProcessingException ObjectMapper의 JSON 직렬화 실패 시
     */
    @GetMapping("/tags")
    public String updateTags(@CurrentUser Account account, Model model) throws JsonProcessingException {
        model.addAttribute("account", account);

        // 현재 사용자의 태그 조회
        // - account는 Detached 상태일 수 있으므로 Service 내부에서 ID로 재조회하여 영속 상태로 만듦
        // - 영속 상태에서 getTags() 호출 시 Lazy Loading으로 연관 태그를 조회
        Set<Tag> tags = tagZoneService.getTags(account);
        List<String> tagTitles = tags.stream()
                .map(Tag::getTitle)
                .collect(Collectors.toList());
        model.addAttribute("tags", tagTitles);

        // 전체 태그 목록을 JSON으로 직렬화하여 Tagify 자동완성(whitelist)에 전달
        // TagService.getAllTagTitles()가 List<String> 변환까지 처리
        // 예: ["Java", "Spring", "JPA"] → '["Java","Spring","JPA"]'
        List<String> allTagTitles = tagService.getAllTagTitles();
        model.addAttribute("whitelist", objectMapper.writeValueAsString(allTagTitles));

        return "settings/tags";
    }

    /**
     * ✅ 태그 추가 (AJAX 요청)
     *
     * 처리 흐름:
     *   1. TagForm에서 태그 제목(title) 추출
     *   2. TagService.findOrCreateNew()로 기존 태그 조회 또는 새로 생성
     *   3. TagZoneService를 통해 Account ↔ Tag @ManyToMany 연관관계에 추가
     *
     * @param account 현재 로그인한 사용자 (@CurrentUser로 SecurityContext에서 추출)
     * @param tagForm AJAX 요청 본문에서 바인딩된 태그 정보 ({tagTitle: "Java"})
     * @return 200 OK
     */
    @PostMapping("/tags/add")
    @ResponseBody
    public ResponseEntity addTag(@CurrentUser Account account, @RequestBody TagForm tagForm) {
        // TagService가 "조회 → 없으면 생성" 로직을 캡슐화
        Tag tag = tagService.findOrCreateNew(tagForm.getTagTitle());

        // Account ↔ Tag 연관관계 추가 (Detached → 영속 재조회 후 컬렉션 조작)
        tagZoneService.addTag(account, tag);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ 태그 삭제 (AJAX 요청)
     *
     * 처리 흐름:
     *   1. TagService.findByTitle()로 DB에서 해당 태그 조회 → 없으면 400 Bad Request
     *   2. TagZoneService를 통해 Account ↔ Tag 연관관계에서 제거
     *
     * 참고: 태그 자체는 삭제하지 않음 (다른 사용자나 스터디에서도 사용 중일 수 있으므로)
     *
     * @param account 현재 로그인한 사용자
     * @param tagForm AJAX 요청 본문의 태그 정보
     * @return 200 OK 또는 400 Bad Request
     */
    @PostMapping("/tags/remove")
    @ResponseBody
    public ResponseEntity removeTag(@CurrentUser Account account, @RequestBody TagForm tagForm) {
        Tag tag = tagService.findByTitle(tagForm.getTagTitle());

        if (tag == null) {
            return ResponseEntity.badRequest().build();
        }

        tagZoneService.removeTag(account, tag);
        return ResponseEntity.ok().build();
    }

    // ──────────────────────────────────────────
    // 지역 (Zone) 관련 엔드포인트
    // ──────────────────────────────────────────

    /**
     * ✅ 지역 설정 페이지 렌더링
     *
     * 뷰에 전달하는 데이터:
     *   - account: 현재 로그인한 사용자
     *   - zones: 현재 사용자가 등록한 지역 목록 (List<String>)
     *     → Zone.toString() 오버라이드에 의해 "Seoul(서울)/서울특별시" 형식으로 표현
     *   - whitelist: 전체 지역 목록을 JSON 문자열로 직렬화 — Tagify 자동완성용
     *     → 태그와 달리 지역은 DB에 미리 등록된 데이터만 허용 (enforceWhitelist: true)
     *
     * View: resources/templates/settings/zones.html
     *
     * @throws JsonProcessingException ObjectMapper의 JSON 직렬화 실패 시
     */
    @GetMapping("/zones")
    public String updateZonesForm(@CurrentUser Account account, Model model) throws JsonProcessingException {
        model.addAttribute(account);

        // 현재 사용자가 선택한 지역 목록 조회
        Set<Zone> zones = tagZoneService.getZones(account);
        model.addAttribute("zones", zones.stream().map(Zone::toString).collect(Collectors.toList()));

        // 전체 지역 목록을 JSON으로 직렬화하여 Tagify 자동완성(whitelist)에 전달
        // ZoneService.getAllZoneNames()가 Zone → String 변환까지 처리
        List<String> allZoneNames = zoneService.getAllZoneNames();
        model.addAttribute("whitelist", objectMapper.writeValueAsString(allZoneNames));

        return "settings/zones";
    }

    /**
     * ✅ 지역 추가 (AJAX 요청)
     *
     * 처리 흐름:
     *   1. ZoneForm이 프론트에서 전달된 문자열을 파싱
     *      예: "Seoul(서울)/서울특별시" → city: Seoul, province: 서울특별시
     *   2. ZoneService.findByCityAndProvince()로 DB에서 조회 → 없으면 400 Bad Request
     *      (태그와 달리, 지역은 새로 생성하지 않고 미리 등록된 데이터만 사용)
     *   3. TagZoneService를 통해 Account ↔ Zone @ManyToMany 연관관계에 추가
     *
     * @param account  현재 로그인한 사용자
     * @param zoneForm AJAX 요청 본문의 지역 정보 ({zoneName: "Seoul(서울)/서울특별시"})
     * @return 200 OK 또는 400 Bad Request
     */
    @PostMapping("/zones/add")
    @ResponseBody
    public ResponseEntity addZone(@CurrentUser Account account, @RequestBody ZoneForm zoneForm) {
        Zone zone = zoneService.findByCityAndProvince(zoneForm.getCityName(), zoneForm.getProvinceName());
        if (zone == null) {
            return ResponseEntity.badRequest().build();
        }
        tagZoneService.addZone(account, zone);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ 지역 삭제 (AJAX 요청)
     *
     * - ZoneService로 DB에서 Zone 엔티티 조회 → 없으면 400 Bad Request
     * - TagZoneService를 통해 Account ↔ Zone 연관관계에서 제거
     * - Zone 엔티티 자체는 삭제하지 않음 (시스템 공유 데이터)
     *
     * @param account  현재 로그인한 사용자
     * @param zoneForm AJAX 요청 본문의 지역 정보
     * @return 200 OK 또는 400 Bad Request
     */
    @PostMapping("/zones/remove")
    @ResponseBody
    public ResponseEntity removeZone(@CurrentUser Account account, @RequestBody ZoneForm zoneForm) {
        Zone zone = zoneService.findByCityAndProvince(zoneForm.getCityName(), zoneForm.getProvinceName());
        if (zone == null) {
            return ResponseEntity.badRequest().build();
        }
        tagZoneService.removeZone(account, zone);
        return ResponseEntity.ok().build();
    }
}