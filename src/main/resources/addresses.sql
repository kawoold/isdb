CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE addresses(
    id uuid DEFAULT uuid_generate_v4(),
    line_1 VARCHAR NOT NULL,
    line_2 VARCHAR,
    state_code VARCHAR NOT NULL,
    zip_code VARCHAR NOT NULL,
    geo_results JSON
);