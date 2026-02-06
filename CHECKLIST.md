# 최종 체크리스트

## 1. 공통

- [ ] loginId, name, email은 trim() 후 빈 값이면 실패 처리
- [ ] password는 trim()하지 않고, 공백/개행/제어문자 포함 시 실패 처리
- [ ] 필수 필드 누락/형식 오류에 대해 일관된 에러 응답

## 2. 회원가입

- [ ] 요청 바디에 loginId, password, name, birthDate, email 존재
- [ ] loginId는 trim()+소문자 정규화 후 영문/숫자만 허용
- [ ] loginId 길이 4~20 검증
- [ ] loginId 중복 불가(정규화 기준)
- [ ] name은 한글/영문/공백만 허용, 길이 1~50
- [ ] birthDate는 yyyy-MM-dd 파싱 가능, 미래 날짜 금지
- [ ] email 길이 ≤ 254, 기본 이메일 형식 검증
- [ ] email 공백/제어문자 포함 시 실패
- [ ] password 길이 8~16 검증
- [ ] password 허용 문자: 영문 대/소문자, 숫자, ASCII 특수문자만
- [ ] password 공백/개행 포함 시 실패
- [ ] password에 생년월일 포함 금지(YYYYMMDD, YYYY-MM-DD 모두 검사)
- [ ] 비밀번호 해시 저장
- [ ] 응답에 비밀번호/해시 포함 금지

## 3. 내 정보 조회

- [ ] 요청 헤더 X-Loopers-LoginId, X-Loopers-LoginPw 존재
- [ ] 헤더 값 null/blank 검사
- [ ] loginId는 trim()+소문자 정규화 후 조회
- [ ] 헤더 비밀번호와 해시 비교로 인증
- [ ] 반환 필드는 loginId, name, birthDate, email만 포함
- [ ] 이름 마스킹: 마지막 글자 *
- [ ] 1글자 이름은 *로 반환

## 4. 비밀번호 수정

- [ ] 헤더 인증 선행 (X-Loopers-LoginId, X-Loopers-LoginPw)
- [ ] 요청 바디에 currentPassword, newPassword 존재
- [ ] currentPassword가 현재 비밀번호와 일치
- [ ] newPassword가 현재 비밀번호와 동일하면 실패
- [ ] newPassword는 회원가입과 동일한 비밀번호 규칙 적용
- [ ] 새 비밀번호 해시 저장
- [ ] 응답에 비밀번호/해시 포함 금지

## 5. 테스트

### 단위 테스트

- [ ] loginId 정규화 및 형식/길이 검증
- [ ] name 허용 문자/길이 검증
- [ ] birthDate 포맷/미래 날짜 금지
- [ ] email 형식/길이/공백·제어문자 실패
- [ ] password 길이/허용 문자/공백 금지
- [ ] 생년월일 포함 금지(YYYYMMDD, YYYY-MM-DD)
- [ ] 이름 마스킹(1글자/다글자)

### 통합 테스트

- [ ] 회원가입 성공 및 해시 저장 확인
- [ ] loginId 중복 실패
- [ ] 내 정보 조회 성공/인증 실패
- [ ] 비밀번호 변경 성공/실패(불일치/동일/규칙 위반)

### E2E 테스트

- [ ] 회원가입 정상/실패
- [ ] 내 정보 조회 정상/실패
- [ ] 비밀번호 변경 정상/실패
