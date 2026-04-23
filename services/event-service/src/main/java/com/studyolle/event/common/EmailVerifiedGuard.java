package com.studyolle.event.common;

/**
 * 이메일 인증 여부에 따라 쓰기 작업을 허용/차단하는 가드 유틸.
 *
 * =============================================================
 * [왜 별도의 유틸 클래스로 분리했는가]
 * =============================================================
 *
 * 이메일 인증 체크 로직은 한 줄짜리 간단한 if 문에 불과하다:
 *
 *   if (!Boolean.TRUE.equals(emailVerified)) {
 *       throw new IllegalStateException("이메일 인증이 필요합니다.");
 *   }
 *
 * 그러나 이 코드는 쓰기 엔드포인트 수십 개에 반복 등장하게 된다.
 * 반복되는 코드를 한 곳에 모으면 다음 이점을 얻는다:
 *
 * 1. 에러 메시지 일관성 — "이메일 인증이 필요합니다" 라는 문구를 한 곳에서만 관리
 * 2. 예외 타입 일관성 — IllegalStateException 으로 통일해 GlobalExceptionHandler 가
 *    403 Forbidden 으로 매핑하는 규칙 준수
 * 3. 미래 확장 용이 — 나중에 "인증 메일 재전송" 링크 정보를 응답에 추가하고 싶어지면
 *    이 한 클래스만 고치면 된다
 * 4. 테스트 용이 — 가드 로직의 단위 테스트가 이 클래스 하나만 대상으로 가능
 *
 * =============================================================
 * [왜 AOP 나 인터셉터가 아니라 명시적 호출인가]
 * =============================================================
 *
 * 선택지 1 — @EmailVerified 커스텀 어노테이션 + AOP
 *   장점: 컨트롤러 메서드에 어노테이션만 붙이면 자동 검증
 *   단점: 검증 로직이 "보이지 않는 곳" 에서 일어나 디버깅이 어렵고,
 *         어노테이션이 없는 메서드는 검증을 빠뜨릴 위험이 높다
 *
 * 선택지 2 — HandlerInterceptor 로 특정 패턴의 요청 차단
 *   장점: 메서드 수정 없이 적용 가능
 *   단점: "어떤 엔드포인트가 인증 필수인가" 가 한 곳에 모여있어야 하는데
 *         각 서비스마다 WebMvcConfig 에 경로 패턴을 나열해야 하고,
 *         그 패턴이 실제 메서드와 어긋날 때 찾기 어렵다
 *
 * 선택지 3 (채택) — 명시적 guard() 호출
 *   장점: 각 메서드가 "나는 이메일 인증이 필요하다" 를 자기 시작 부분에서 명시적으로 선언.
 *         컨트롤러 코드를 읽기만 해도 인증 필수 여부가 한눈에 보인다.
 *   단점: 한 줄 추가가 필요함 (그러나 그 한 줄이 의도를 정직하게 드러낸다)
 *
 * MSA 의 각 서비스는 자기 경계의 비즈니스 규칙을 자기가 가시적으로 소유해야 한다는
 * 원칙과 부합한다. 가드 호출이 있어야 "이 엔드포인트는 인증 필수" 임이 명백해지고,
 * 없으면 "이 엔드포인트는 비인증 사용자도 쓸 수 있음" 이 명백해진다.
 */
public final class EmailVerifiedGuard {

    private EmailVerifiedGuard() {
        // 유틸리티 클래스 — 인스턴스 생성 금지
    }

    /**
     * 이메일 인증을 완료한 사용자만 통과시킨다.
     * <p>
     * 사용 예시:
     *
     * @param emailVerified 게이트웨이가 주입한 X-Account-Email-Verified 헤더 값  null 이면 (과거 JWT 로 claim 이 없었던 경우) 인증 안 된 것으로 간주
     * @throws IllegalStateException 인증되지 않은 경우. GlobalExceptionHandler 가
     *                               403 Forbidden 으로 매핑한다.
     * @PostMapping("/api/studies") public ResponseEntity<?> createStudy(
     * @RequestHeader("X-Account-Id") Long accountId,
     * @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
     * @Valid @RequestBody CreateStudyRequest request) {
     * <p>
     * EmailVerifiedGuard.require(emailVerified);   // ← 이 한 줄
     * <p>
     * // 이후 정상 로직
     * }
     */

    public static void require(Boolean emailVerified) {
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new IllegalStateException("이메일 인증이 필요한 기능입니다. 인증 메일을 확인해 주세요.");
        }
    }
}