package com.studyolle.modules.zone;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ Zone 엔티티의 초기화, 조회를 담당하는 서비스
 *
 * 핵심 역할:
 *   - Zone 도메인에 대한 모든 비즈니스 로직을 캡슐화
 *   - Controller → Service → Repository 계층 원칙을 유지하여
 *     컨트롤러가 ZoneRepository에 직접 접근하지 않도록 함
 *
 * 제공하는 기능:
 *   - initZoneData(): 애플리케이션 시작 시 CSV에서 지역 데이터 초기화
 *   - findByCityAndProvince(): city + province 조합으로 Zone 조회 (지역 추가/삭제 시)
 *   - getAllZoneNames(): 전체 지역 이름 목록 조회 (Tagify whitelist용)
 *
 * TagService와의 구조적 대칭:
 *
 *   [데이터 성격]
 *     - TagService  : 열린 데이터 (사용자 생성)
 *     - ZoneService : 닫힌 데이터 (CSV 초기화)
 *
 *   [추가 시 동작]
 *     - TagService  : findOrCreateNew() — 없으면 새로 생성
 *     - ZoneService : findByCityAndProvince() — 없으면 null → 400
 *
 *   [삭제 시 조회]
 *     - TagService  : findByTitle()
 *     - ZoneService : findByCityAndProvince()
 *
 *   [whitelist 조회]
 *     - TagService  : getAllTagTitles()
 *     - ZoneService : getAllZoneNames()
 *
 *   [초기화]
 *     - TagService  : 없음
 *     - ZoneService : initZoneData() (@PostConstruct)
 *
 * 호출처:
 *   - TagZoneController: Account의 활동 지역 추가/삭제/조회
 *   - StudySettingsController: Study의 활동 지역 추가/삭제/조회
 *
 * @Transactional 설계:
 *   - 클래스 레벨에 선언하여 모든 public 메서드에 트랜잭션 적용
 *   - initZoneData()는 saveAll()로 대량 INSERT를 수행하므로 쓰기 트랜잭션 필요
 *   - findByCityAndProvince(), getAllZoneNames()는 읽기만 하지만
 *     클래스 레벨 기본값(readOnly = false)이 적용됨
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    /**
     * ✅ 애플리케이션 시작 시 CSV 파일에서 지역 데이터를 일괄 로드
     *
     * @PostConstruct 동작:
     *   - Spring 빈 생성 + 의존성 주입 완료 직후 자동 실행
     *   - 애플리케이션 시작마다 호출되지만, DB에 데이터가 있으면 스킵 (멱등성 보장)
     *
     * 처리 흐름:
     *   1. zoneRepository.count()로 기존 데이터 존재 여부 확인
     *   2. 비어 있으면 → classpath의 zones_kr.csv 파일을 읽어 Zone 엔티티로 변환
     *   3. saveAll()로 일괄 저장
     *
     * CSV 형식 (zones_kr.csv):
     *   Seoul,서울,서울특별시
     *   Busan,부산,부산광역시
     *   Incheon,인천,인천광역시
     *   ...
     *
     * ClassPathResource 사용 이유:
     *   - src/main/resources 디렉토리의 파일을 클래스패스 기준으로 접근
     *   - JAR로 패키징되어도 정상 작동 (파일 시스템 경로가 아닌 클래스패스 사용)
     *
     * @throws IOException CSV 파일 읽기 실패 시
     */
    @PostConstruct
    public void initZoneData() throws IOException {
        if (zoneRepository.count() == 0) {
            Resource resource = new ClassPathResource("zones_kr.csv");

            List<Zone> zoneList = Files.readAllLines(resource.getFile().toPath(), StandardCharsets.UTF_8).stream()
                    .map(line -> {
                        String[] split = line.split(",");
                        return Zone.builder()
                                .city(split[0])
                                .localNameOfCity(split[1])
                                .province(split[2])
                                .build();
                    }).collect(Collectors.toList());

            zoneRepository.saveAll(zoneList);
        }
    }

    /**
     * ✅ 영문 도시명 + 시/도명으로 Zone 엔티티를 조회
     *
     * TagService.findByTitle()에 대응하는 메서드.
     * 지역 추가/삭제 시 프론트에서 전달된 ZoneForm의 파싱 결과로 DB 조회에 사용.
     *
     * Tag와의 핵심 차이:
     *   - Tag 추가 시: findOrCreateNew() → 없으면 새로 생성
     *   - Zone 추가 시: findByCityAndProvince() → 없으면 null (400 Bad Request)
     *   - Zone은 닫힌 데이터이므로 사용자가 임의로 지역을 생성할 수 없음
     *
     * @param cityName     영문 도시명 (예: "Seoul") — ZoneForm.getCityName()에서 추출
     * @param provinceName 시/도 행정구역명 (예: "서울특별시") — ZoneForm.getProvinceName()에서 추출
     * @return 일치하는 Zone 엔티티, 없으면 null
     */
    public Zone findByCityAndProvince(String cityName, String provinceName) {
        return zoneRepository.findByCityAndProvince(cityName, provinceName);
    }

    /**
     * ✅ 시스템에 등록된 전체 지역 이름 목록을 조회
     *
     * TagService.getAllTagTitles()에 대응하는 메서드.
     * 지역 설정 페이지에서 Tagify 자동완성(whitelist)에 전달할 데이터를 생성.
     *
     * 반환 형식:
     *   - Zone.toString()을 사용하여 "Seoul(서울)/서울특별시" 형식의 문자열 리스트 반환
     *   - 컨트롤러에서 ObjectMapper로 JSON 직렬화 후 뷰에 전달
     *     예: ["Seoul(서울)/서울특별시", "Busan(부산)/부산광역시"] → JSON 문자열
     *
     * List<String>으로 반환하는 이유:
     *   - 컨트롤러에서는 Zone 엔티티가 아닌 표시용 문자열만 필요
     *   - 서비스 계층에서 변환까지 처리하여 컨트롤러의 역할을 최소화
     *   - Zone 엔티티가 컨트롤러로 노출되지 않아 계층 간 의존도 감소
     *
     * @return 전체 지역 이름 리스트 (List<String>)
     */
    public List<String> getAllZoneNames() {
        return zoneRepository.findAll().stream()
                .map(Zone::toString)
                .collect(Collectors.toList());
    }
}