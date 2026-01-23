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