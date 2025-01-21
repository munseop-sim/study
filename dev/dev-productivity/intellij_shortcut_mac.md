# mac 인텔리제이 단축키

## 코드 Edit
### 메인 메소드 생성 및 실행
- `command + n` : 새로 만들기 (mac)
- **코드 템플릿** 
  - `psvm` : 메인메소드 코드 생성
  - `sout` : print.out 코드 생성
- **코드 실행**
  - `control + shift + r` : 마우스의 포커스가 있는 코드 실행 (현재 포커스)
  - `control + r` : 바로 전에 실행시켰던 코드 실행 (이전 포커스)
### 라인 수정하기
- `command + d` : 라인 복사
- `command + delete` : 라인 삭제
- `control + shift + j` : 문자열 라인 합치기(여러개의 문자열 라인을 하나의 라인으로 합침)
  - 코드 내에서 쿼리 짤 때 많이 사용
- **라인 옮기기**
  - `option + shift + up,down` : 코드내에 아무곳이나 이동
  - `command + shift + up,down` : 문법적 오류가 없는 범위내에서 이동
  - `option + command + shift + right,left` : element 순서 변경 (html, xml 에서 사용)
### 코드 즉시보기
- `command + p` : 인자값 확인 (코드로 직접 이동하지 않고 툴팁으로 해당 메소드의 파라미터를 확인 할 수 있음)
- `option + space` : 클래스, 메소드의 내용 확인 가능
- `f1` : docs 확인

## 포커스
### 포커스 에디터
- `option + right,left` : 단어별 이동
- `option + shift + right,left`: 단어별 선택
- `Fn + right,left` : 라인 첫,끝 이동
- `shift + command + right,left`: 라인 전체 선택
- `Fn + up,down` : Page Up,Down    
### 포커스 특수키
- `option + up,down` : 포커스 범위 한 단계씩 늘리기,줄이기
- `command + [,]` : 포커스 뒤로,앞으로 가기 (다른 파일로도 이동 가능)
- `option(두번 치고 누르고 있어야 함) + up,down` : 멀티 포커스 
- `f2` : 오류라인으로 자동 포커스

## 검색
### 검색 텍스트
- `command + f` : 현재파일에서 검색
- `command + r` : 현재파일에서 교체
- `command + shift + f` : 전체에서 검색
- `command + shift + r` : 전체에서 교체
- 정규표현식으로 검색, 교체  
  - 정규표현식을 이용하여 검색
  - 정교표현식을 이용하여 교체 [] 안의 문자는 순서대로 `$1, $2...`
### 검색 기타
- `command + shift + o` : 파일검색 (패키지명을 포함해서도 검색 가능)
- `command + option + o` : 메소드 검색
- `command + shift + a` : Action 검색
- `command + e` : 최근 열었던 파일 목록 보기 (파일목록이 뜬 상태에서 `command+e`를 한번 더 누르면 최근 수정된 파일만 필터링 됨)
- `command + shfit + e` : 최근 수정한 파일 목록 보기 (최근 수정한 내용 확인)

## 자동완성
### 자동완성
- `command + shift + space` : 스마트 자동완성
- `command + space (2번)` : static method 자동완성
- `command + n` : getter/setter/생성자 자동완성 (팝업창에서 선택해서 생성)
- `control + i` : override 메소드 자동완성
### LiveTemplate
- `command + j` : live template 확인
- action 에서 live template 추가후에 반복적으로 선언해야 되는 annotation 등을 라이브템플릿으로 등록하면 편하다.

## 리팩토링
### 리팩토링 Extract
- `option + command + v` : 변수(variable) 추출하기
- `option + command + p` : 파라미터(parameter) 추출하기
- `option + command + m` : 메소드(method) 추출하기
- `f6` : 이너클래스 추출하기 (클래스이름에 포커스 이동후 `f6` 눌러서 이너 클래스 이동)
### 리팩토링 기타
- `shift + f6` : 이름 일괄 변경하기
- `command + shift + f6` : 타입 일괄 변경하기(리턴타입 변경 등에서 유용하다)
- `control + option + o` : Import 정리하기
  - `command + shift + a` 누르고 `action`에서 `optimize imports on` 실행하여 ide 에서 자동실행되도록 변경
- `command + option + l` : 코드 자동 정렬하기

## 디버깅
- `control + shift + d` : Debug모드로 실행하기 - 즉시실행
- `control + d` : Debug모드로 실행하기 - 이전실행
- `command + option + r`: Resume - 다음 브레이크 포인트로 넘어가기
- `f8` : Step Over - 라인별로 실행 확인
- `f7` : Step Into - 해당 코드가 실행되는 메소드 확인
- `shift + f8` : Step Out - 해당 코드가 실행되는 메소드를 확인할 때 호출된 쪽으로 다시 실행
- conditional debugging : 브레이크포인트 찍어놓고 해당 브레이크 포인트를 우클릭 -> condition 조건에 맞는 브레이크 포인트만 디버깅에 걸림
- `option + f8` : Evaluate Expression - 브레이크 된 상태에서 코드 실행 결과 확인
- Watch (단축키 없음)
  
## Git
### Git 기본 기능
- `command + 9` : Git View On
- `control + v` : Git Option Popup
- `control + v -> 4` : Git History
- `control + v -> 7` : Branch
- `command + k` : commit
- `command + shift + k` : push


`option + f12` : 터미널 창 띄우기 

## 추천 플러그인
- `.ignore`
- `presentation assistant` -> 인텔리제이 기본 기능으로 포함됨. `setting`에서 수정 가능
- `bash support` -> 유료
- `translation(https://plugins.jetbrains.com/plugin/8579-translation)` -> `control + command + u` : 바로 번역, `control + command + o` : 번역해서 붙여 넣기
- `material theme ui(https://plugins.jetbrains.com/plugin/8006-material-theme-ui)` : theme 플러그인 취향에 따라 설치 