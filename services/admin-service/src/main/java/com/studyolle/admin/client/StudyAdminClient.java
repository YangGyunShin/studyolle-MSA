package com.studyolle.admin.client;

import com.studyolle.admin.client.dto.PageResponse;
import com.studyolle.admin.client.dto.StudyAdminDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * study-service 의 /internal/studies/** 경로를 호출하는 Feign Client.
 *
 * [왜 name 을 "study-service" 로 쓰는가]
 * Eureka 에 등록된 서비스 이름이다.
 * Feign 내부의 LoadBalancer 가 이 이름을 실제 인스턴스의 host:port 로 변환하므로,
 * 이 코드에는 localhost:8083 같은 하드코딩된 주소가 등장하지 않는다.
 * study-service 가 여러 인스턴스로 수평 확장되어도 코드는 수정할 필요가 없다.
 *
 * [같은 name 의 FeignClient 가 이미 있어도 되는가]
 * admin-service 에는 study-service 를 호출하는 다른 Feign Client 는 아직 없지만,
 * 있더라도 Spring Cloud OpenFeign 은 "contextId" 를 명시하면 같은 서비스를 가리키는 여러 인터페이스를 공존시킬 수 있다.
 * 지금은 하나뿐이라 contextId 가 불필요하다.
 *
 * [왜 두 메서드 모두 X-Internal-Service 헤더를 받는가]
 * study-service 의 InternalRequestFilter 가 그 헤더 없이 들어온 /internal/** 요청을 403 으로 막기 때문이다.
 * admin-service 는 "admin-service" 라는 자기 이름을 심어서 보내야 한다.
 * study-service 의 ALLOWED 목록에 "admin-service" 가 이미 들어있음을 확인했다
 * (study-service/src/main/java/com/studyolle/study/filter/InternalRequestFilter.java).
 */
@FeignClient(name = "study-service")
public interface StudyAdminClient {

    /**
     * 관리자용 스터디 목록 조회.
     * <p>
     * 메서드 시그니처가 그대로 HTTP 요청으로 변환된다:
     * GET http://study-service/internal/studies?keyword={keyword}&page={page}&size={size}
     * Header: X-Internal-Service: admin-service
     */
    @GetMapping("/internal/studies")
    PageResponse<StudyAdminDto> listStudies(
            @RequestParam(required = false) String keyword,
            @RequestParam int page,
            @RequestParam int size,
            @RequestHeader("X-Internal-Service") String serviceName
    );

    /**
     * 스터디 강제 비공개 처리.
     *
     * 요청 본문이 없다. "강제 종료" 라는 단일 동작이라 추가 파라미터가 필요 없다.
     * study-service 쪽 컨트롤러도 @RequestBody 를 받지 않는다.
     *
     * X-Account-Id 헤더는 감사·로깅 용도로 함께 전달한다.
     * 현재 service 에서는 가드 검증에만 쓰이지만, 나중에 "누가 어느 스터디를 차단했는지" 이력을 남길 때
     * 이 값이 바로 사용된다.
     */
    @PostMapping("/internal/studies/{path}/force-close")
    StudyAdminDto forceCloseStudy(
            @PathVariable("path") String path,
            @RequestHeader("X-Internal-Service") String serviceName,
            @RequestHeader("X-Account-Id") Long requesterId
    );
}