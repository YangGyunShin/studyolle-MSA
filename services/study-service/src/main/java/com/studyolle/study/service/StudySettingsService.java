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
 * StudySettingsService — 스터디 설정 변경 비즈니스 로직 담당
 *
 * =============================================
 * [모노리틱 참조: StudySettingsService.java]
 * =============================================
 *
 * [서비스 분리 배경 — 모노리틱과 동일]
 * StudyController → StudyService
 * StudySettingsController → StudySettingsService
 * 단일 책임 원칙(SRP): 스터디 생성/가입 관련은 StudyService, 설정 변경은 이 클래스가 담당.
 *
 * =============================================
 * [모노리틱과의 주요 변경사항]
 * =============================================
 *
 * [가장 큰 변경: Tag/Zone 처리]
 * 모노리틱에서는 TagService, ZoneService 를 주입받아 로컬 DB 에서 조회했으나,
 * MSA 길 1 에서는 이 두 서비스를 완전히 제거하고 MetadataFeignClient 로 대체한다.
 *
 *   모노리틱                    MSA 길 1
 *   TagService.findOrCreateNew  → MetadataFeignClient.findOrCreateTag()
 *   ZoneService.findByCityAndProvince → MetadataFeignClient.findZone()
 *   study.getTags().add(tag)    → study.getTagIds().add(tagDto.getId())
 *   study.getZones().add(zone)  → study.getZoneIds().add(zoneDto.getId())
 *
 * [이벤트 발행 제거]
 * 모노리틱에서는 StudyUpdateEvent 를 발행했으나 Phase 5 RabbitMQ 로 대체 예정.
 * 현재는 이벤트 발행 코드 없음.
 *
 * [isValidPath 중복 검증]
 * 모노리틱과 동일하게 현재 path 와 다를 때만 중복 검증 수행.
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
     * [모노리틱 참조: StudySettingsService.updateStudyDescription(study, form)]
     * Dirty Checking: findByPath 로 영속 상태 획득 후 set → 자동 반영.
     */
    public void updateStudyDescription(Study study,
                                       UpdateStudyDescriptionRequest request) {
        study.setShortDescription(request.getShortDescription());
        study.setFullDescription(request.getFullDescription());

        // [Phase 5 TODO]
        // StudyUpdateEvent 발행 → notification-service 가 구독자에게 알림
        // eventPublisher.publishEvent(new StudyUpdatedEvent(study, "소개를 수정했습니다."));
    }

    // ============================
    // 배너 이미지 수정
    // ============================

    /**
     * 배너 이미지를 변경한다.
     *
     * [모노리틱 참조: StudySettingsService.updateStudyImage(study, image)]
     */
    public void updateStudyImage(Study study, String image) {
        study.setImage(image);
    }

    /**
     * 배너 사용 여부를 활성화한다.
     *
     * [모노리틱 참조: StudySettingsService.enableStudyBanner(study)]
     */
    public void enableStudyBanner(Study study) {
        study.setUseBanner(true);
    }

    /**
     * 배너 사용 여부를 비활성화한다.
     *
     * [모노리틱 참조: StudySettingsService.disableStudyBanner(study)]
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
     * [모노리틱 변경 — 핵심]
     * 모노리틱:
     *   Tag tag = tagService.findOrCreateNew(tagTitle);
     *   study.getTags().add(tag);
     *
     * MSA 길 1:
     *   TagDto tagDto = metadataFeignClient.findOrCreateTag(tagTitle, "study-service");
     *   study.getTagIds().add(tagDto.getId());
     *
     * 이유: study-service 에 Tag 엔티티가 없으므로 metadata-service 에 위임한다.
     * ID 만 저장하므로 FK 없이 서비스 간 DB 분리가 유지된다.
     */
    public void addTag(Study study, String tagTitle) {
        TagDto tagDto = metadataFeignClient.findOrCreateTag(tagTitle, "study-service");
        study.getTagIds().add(tagDto.getId());
    }

    /**
     * 스터디에서 태그를 제거한다.
     *
     * [모노리틱 변경]
     * study.getTags().remove(tag) → study.getTagIds().remove(tagId)
     *
     * 태그 ID 를 얻기 위해 metadata-service 를 조회한다.
     * 없는 태그를 제거하는 경우 조용히 무시한다.
     */
    public void removeTag(Study study, String tagTitle) {
        TagDto tagDto = metadataFeignClient.findOrCreateTag(tagTitle, "study-service");
        study.getTagIds().remove(tagDto.getId());
    }

    // ============================
    // 지역 추가/제거
    // ============================

    /**
     * 스터디에 활동 지역을 추가한다.
     *
     * [모노리틱 변경 — 핵심]
     * 모노리틱:
     *   Zone zone = zoneRepository.findByCityAndProvince(city, province);
     *   study.getZones().add(zone);
     *
     * MSA 길 1:
     *   ZoneDto zoneDto = metadataFeignClient.findZone(city, province, "study-service");
     *   study.getZoneIds().add(zoneDto.getId());
     *
     * ZoneRequest 파싱: "Seoul(서울)/서울특별시" → city="Seoul", province="서울특별시"
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
     * [모노리틱 변경]
     * study.getZones().remove(zone) → study.getZoneIds().remove(zoneId)
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
     * 스터디를 공개한다 (비가역적).
     *
     * [모노리틱 참조: StudySettingsService.publish(study)]
     * Study.publish() 가 상태 검증 + 필드 변경을 담당하고,
     * 이 메서드는 트랜잭션 경계 + 이벤트 발행(현재 제거됨)만 담당한다.
     */
    public void publish(Study study) {
        study.publish();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    /**
     * 스터디를 종료한다 (비가역적).
     *
     * [모노리틱 참조: StudySettingsService.close(study)]
     */
    public void close(Study study) {
        study.close();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    /**
     * 팀원 모집을 시작한다.
     *
     * [모노리틱 참조: StudySettingsService.startRecruit(study)]
     */
    public void startRecruit(Study study) {
        study.startRecruit();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    /**
     * 팀원 모집을 중단한다.
     *
     * [모노리틱 참조: StudySettingsService.stopRecruit(study)]
     */
    public void stopRecruit(Study study) {
        study.stopRecruit();
        // [Phase 5 TODO] StudyUpdatedEvent 발행
    }

    // ============================
    // 경로/제목 변경
    // ============================

    /**
     * 스터디 경로를 변경한다.
     *
     * [모노리틱 참조: StudySettingsService.updateStudyPath(study, path)]
     * 현재 path 와 다를 때만 중복 검증을 수행한다.
     */
    public void updateStudyPath(Study study, String newPath) {
        if (!study.getPath().equals(newPath) && studyRepository.existsByPath(newPath)) {
            throw new IllegalArgumentException("이미 사용 중인 경로입니다: " + newPath);
        }
        study.setPath(newPath);
    }

    /**
     * 스터디 제목을 변경한다.
     *
     * [모노리틱 참조: StudySettingsService.updateStudyTitle(study, title)]
     */
    public void updateStudyTitle(Study study, String newTitle) {
        study.setTitle(newTitle);
    }

    /**
     * 스터디 가입 방식을 변경한다.
     *
     * [추가]
     * 모노리틱에는 없었으나 MSA 에서 joinType 설정 페이지를 위해 추가.
     */
    public void updateJoinType(Study study, String joinType) {
        try {
            study.setJoinType(JoinType.valueOf(joinType));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 가입 방식입니다: " + joinType);
        }
    }
}