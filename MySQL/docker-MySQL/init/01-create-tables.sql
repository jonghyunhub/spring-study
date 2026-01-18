-- MySQL 초기 테이블 생성 스크립트
-- 이 파일은 컨테이너 시작 시 자동으로 실행됩니다
-- 파일명 앞의 숫자(01, 02, ...)로 실행 순서가 결정됩니다

-- 데이터베이스 선택
USE coredb;

-- 예제 테이블: users
-- CREATE TABLE IF NOT EXISTS users (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     username VARCHAR(50) NOT NULL UNIQUE,
--     email VARCHAR(100) NOT NULL UNIQUE,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     INDEX idx_username (username),
--     INDEX idx_email (email)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 예제 테이블: posts
-- CREATE TABLE IF NOT EXISTS posts (
--     id BIGINT AUTO_INCREMENT PRIMARY KEY,
--     user_id BIGINT NOT NULL,
--     title VARCHAR(255) NOT NULL,
--     content TEXT,
--     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
--     INDEX idx_user_id (user_id),
--     INDEX idx_created_at (created_at)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 위 주석을 해제하고 필요한 테이블을 추가하세요
-- 여러 개의 SQL 파일을 만들어서 관리할 수도 있습니다:
-- 01-create-tables.sql
-- 02-insert-data.sql
-- 03-create-indexes.sql
