-- 테스트 데이터 추가 스크립트
-- 주의: 이 스크립트는 개발 환경에서만 사용하세요!

USE DB_DINORY;

-- 예시: parent_id=1인 부모가 이미 존재한다고 가정하고 Child 데이터 추가
-- parent_id는 실제 DB의 member 테이블에 존재하는 ID로 변경하세요

-- Child 테스트 데이터
INSERT INTO child (parent_id, name, birth_date, gender, interests, concerns, created_at, updated_at)
VALUES
(1, '지우', '2018-05-15', '남', '["가족", "동물", "자동차"]', '["분노조절", "형제관계"]', NOW(), NOW()),
(1, '민서', '2020-03-22', '여', '["공주", "그림그리기", "음악"]', '["수면문제", "친구관계"]', NOW(), NOW());

-- 참고:
-- 1. parent_id는 member 테이블에 존재하는 실제 ID여야 합니다
-- 2. interests와 concerns는 JSON 배열 형식입니다
-- 3. 이미 child id=1이 존재하는 경우 DELETE 후 실행하거나 다른 parent_id를 사용하세요