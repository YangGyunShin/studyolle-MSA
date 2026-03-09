/**
 * glass-validation.js  (MSA 버전)
 *
 * [모노리틱과의 차이]
 * - 모노리틱: 실시간 유효성 검사 + Bootstrap form submit 이벤트 가로채기 두 역할을 함께 했다.
 *             Thymeleaf 서버사이드 렌더링이 input 값을 미리 채워줄 수 있으므로
 *             페이지 로드 시점에도 is-valid 검사를 했다.
 *
 * - MSA:      폼이 전통적인 submit 을 하지 않는다.
 *             모든 API 호출은 각 페이지의 JS 가 fetch() 로 직접 처리한다.
 *             따라서 form submit 이벤트 리스너는 제거했다.
 *             대신 각 페이지 JS 가 버튼 클릭 시 모든 필드 통과 여부를
 *             한 번에 확인할 수 있도록 checkAllValid() 헬퍼를 export 한다.
 *
 * [담당 기능]
 *   1. 실시간 입력 유효성 검사: data-validate 속성 값에 맞는 규칙 적용
 *   2. 통과 시 .input-wrapper 에 .is-valid 클래스 추가 → CSS 체크 아이콘 바운스 애니메이션
 *   3. checkAllValid(): 지정된 필드 목록이 모두 is-valid 인지 반환 (버튼 활성화 판단용)
 *
 * [유효성 규칙]
 *   - nickname : 3~20자, 공백 없이 영문/숫자/한글만 허용
 *   - email    : 기본 이메일 형식 정규식 검사
 *   - password : 8자 이상
 *   - username : 1자 이상 (이메일 또는 닉네임이므로 느슨한 검증)
 *
 * [사용 방법]
 *   각 HTML 페이지에서 이 파일을 <script src="/js/glass-validation.js"> 로 로드하면
 *   data-validate 속성이 붙은 모든 input 에 자동으로 실시간 검사가 적용된다.
 *
 *   버튼 활성화 등 "모든 필드 통과 여부" 판단이 필요할 때는:
 *     if (GlassValidation.checkAllValid(['nickname', 'email', 'password'])) { ... }
 */

var GlassValidation = (function () {

    // ----------------------------------------------------------------
    // 유효성 규칙 정의
    // 각 규칙은 (value: string) => boolean 형태의 순수 함수다.
    // 새로운 필드 타입이 필요하면 여기에 추가한다.
    // ----------------------------------------------------------------
    var validators = {
        nickname: function (v) {
            // 3~20자, 공백 없이 영문/숫자/한글만 허용
            return v.length >= 3 && v.length <= 20 && /^[a-zA-Z0-9가-힣]+$/.test(v);
        },
        email: function (v) {
            return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
        },
        password: function (v) {
            // 8자 이상 (서버에서 추가 검증 - 특수문자 등 - 을 수행한다)
            return v.length >= 8;
        },
        username: function (v) {
            // 로그인 시 이메일 또는 닉네임 중 하나이므로 느슨하게 검사
            return v.trim().length >= 1;
        }
    };

    // ----------------------------------------------------------------
    // 실시간 검사 초기화
    // DOMContentLoaded 에서 자동으로 호출된다.
    // data-validate 속성이 있는 모든 input 에 'input' 이벤트 리스너를 등록한다.
    // ----------------------------------------------------------------
    function init() {
        document.querySelectorAll('input[data-validate]').forEach(function (input) {
            var type = input.getAttribute('data-validate');
            var wrapper = input.closest('.input-wrapper');

            if (!wrapper) return; // .input-wrapper 가 없는 input 은 무시

            input.addEventListener('input', function () {
                if (validators[type] && validators[type](input.value)) {
                    wrapper.classList.add('is-valid');
                } else {
                    wrapper.classList.remove('is-valid');
                }
            });
        });
    }

    // ----------------------------------------------------------------
    // checkAllValid(fieldIds)
    //
    // 지정된 id 목록의 input 들이 모두 is-valid 상태인지 확인한다.
    // 각 페이지의 JS 에서 API 호출 버튼 활성화 판단에 사용한다.
    //
    // 사용 예시:
    //   var ok = GlassValidation.checkAllValid(['nickname', 'email', 'password']);
    //   document.getElementById('submitBtn').disabled = !ok;
    //
    // @param  fieldIds  string[]  확인할 input 의 id 목록
    // @return boolean   모두 통과하면 true, 하나라도 미통과면 false
    // ----------------------------------------------------------------
    function checkAllValid(fieldIds) {
        return fieldIds.every(function (id) {
            var input = document.getElementById(id);
            if (!input) return false;
            var wrapper = input.closest('.input-wrapper');
            if (!wrapper) return false;
            return wrapper.classList.contains('is-valid');
        });
    }

    // ----------------------------------------------------------------
    // validateNow(inputId)
    //
    // 특정 필드를 즉시 강제 검사한다.
    // 페이지 로드 후 기존 값이 있을 때 (예: 이메일 재발송 페이지에서 URL 파라미터로
    // 이메일 값을 채워넣을 때) 체크 아이콘이 바로 표시되도록 트리거한다.
    //
    // 사용 예시:
    //   document.getElementById('email').value = urlParams.get('email');
    //   GlassValidation.validateNow('email');
    //
    // @param inputId  string  대상 input 의 id
    // ----------------------------------------------------------------
    function validateNow(inputId) {
        var input = document.getElementById(inputId);
        if (!input) return;
        var type = input.getAttribute('data-validate');
        var wrapper = input.closest('.input-wrapper');
        if (!wrapper || !type) return;

        if (validators[type] && validators[type](input.value)) {
            wrapper.classList.add('is-valid');
        } else {
            wrapper.classList.remove('is-valid');
        }
    }

    // DOMContentLoaded 에서 init() 자동 실행
    document.addEventListener('DOMContentLoaded', init);

    // 외부에서 사용할 공개 API
    return {
        checkAllValid: checkAllValid,
        validateNow: validateNow
    };

})();
