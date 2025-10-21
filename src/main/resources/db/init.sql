-- DB_DINORY 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS DB_DINORY CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE DB_DINORY;

-- image_generation 테이블 생성
CREATE TABLE IF NOT EXISTS image_generation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id BIGINT,
    prompt TEXT NOT NULL,
    style VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    image_url VARCHAR(500),
    error_message TEXT,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    INDEX idx_scene_id (scene_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 참고: scene 테이블이 생성되면 아래 외래키 제약을 추가할 수 있습니다
-- ALTER TABLE image_generation
-- ADD CONSTRAINT fk_image_generation_scene
-- FOREIGN KEY (scene_id) REFERENCES scene(id) ON DELETE SET NULL;