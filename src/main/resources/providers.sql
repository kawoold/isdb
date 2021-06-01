BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TYPE service_type AS enum ('cable', 'dsl', 'satellite', 'other');

CREATE TABLE providers(
    id uuid DEFAULT uuid_generate_v4(),
    name VARCHAR NOT NULL,
    url VARCHAR NOT NULL,
    service_type service_type
);

COMMIT;