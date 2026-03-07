package com.studyolle.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Lombok 어노테이션: 로그를 위한 Logger 생성 (@Slf4j 사용 시 log 변수 자동 생성)
@Slf4j

// Spring 비동기 처리를 활성화시키는 어노테이션
// 이 어노테이션이 있어야 @Async 어노테이션이 작동함
@EnableAsync

// 이 클래스가 Spring의 설정 클래스임을 선언
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * AsyncConfigurer 인터페이스를 구현하여
     * Spring에서 사용할 비동기용 Executor를 커스터마이징 한다.
     *
     * 이 메서드에서 반환한 Executor가
     * @Async 어노테이션이 붙은 메서드들의 실행 스레드풀로 사용됨.
     */
    @Override
    public Executor getAsyncExecutor() {
        // Spring에서 제공하는 스레드 풀 기반 Task Executor
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 현재 시스템의 CPU 코어 수를 가져옴
        int processors = Runtime.getRuntime().availableProcessors();

        // 현재 프로세스가 사용 가능한 코어 개수를 로그로 출력 (서버 성능 모니터링용)
        log.info("processors count {}", processors);

        /*
          Core Pool Size 설정
          - 항상 유지할 최소 스레드 개수 (코어 스레드 개수)
          - CPU 코어 수 만큼 설정하여 CPU를 효율적으로 사용할 수 있도록 함
         */
        executor.setCorePoolSize(processors);

        /*
          Max Pool Size 설정
          - 최대 스레드 개수 (필요 시 코어 스레드를 넘어 추가 생성)
          - 일반적으로 코어의 2배 정도로 설정
          - 과한 수치를 주면 CPU 컨텍스트 스위칭 부하가 증가할 수 있음
         */
        executor.setMaxPoolSize(processors * 2);

        /*
          작업 큐 크기 설정 (Queue Capacity)
          - 작업이 몰릴 경우 대기할 수 있는 요청 수
          - 큐가 꽉 차면 MaxPoolSize까지 스레드 증가
          - 큐가 꽉 차고 MaxPoolSize도 도달하면 이후 작업은 거부됨
         */
        executor.setQueueCapacity(50);

        /*
          Idle 상태 스레드의 유지 시간 설정 (초 단위)
          - 유휴 스레드가 유지될 최대 시간
          - 이 시간이 지나면 스레드를 종료하여 리소스 절약
         */
        executor.setKeepAliveSeconds(60);

        /*
          생성되는 스레드 이름 접두사 설정 (디버깅 및 모니터링에 도움)
          → 예: AsyncExecutor-1, AsyncExecutor-2 ...
         */
        executor.setThreadNamePrefix("AsyncExecutor-");

        // 스레드풀 초기화 (설정 적용)
        executor.initialize();

        // 커스터마이징한 executor 반환 (이게 실제 @Async에서 사용될 스레드풀)
        return executor;
    }
}