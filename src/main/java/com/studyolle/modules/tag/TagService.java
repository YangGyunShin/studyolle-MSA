package com.studyolle.modules.tag;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ Tag 엔티티의 조회 및 생성을 담당하는 서비스
 *
 * 핵심 역할:
 *   - Tag 도메인에 대한 모든 비즈니스 로직을 캡슐화
 *   - Controller → Service → Repository 계층 원칙을 유지하여
 *     컨트롤러가 TagRepository에 직접 접근하지 않도록 함
 *
 * 제공하는 기능:
 *   - findOrCreateNew(): 태그 조회 후 없으면 새로 생성 (태그 추가 시)
 *   - findByTitle(): 태그 제목으로 단건 조회 (태그 삭제 시 존재 확인)
 *   - getAllTagTitles(): 전체 태그 제목 목록 조회 (Tagify whitelist용)
 *
 * 호출처:
 *   - TagZoneController: Account의 관심 태그 추가/삭제/조회
 *   - StudySettingsController: Study의 주제 태그 추가/삭제/조회
 *
 * @Transactional 설계:
 *   - findOrCreateNew()는 조회(SELECT)와 생성(INSERT)을 하나의 트랜잭션에서 처리
 *   - findByTitle(), getAllTagTitles()는 읽기만 하지만,
 *     클래스 레벨 @Transactional의 기본값(readOnly = false)이 적용됨
 *   - TagRepository의 @Transactional(readOnly = true)보다
 *     이 서비스의 @Transactional이 우선 적용됨 (트랜잭션 전파: REQUIRED 기본값)
 *
 * @RequiredArgsConstructor:
 *   - final 필드(tagRepository)에 대한 생성자를 Lombok이 자동 생성
 *   - Spring이 생성자 주입(Constructor Injection)으로 TagRepository 빈을 주입
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    /**
     * ✅ 태그 제목으로 기존 태그를 조회하고, 없으면 새로 생성하여 반환
     *
     * "Find or Create" 패턴:
     *   1. tagRepository.findByTitle()로 DB에서 해당 제목의 태그 검색
     *   2. 존재하면 → 기존 Tag 객체를 그대로 반환 (재사용)
     *   3. 존재하지 않으면 → 빌더로 새 Tag 생성 후 save()하여 DB에 저장 후 반환
     *
     * 이 패턴이 필요한 이유:
     *   - Tag.title에 unique 제약이 있으므로, 동일 제목을 중복 INSERT하면 예외 발생
     *   - 사용자가 입력한 태그가 이미 시스템에 존재하는지 먼저 확인해야 함
     *   - 태그는 공유 자원이므로 "Spring"이라는 태그는 시스템에 단 하나만 존재하고
     *     여러 Account/Study가 이 하나의 Tag를 참조함
     *
     * save() 반환값 활용:
     *   - tagRepository.save()는 영속화된 Tag 엔티티를 반환
     *   - @GeneratedValue로 생성된 id가 포함된 상태로 반환되므로
     *     이후 @ManyToMany 조인 테이블에 바로 사용할 수 있음
     *
     * 동시성 참고:
     *   - 두 사용자가 동시에 같은 태그를 최초 생성하면
     *     unique 제약 위반(DataIntegrityViolationException)이 발생할 수 있음
     *   - 현재는 별도의 동시성 처리(비관적/낙관적 락)가 없으므로,
     *     필요 시 @Retryable 또는 try-catch로 재조회 패턴을 추가할 수 있음
     *
     * @param tagTitle 조회/생성할 태그 제목 (예: "Spring", "Docker")
     * @return 기존 또는 새로 생성된 Tag 엔티티 (영속 상태)
     */
    public Tag findOrCreateNew(String tagTitle) {
        Tag tag = tagRepository.findByTitle(tagTitle);
        if (tag == null) {
            tag = tagRepository.save(Tag.builder().title(tagTitle).build());
        }
        return tag;
    }

    /**
     * ✅ 태그 제목으로 기존 태그를 조회 (없으면 null 반환)
     *
     * findOrCreateNew()와의 차이:
     *   - findOrCreateNew(): 없으면 새로 생성하여 반환 (태그 추가 시)
     *   - findByTitle(): 없으면 null 그대로 반환 (태그 삭제 시 존재 확인)
     *
     * 사용처:
     *   - 태그 삭제(remove) 요청 시, 해당 태그가 DB에 존재하는지 확인
     *   - null이면 컨트롤러에서 400 Bad Request를 반환하는 분기에 사용
     *
     * @param tagTitle 검색할 태그 제목
     * @return 일치하는 Tag 엔티티, 없으면 null
     */
    public Tag findByTitle(String tagTitle) {
        return tagRepository.findByTitle(tagTitle);
    }

    /**
     * ✅ 시스템에 등록된 전체 태그 제목 목록을 조회
     *
     * 사용처:
     *   - 태그 설정 페이지에서 Tagify 자동완성(whitelist)에 전달할 데이터 생성
     *   - 컨트롤러에서 ObjectMapper로 JSON 직렬화한 후 뷰에 전달
     *     예: ["Spring", "JPA", "Docker"] → '["Spring","JPA","Docker"]'
     *
     * List<String>으로 반환하는 이유:
     *   - 컨트롤러에서는 Tag 엔티티가 아닌 title(문자열)만 필요
     *   - 서비스 계층에서 변환까지 처리하여 컨트롤러의 역할을 최소화
     *   - Tag 엔티티가 컨트롤러로 노출되지 않아 계층 간 의존도 감소
     *
     * @return 전체 태그 제목 리스트 (List<String>)
     */
    public List<String> getAllTagTitles() {
        return tagRepository.findAll().stream()
                .map(Tag::getTitle)
                .collect(Collectors.toList());
    }
}