package com.studyolle.infra.config;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.NameTokenizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

// AppConfig 클래스는 Spring의 설정 클래스임을 나타냄
// 이 클래스 안에서 @Bean으로 정의된 메서드들이 Spring Bean으로 등록됨
@Configuration
public class AppConfig {

    /**
     * 비밀번호 암호화용 PasswordEncoder Bean 생성
     *
     * Spring Security에서 제공하는 PasswordEncoderFactories의 createDelegatingPasswordEncoder()를 사용.
     * - 여러 암호화 방식을 내부적으로 지원하는 DelegatingPasswordEncoder 생성
     * - 기본적으로 Bcrypt를 사용하지만, 다른 암호화 알고리즘도 지원 가능
     * - 저장된 암호 앞에 {id} prefix가 붙어서 어떤 암호화 방식을 사용했는지 명시할 수 있음
     *
     * 예: {bcrypt}$2a$10$abcde... 이렇게 저장됨.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * ModelMapper Bean 생성
     *
     * ModelMapper는 객체 간 매핑을 도와주는 라이브러리 (예: DTO <-> Entity 변환 시 유용)
     *
     * 추가 설정:
     * - setDestinationNameTokenizer(NameTokenizers.UNDERSCORE):
     *   매핑 대상 객체의 속성명을 언더스코어 기준으로 분리하여 매핑함
     *   예: first_name → firstName
     * - setSourceNameTokenizer(NameTokenizers.UNDERSCORE):
     *   매핑 원본 객체의 속성명도 동일하게 언더스코어 기준으로 토큰화
     *   → DB나 외부 API 응답이 snake_case일 때 활용도가 높음
     *
     * 이 설정을 통해 소스와 대상 객체 간 snake_case ↔ camelCase 변환이 좀 더 유연하게 이루어짐.
     */
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        // 언더스코어로 이름을 토큰화해서 매핑을 더 똑똑하게 수행하도록 설정
        modelMapper.getConfiguration()
                .setDestinationNameTokenizer(NameTokenizers.UNDERSCORE)
                .setSourceNameTokenizer(NameTokenizers.UNDERSCORE);

        return modelMapper;
    }
}
