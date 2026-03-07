package com.studyolle.modules.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * ✅ Tag 엔티티 전용 데이터 접근 계층 (Spring Data JPA Repository)
 *
 * JpaRepository&lt;Tag, Long&gt;을 상속하여 기본 CRUD 메서드를 자동으로 제공받는다.
 * (save, findById, findAll, delete 등)
 *
 * @Transactional(readOnly = true) 설계 의도:
 *   - 인터페이스 레벨에 선언하여 모든 메서드에 기본적으로 읽기 전용 트랜잭션 적용
 *   - 읽기 전용 트랜잭션의 이점:
 *     (1) JPA Dirty Checking을 수행하지 않아 flush 시점의 비교 연산 생략 → 성능 향상
 *     (2) DB에 따라 읽기 전용 힌트를 전달하여 쿼리 최적화 가능
 *     (3) 의도치 않은 데이터 변경 방지 (안전장치)
 *   - save(), delete() 등 쓰기 연산은 Service 계층의 @Transactional이 우선 적용됨
 *     → Service의 @Transactional(readOnly = false, 기본값)이 Repository의 readOnly = true를 오버라이드
 *     → 따라서 쓰기 작업이 정상적으로 수행됨
 *
 * 사용처:
 *   - TagService: findOrCreateNew()에서 findByTitle + save 조합
 *   - TagZoneController: 태그 추가 시 findByTitle로 기존 태그 확인
 *   - StudySettingsController: 태그 설정 페이지에서 findAll()로 전체 태그 목록(whitelist) 조회
 */
@Transactional(readOnly = true)
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 태그 제목으로 Tag 엔티티를 조회하는 쿼리 메서드
     *
     * Spring Data JPA의 메서드 이름 기반 쿼리 파생 규칙:
     *   findBy + Title → SELECT t FROM Tag t WHERE t.title = :title
     *
     * 반환값:
     *   - 해당 제목의 태그가 존재하면 Tag 객체 반환
     *   - 존재하지 않으면 null 반환
     *     → 호출 측에서 null 체크를 통해 "기존 태그 재사용 vs 새 태그 생성" 분기
     *
     * Optional&lt;Tag&gt;이 아닌 Tag를 반환하는 이유:
     *   - 호출 패턴이 "null이면 새로 생성"이므로 Optional 언래핑보다 null 체크가 간결
     *   - 예: if (tag == null) { tag = tagRepository.save(Tag.builder().title(title).build()); }
     *
     * @param title 검색할 태그 제목 (예: "Spring", "JPA")
     * @return 일치하는 Tag 엔티티, 없으면 null
     */
    Tag findByTitle(String title);
}