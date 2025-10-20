## 1. 본인 브랜치 생성
```bash
git checkout -b 브랜치명
ex) git checkout -b haseonj
```

## 2. 저장소 fork


## 3. fork 한 저장소 clone


```bash
git clone -b 브랜치명 --single-branch base_저장소_url
ex) git clone -b haseonj --single-branch GIT_REPOSITORY_URL
```

## 4. 기능 구현 브랜치 생성


```bash
git checkout -b 브랜치명
ex) git checkout -b feat/board
```

## 5. 기능 구현


## 6. 원격 저장소에 올리기


```bash
git push origin 신규_기능_브랜치명
ex) git push origin feat/board
```

## 7. pull request 생성

target branch를 **base 저장소의 자신 브랜치**로 지정합니다.

리뷰어, 마일스톤, 라벨등 지정합니다.

리뷰어는 해당 PR 에 리뷰를 남깁니다.

## 8. 리뷰 피드백 반영 후 다시 커밋 후 푸시

리뷰 후 수정사항이 있다면 다시 PR을 날릴 필요 없이 코드 수정 후 커밋을 하면 자동으로 반영됩니다.

## 9. merge

### 새로운 기능 추가 시 아래 순서대로 작업

## 10. 현재 브랜치 삭제 후 새로운 브랜치 생성


```bash
git checkout 브랜치명
git branch -D 신규_기능_브랜치명
ex) git checkout haseonj
ex) git brach -D feat/board
```

## 11. 통합한 저장소와 동기화하기 위해 메인 저장소 추가 (최초 1회)


```bash
git remote add 저장소_별칭 base_저장소_url
ex) git remote add upstream https://gitlab.a2dcorp.co.kr/platform/dojebakery-admin
// 저장소 목록 확인 명령어
git remote -v
```

## 12. base 저장소에서 자신 브랜치 가져오기(갱신)


```bash
git fetch upstream 브랜치명
ex) git fetch upstream haseonj
```

## 13. base 저장소 브랜치와 동기화하기


```bash
git rebase upstream/브랜치명
ex) git rebase upstream/haseonj
```

## 14. 새로운 기능 브랜치 생성


```bash
git checkout -b 신규_기능_브랜치명
ex) git checkout -b feat/order
```

## 15. git에 이미 추가되었으나, 파일은 삭제하지 않고 git에서 제외하는 방법
1. `gitignore`파일에 제외할 파일명(or 폴더) 추가
2. `git rm --cached [제외할 파일명]`
