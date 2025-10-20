# Window 인텔리제이 단축키

## 코드 Edit
### 메인 메소드 생성 및 실행
- `alt + insert` : 새로 만들기 
- 코드 템플릿 
  - `psvm` : 메인메소드 코드 생성
  - `sout` : print.out 코드 생성
- 코드 실행
  - `ctrl + shift + f10` : 마우스의 포커스가 있는 코드 실행 (현재 포커스)
  - `shift + f10` : 바로 전에 실행시켰던 코드 실행 (이전 포커스)
### 라인 수정하기
- `ctrl + d` : 라인 복사
- `ctrl + y` : 라인 삭제
- `ctrl + shift + j` : 문자열 라인 합치기(여러개의 문자열 라인을 하나의 라인으로 합침)
  - 코드 내에서 쿼리 짤 때 많이 사용
- 라인 옮기기
  - `alt + shift + up` : 코드내에 아무곳이나 이동
  - `ctrl + shift + up` : 문법적 오류가 없는 범위내에서 이동
  - `alt + ctrl + shift + right/left` : element 순서 변경 (html, xml에서 사용)
### 코드 즉시보기
- `ctrl + p` : 인자값 확인 (코드로 직접 이동하지 않고 툴팁으로 해당 메소드의 파라미터를 확인 할 수 있음)
- `shift + ctrl + i` : 클래스, 메소드의 내용 확인 가능
- `ctrl + q` : docs 확인

## 포커스
### 포커스 에디터
- `ctrl + right/left` : 단어별 이동
- `ctrl + shift + right/left`: 단어별 선택
- `Home/End` : 라인 첫/끝 이동
- `shift + Home/End`: 라인 전체 선택
- `PageUp/PageDown` : Page Up/Down
### 포커스 특수키
- `ctrl + up/down` : 포커스 범위 한 단계씩 늘리기/줄이기
- `ctrl + alt + right/left` : 포커스 뒤로/앞으로 가기 (다른 파일로도 이동 가능)
- `ctrl(두번 치고 누르고 있어야 함) + up/down` : 멀티 포커스
- `f2` : 오류라인으로 자동 포커스

## 검색
### 검색 텍스트
- `ctrl + f` : 현재파일에서 검색
- `ctrl + r` : 현재파일에서 교체
- `ctrl + shift + f` : 전체에서 검색
- `ctrl + shift + r`: 전체에서 교체
- 정규표현식으로 검색, 교체 :
  - 정규표현식을 이용하여 검색
  - 정교표현식을 이용하여 교체 [] 안의 문자는 순서대로 `$1, $2...`
### 검색 기타
- `ctrl + shift + o` : 파일검색 (패키지명을 포함해서도 검색 가능)
- `shift + ctrl + alt + n` : 메소드 검색
- `ctrl + shift + a` : Action 검색
- `ctrl + e` : 최근 열었던 파일 목록 보기
- `ctrl + shfit + e` : 최근 수정한 파일 목록 보기

## 자동완성
### 자동완성
- `ctrl + shift + space` : 스마트 자동완성
- `ctrl + space (2번)` : static method 자동완성
- `alt + insert` : getter/setter/생성자 자동완성 (팝업창에서 선택해서 생성)
- `ctrl + i` : override 메소드 자동완성
### LiveTemplate
- `ctrl + j` : live template 확인
- action 에서 live template 추가후에 반복적으로 선언해야 되는 annotation 등을 라이브템플릿으로 등록하면 편하다. 

## 리팩토링
### 리팩토링 Extract
- `ctrl + alt + v` : 변수 추출하기
- `alt + ctrl + p` : 파라미터 추출하기
- `alt + ctrl + m` : 메소드 추출하기
- `f6` : 이너클래스 추출하기 (클래스이름에 포커스 이동후 `f6` 눌러서 이너 클래스 이동)
### 리팩토링 기타
- `shift + f6` : 이름 일괄 변경하기
- `ctrl + shift + f6` : 타입 일괄 변경하기(리턴타입 변경 등에서 유용하다)
- `ctrl + alt + o` : Import 정리하기
  - `ctrl + shift + a` 누르고 `action`에서 `optimize imports on` 실행하여 ide 에서 자동실행되도록 변경
- `ctrl + alt + l` : 코드 자동 정렬하기

## 디버깅
-  ~~Debug모드로 실행하기 - 즉시실행 (단축키 없음)~~
- `shift + f9` : Debug모드로 실행하기 - 이전실행
- `f9`: Resume - 다음 브레이크 포인트로 넘어가기
- `f8` : Step Over - 라인별로 실행 확인
- `f7` : Step Into - 해당 코드가 실행되는 메소드 확인
- `shift + f8` : Step Out - 해당 코드가 실행되는 메소드를 확인할 때 호출된 쪽으로 다시 실행
- conditional debugging : 브레이크포인트 찍어놓고 해당 브레이크 포인트를 우클릭 -> condition 조건에 맞는 브레이크 포인트만 디버깅에 걸림
- `alt + f8` : Evaluate Expression - 브레이크 된 상태에서 코드 실행 결과 확인
- Watch (단축키 없음)

## Git
### Git 기본 기능
- `alt + 9` : Git View On
- ``alt +  `(tab 위 버튼)`` : Git Option Popup
- ``alt +  `(tab 위 버튼) -> 4`` : Git History
- ``alt +  `(tab 위 버튼) -> 7`` : Branch
- `ctrl + k` : commit
- `ctrl + shift + k` : push
