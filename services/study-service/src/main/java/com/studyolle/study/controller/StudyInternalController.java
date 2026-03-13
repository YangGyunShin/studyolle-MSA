package com.studyolle.study.controller;

import com.studyolle.study.dto.response.StudyInternalResponse;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 서비스 간 내부 통신 전용 컨트롤러
 *
 * =============================================
 * 보안 규칙 (MSA_AUTH_FLOW.md 6장 참조)
 * =============================================
 *
 * [외부 접근 차단]
 * api-gateway 가 /internal/** 를 전면 403 으로 차단하므로
 * 외부 클라이언트는 이 경로에 도달할 수 없다.
 *
 * [내부 접근 허용]
 * X-Internal-Service 헤더가 있는 서비스만 InternalRequestFilter 를 통과한다.
 * Authorization 헤더(JWT) 없이 호출된다.
 *
 * =============================================
 * 주요 사용처
 * =============================================
 *
 * [event-service]
 * - 모임 생성 전 스터디가 공개 상태이고 요청자가 관리자인지 확인
 * - 모임 목록에서 스터디 제목을 표시할 때 사용
 *
 * [admin-service]
 * - 스터디 목록 관리 화면에서 데이터 조회
 *
 * =============================================
 * [모노리틱과의 차이]
 * =============================================
 * 모노리틱에는 이 컨트롤러가 없었다.
 * 같은 프로세스 내에서 직접 서비스를 호출했기 때문이다.
 * MSA 에서는 서비스 간 통신이 HTTP 로 이루어지므로 별도 내부 API 가 필요하다.
 *
 * @see com.studyolle.study.filter.InternalRequestFilter
 */
@RestController
@RequestMapping("/internal/studies")
@RequiredArgsConstructor
public class StudyInternalController {

    private final StudyRepository studyRepository;

    /**
     * GET /internal/studies/{path}
     *
     * path 로 스터디를 조회한다. event-service 가 모임 생성 전에 호출한다.
     *
     * [Feign Client 호출 예시 — event-service 의 StudyFeignClient]
     * @GetMapping("/internal/studies/{path}")
     * StudyInternalResponse getStudy(@PathVariable String path,
     *                                @RequestHeader("X-Internal-Service") String serviceName);
     */
    @GetMapping("/{path}")
    public ResponseEntity<StudyInternalResponse> getStudy(@PathVariable String path) {
        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(StudyInternalResponse.from(study));
    }

    /**
     * GET /internal/studies/{path}/is-manager?accountId=
     *
     * 특정 사용자가 이 스터디의 관리자인지 확인한다.
     * event-service 가 관리자만 모임을 생성할 수 있도록 권한 체크할 때 사용한다.
     */
    @GetMapping("/{path}/is-manager")
    public ResponseEntity<Boolean> isManager(
            @PathVariable String path,
            @RequestParam Long accountId) {

        Study study = studyRepository.findByPath(path);
        if (study == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(study.isManagerOf(accountId));
    }
}