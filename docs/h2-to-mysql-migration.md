# H2 데이터를 MySQL로 이전하기

이 도구는 기존 H2의 사용자 테이블 전체를 MySQL로 한 번만 이전합니다.
일반 애플리케이션 시작 시에는 실행되지 않습니다.

## 1. 사전 준비

1. 애플리케이션을 MySQL 설정으로 한 번 실행하여 테이블을 생성합니다.
2. 애플리케이션을 중지합니다. 이전 중에는 H2와 MySQL을 수정하지 않아야 합니다.
3. H2 파일과 MySQL을 각각 백업합니다.
4. `data/finalproject.mv.db`를 프로젝트의 `data` 디렉터리에 둡니다.

## 2. 환경변수 설정

Linux/macOS 예시:

```bash
export MIGRATION_MYSQL_URL='jdbc:mysql://DB주소:3306/DB이름'
export MIGRATION_MYSQL_USER='DB사용자'
read -s MIGRATION_MYSQL_PASSWORD
export MIGRATION_MYSQL_PASSWORD
```

MySQL 계정에 비밀번호가 없다면 `MIGRATION_MYSQL_PASSWORD`는 생략할 수 있습니다.

기본 H2 경로는 다음과 같습니다.

```text
jdbc:h2:file:./data/finalproject;AUTO_SERVER=TRUE
```

다른 경로라면 `MIGRATION_H2_URL`을 설정합니다.

## 3. 미리보기

다음 명령은 양쪽 DB의 테이블별 행 수만 확인하며 데이터를 변경하지 않습니다.

```bash
./gradlew migrateH2ToMySql
```

EC2에 프로젝트 소스가 없고 실행용 JAR만 배포한다면 로컬에서 독립 실행형
마이그레이션 JAR를 먼저 만듭니다.

```bash
./gradlew migrationJar
```

생성 파일:

```text
build/libs/finalProject-0.0.1-h2-to-mysql.jar
```

이 파일과 `data/finalproject.mv.db`를 EC2의 같은 작업 디렉터리 구조로 올린 뒤
다음처럼 미리보기를 실행할 수 있습니다.

```bash
java -jar finalProject-0.0.1-h2-to-mysql.jar
```

## 4. 실제 이전

MySQL 테이블이 비어 있으면 다음 명령으로 이전합니다.

```bash
./gradlew migrateH2ToMySql --args='--execute'
```

독립 실행형 JAR 사용 시:

```bash
java -jar finalProject-0.0.1-h2-to-mysql.jar --execute
```

MySQL에 초기화 데이터가 이미 있고 H2 데이터로 완전히 교체해야 할 때만 다음
명령을 사용합니다. 공통 대상 테이블의 기존 데이터가 삭제됩니다.

```bash
./gradlew migrateH2ToMySql --args='--execute --reset-target'
```

독립 실행형 JAR 사용 시:

```bash
java -jar finalProject-0.0.1-h2-to-mysql.jar --execute --reset-target
```

이전은 하나의 MySQL 트랜잭션으로 처리됩니다. 실패하면 변경 내용을 롤백하며,
성공 전 테이블별 행 수가 H2와 같은지 검증합니다.

## 5. 이전 후 확인

```sql
SELECT COUNT(*) FROM member;
```

기존 H2의 `member` 테이블은 현재 확인 기준 10행입니다. 다른 테이블도 도구가
출력한 H2 행 수와 MySQL 행 수가 같은지 확인한 뒤 애플리케이션을 시작합니다.
