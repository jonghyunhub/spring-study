-- MySQL 초기 테이블 생성 스크립트
-- 이 파일은 컨테이너 시작 시 자동으로 실행됩니다
-- 파일명 앞의 숫자(01, 02, ...)로 실행 순서가 결정됩니다

-- 한글 인코딩 설정
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

-- 데이터베이스 선택
USE coredb;

CREATE TABLE product
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       varchar(25) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE INDEX uk_name (name)
);

CREATE TABLE stock
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE INDEX uk_product_id (product_id)
);

-- 테스트용 초기 데이터
INSERT INTO product (name, created_at, updated_at)
VALUES ('두바이 쫀득 쿠키', NOW(), NOW());

INSERT INTO product (name, created_at, updated_at)
VALUES ('두바이 딱딱 강정', NOW(), NOW());


INSERT INTO stock (product_id, quantity, created_at, updated_at)
VALUES (1, 100, NOW(), NOW());

INSERT INTO stock (product_id, quantity, created_at, updated_at)
VALUES (2, 100, NOW(), NOW());