# MySQL 해보면서 제대로 먹어보기

## Unique Index

[MySQL InnoDB Unique Index는 어떻게 정합성을 보장할까?](https://velog.io/@jonghyun3668/MySQL-InnoDB-Unique-Index%EB%8A%94-%EC%96%B4%EB%96%BB%EA%B2%8C-%EB%8F%99%EC%9E%91%ED%95%A0%EA%B9%8C-Feat.DeadLock)

## Named Lock
[Spring에서 MySQL NamedLock을 사용시 주의점 (Thread 와 Connection은 다르게 관리된다)](https://velog.io/@jonghyun3668/Spring%EC%97%90%EC%84%9C-MySQL-NamedLock%EC%9D%84-%EC%82%AC%EC%9A%A9%EC%8B%9C-%EC%A3%BC%EC%9D%98%EC%A0%90) 

# MySQL 실행 방법
    1. MySQL 컨테이너 시작: docker-compose up -d
    2. MySQL 컨테이너 중지: docker-compose down
    3. MySQL 컨테이너 재시작: docker-compose restart
    4. MySQL 접속: docker exec -it jonghyun-mysql-study mysql -u root -p
    5. 로그 확인: docker-compose logs -f mysql

 # 초기 테이블 생성 방법:
    - ./init 폴더에 .sql 파일을 추가하면 컨테이너 시작 시 자동 실행됩니다
    - 예: ./init/01-create-tables.sql

 # 데이터 볼륨:
    - mysql-data: MySQL 데이터 영구 저장