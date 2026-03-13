package com.studyolle.study.controller;

import com.studyolle.study.dto.response.StudyInternalResponse;
import com.studyolle.study.entity.Study;
import com.studyolle.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 서비스 간 내부 통신 전용 컨트롤러.
 *
 * =============================================
 * 이 컨트롤러가 필요한 이유
 * =============================================
 *
 * 모노리틱에서는 같은 JVM 프로세스 안에 있는 서비스들이 서로의 메서드를 직접 호출했다.
 * MSA 에서는 각 서비스가 독립적인 프로세스로 실행되므로 HTTP 를 통해서만 통신할 수 있다.
 * event-service 가 스터디 정보가 필요하면 study-service 의 이 컨트롤러로 HTTP 요청을 보낸다.
 *
 * =============================================
 * /internal/** 보안 계층 구조
 * =============================================
 *
 * 이 경로는 두 가지 방어막으로 외부 접근을 차단한다:
 *
 * 1차 방어 — api-gateway:
 *   application.yml 에서 /internal/** 경로를 전면 403 으로 차단한다.
 *   외부 클라이언트(브라우저, 앱)는 이 경로에 도달조차 할 수 없다.
 *
 * 2차 방어 — InternalRequestFilter (각 서비스):
 *   서비스 내부 네트워크에서 들어오는 요청도 X-Internal-Service 헤더가 없으면 403 을 반환한다.
 *   api-gateway 를 우회하더라도 헤더 없이는 데이터에 접근할 수 없다.
 *
 * =============================================
 * Service 계층 없이 Repository 를 직접 호출하는 이유
 * =============================================
 *
 * 이 컨트롤러는 단순 조회만 수행한다. 비즈니스 로직이 없다.
 * Service 를 거치면 불필요한 중간 계층이 추가되므로 Repository 를 직접 사용한다.
 * 단, 데이터를 변경하는 작업이 생기면 반드시 Service 를 추가해야 한다.
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

    /**
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
}