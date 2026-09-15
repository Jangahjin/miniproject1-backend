-- =====================================================================
-- V1: 초기 스키마 (docs/DATABASE.md §3)
-- 순서: 확장 → 테이블 → 인덱스 → 제약
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- 테이블
-- ---------------------------------------------------------------------

CREATE TABLE region (
    code       VARCHAR(10) PRIMARY KEY,
    sido       VARCHAR(20)      NOT NULL,
    sigungu    VARCHAR(30)      NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL
);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(30)  NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'ADMIN')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    report_count  INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pharmacy (
    id             BIGSERIAL PRIMARY KEY,
    hira_code      VARCHAR(30) UNIQUE,
    name           VARCHAR(100)     NOT NULL,
    address_road   VARCHAR(255),
    address_jibun  VARCHAR(255),
    region_code    VARCHAR(10) REFERENCES region (code),
    lat            DOUBLE PRECISION NOT NULL,
    lng            DOUBLE PRECISION NOT NULL,
    phone          VARCHAR(20),
    business_hours JSONB,
    is_active      BOOLEAN          NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE drug (
    id           BIGSERIAL PRIMARY KEY,
    item_seq     VARCHAR(20) UNIQUE,
    name         VARCHAR(200) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    maker        VARCHAR(100),
    category     VARCHAR(50)  NOT NULL
        CHECK (category IN ('해열진통', '소화제', '감기약', '연고', '소독약', '비타민', '기타')),
    form         VARCHAR(50),
    package_unit VARCHAR(50)  NOT NULL,
    otc_flag     BOOLEAN      NOT NULL DEFAULT true,
    base_price   INTEGER,
    image_url    VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE uploaded_file (
    id            BIGSERIAL PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_by   BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE price_report (
    id              BIGSERIAL PRIMARY KEY,
    pharmacy_id     BIGINT      NOT NULL REFERENCES pharmacy (id),
    drug_id         BIGINT      NOT NULL REFERENCES drug (id),
    user_id         BIGINT REFERENCES app_user (id),
    price           INTEGER     NOT NULL CHECK (price BETWEEN 100 AND 200000),
    purchased_at    DATE        NOT NULL,
    source          VARCHAR(20) NOT NULL DEFAULT 'FORM'
        CHECK (source IN ('FORM', 'SEED', 'RECEIPT_OCR', 'PARTNER')),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'HIDDEN', 'REJECTED')),
    flagged         BOOLEAN     NOT NULL DEFAULT false,
    flag_reason     VARCHAR(100)
        CHECK (flag_reason IS NULL OR flag_reason IN ('OUTLIER_HIGH', 'OUTLIER_LOW', 'DUPLICATE', 'MANUAL')),
    receipt_file_id BIGINT REFERENCES uploaded_file (id),
    memo            VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pharmacy_drug_price_stat (
    id                BIGSERIAL PRIMARY KEY,
    pharmacy_id       BIGINT      NOT NULL REFERENCES pharmacy (id),
    drug_id           BIGINT      NOT NULL REFERENCES drug (id),
    rep_price         INTEGER     NOT NULL,
    min_price         INTEGER     NOT NULL,
    max_price         INTEGER     NOT NULL,
    avg_price         INTEGER     NOT NULL,
    report_count      INTEGER     NOT NULL,
    last_reported_at  DATE        NOT NULL,
    window_days       SMALLINT    NOT NULL,
    calculated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 인덱스
-- ---------------------------------------------------------------------

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id) WHERE revoked_at IS NULL;

CREATE INDEX idx_pharmacy_lat_lng   ON pharmacy (lat, lng);
CREATE INDEX idx_pharmacy_region    ON pharmacy (region_code);
CREATE INDEX idx_pharmacy_name_trgm ON pharmacy USING gin (name gin_trgm_ops);

CREATE INDEX idx_drug_display_name_trgm ON drug USING gin (display_name gin_trgm_ops);
CREATE INDEX idx_drug_category          ON drug (category);
CREATE INDEX idx_drug_otc               ON drug (otc_flag) WHERE otc_flag = true;

-- 통계 재계산 시 가장 많이 타는 경로
CREATE INDEX idx_report_pair_active
    ON price_report (pharmacy_id, drug_id, purchased_at DESC)
    WHERE status = 'ACTIVE' AND flagged = false;

CREATE INDEX idx_report_drug    ON price_report (drug_id, purchased_at DESC);
CREATE INDEX idx_report_user    ON price_report (user_id, created_at DESC);
CREATE INDEX idx_report_flagged ON price_report (flagged) WHERE flagged = true;

-- 같은 사용자가 같은 (약국, 약품)에 같은 날(KST) 중복 제보하는 것을 차단한다.
-- (created_at::date)로 쓰면 인덱스 생성이 실패한다: timestamptz -> date 캐스트는 세션
-- TimeZone에 의존해 STABLE이고, 인덱스 표현식은 IMMUTABLE만 허용한다.
-- AT TIME ZONE 'Asia/Seoul'로 고정하면 timestamp가 되어 IMMUTABLE 캐스트가 된다.
CREATE UNIQUE INDEX uq_report_user_pair_day
    ON price_report (
        user_id, pharmacy_id, drug_id,
        ((created_at AT TIME ZONE 'Asia/Seoul')::date)
    )
    WHERE user_id IS NOT NULL AND status = 'ACTIVE';

CREATE INDEX idx_stat_drug_price ON pharmacy_drug_price_stat (drug_id, rep_price);
CREATE INDEX idx_stat_pharmacy   ON pharmacy_drug_price_stat (pharmacy_id);

-- ---------------------------------------------------------------------
-- 제약
-- ---------------------------------------------------------------------

ALTER TABLE pharmacy_drug_price_stat
    ADD CONSTRAINT uq_stat_pair UNIQUE (pharmacy_id, drug_id);
