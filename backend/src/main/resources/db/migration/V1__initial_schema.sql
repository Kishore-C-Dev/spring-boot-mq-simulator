-- Queue configurations
CREATE TABLE queue_configs (
    id              VARCHAR(36) PRIMARY KEY,
    namespace       VARCHAR(255) NOT NULL,
    queue_name      VARCHAR(255) NOT NULL,
    concurrency     VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    updated_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE,
    deleted         BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_queue_configs_namespace_queue ON queue_configs (namespace, queue_name);
CREATE INDEX idx_queue_configs_queue_name ON queue_configs (queue_name);
CREATE INDEX idx_queue_configs_active ON queue_configs (namespace, enabled) WHERE deleted = false;

-- Response mappings
CREATE TABLE response_mappings (
    id              VARCHAR(36) PRIMARY KEY,
    namespace       VARCHAR(255) NOT NULL,
    queue_name      VARCHAR(255) NOT NULL,
    match           JSONB        NOT NULL,
    response        JSONB        NOT NULL,
    delay           JSONB        NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    priority        INTEGER,
    updated_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE,
    deleted         BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_response_mappings_ns_queue_priority ON response_mappings (namespace, queue_name, priority);
CREATE INDEX idx_response_mappings_active ON response_mappings (namespace, queue_name, enabled) WHERE deleted = false;
CREATE INDEX idx_response_mappings_match_gin ON response_mappings USING GIN (match);
CREATE INDEX idx_response_mappings_response_gin ON response_mappings USING GIN (response);

-- User profiles
CREATE TABLE user_profiles (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(255) NOT NULL UNIQUE,
    email               VARCHAR(255) UNIQUE,
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    password_hash       VARCHAR(255),
    namespaces          JSONB DEFAULT '[]'::jsonb,
    default_namespace   VARCHAR(255),
    role                VARCHAR(50),
    created_at          TIMESTAMP WITH TIME ZONE,
    last_login          TIMESTAMP WITH TIME ZONE,
    active              BOOLEAN NOT NULL DEFAULT true,
    deleted             BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);
CREATE INDEX idx_user_profiles_email ON user_profiles (email);
CREATE INDEX idx_user_profiles_namespaces_gin ON user_profiles USING GIN (namespaces);
CREATE INDEX idx_user_profiles_active ON user_profiles (active) WHERE deleted = false;

-- Namespaces
CREATE TABLE namespaces (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    description     TEXT,
    members         JSONB DEFAULT '[]'::jsonb,
    owner           VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE,
    active          BOOLEAN NOT NULL DEFAULT true,
    deleted         BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_namespaces_name ON namespaces (name);
CREATE INDEX idx_namespaces_owner ON namespaces (owner);
CREATE INDEX idx_namespaces_members_gin ON namespaces USING GIN (members);
CREATE INDEX idx_namespaces_active ON namespaces (active) WHERE deleted = false;
