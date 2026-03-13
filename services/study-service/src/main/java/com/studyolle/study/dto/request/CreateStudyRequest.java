package com.studyolle.study.dto.request;

import com.studyolle.study.entity.JoinType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

// 스터디 생성 요청 시 클라이언트가 HTTP Body 로 전송하는 데이터를 담는 DTO.
// @Getter 만 선언하고 @Setter 를 열지 않는 이유:
// 요청 객체는 "클라이언트가 보낸 값 그대로" 를 표현하는 용도이므로
// 한번 역직렬화된 이후 값이 바뀌어서는 안 된다.
// Jackson 은 @Getter 만 있어도 기본 생성자를 통해 역직렬화할 수 있다.
@Getter
public class CreateStudyRequest {

    // path 유효성 검사 정규식 상수.
    // public static 으로 선언해서 StudyService 에서도 오류 메시지에 패턴을 참조할 수 있게 한다.
    // 패턴 의미: 한글(자음/완성형), 영문 소문자, 숫자, 언더스코어, 하이픈만 허용. 2~20자.
    public static final String VALID_PATH_PATTERN = "^[ㄱ-ㅎ가-힣a-z0-9_-]{2,20}$";

    // 스터디 URL 식별자. 예: /study/spring-boot-study
    // 세 가지 제약이 모두 통과해야 유효하다:
    //   @NotBlank  - null, "", " " 모두 거부
    //   @Length    - 2자 이상 20자 이하
    //   @Pattern   - 위 정규식과 일치해야 함
    // DB 중복 검사는 형식 검사 통과 이후 StudyService 에서 별도로 수행한다
    @NotBlank
    @Length(min = 2, max = 20)
    @Pattern(regexp = VALID_PATH_PATTERN)
    private String path;

    // 스터디 표시 이름. 카드 목록과 상세 페이지 상단에 노출된다
    @NotBlank
    @Length(max = 50)
    private String title;

    // 스터디 카드에 노출되는 한 줄 소개
    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    // 스터디 상세 페이지 본문. 에디터로 작성한 HTML 이 포함될 수 있다
    @NotBlank
    private String fullDescription;

    // 가입 방식. 클라이언트가 값을 보내지 않으면 OPEN 으로 기본 초기화된다.
    // Jackson 은 JSON 에 해당 키가 없으면 필드의 초기화 값을 그대로 사용한다
    private JoinType joinType = JoinType.OPEN;
}

/*
 * [DTO 란?]
 *
 * DTO(Data Transfer Object)는 계층 간 데이터를 전달하는 순수한 데이터 컨테이너다.
 * 비즈니스 로직 없이 필드와 getter 만 가진다.
 *
 * 엔티티(Study)를 컨트롤러에서 @RequestBody 로 직접 받지 않고 DTO 를 쓰는 이유:
 * Study 엔티티에는 published, closed, recruiting 같은 내부 상태 필드가 있는데,
 * 엔티티를 그대로 @RequestBody 로 받으면 클라이언트가 이 필드들을 임의로 설정할 수 있다.
 * DTO 는 "클라이언트가 실제로 보낼 수 있는 필드"만 정의하므로 이 위험을 원천 차단한다.
 *
 *
 * [Bean Validation 이 동작하는 흐름]
 *
 * 컨트롤러에 @Valid @RequestBody CreateStudyRequest request 처럼 @Valid 를 붙이면,
 * Spring MVC 가 JSON 을 객체로 변환한 직후 자동으로 검증을 실행한다.
 *
 * 검증 실패 시 MethodArgumentNotValidException 이 발생하고,
 * GlobalExceptionHandler 가 이를 잡아 400 Bad Request 응답으로 변환한다.
 * 덕분에 서비스나 컨트롤러에서 null 체크, 길이 체크를 직접 작성하지 않아도 된다.
 *
 *
 * [path 검증이 두 단계인 이유]
 *
 * 1단계(@Pattern): 형식 검사. DB 쿼리 없이 즉시 처리된다.
 * 2단계(existsByPath): 중복 검사. DB 에 SELECT 쿼리가 필요하다.
 *
 * 형식이 잘못된 요청에 DB 쿼리를 낭비하지 않으려면 두 단계를 순서대로 처리하는 것이 맞다.
 * Bean Validation 이 먼저 실행되고, 통과한 경우에만 서비스 로직(DB 조회)으로 넘어간다.
 */
