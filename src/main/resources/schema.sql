CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE agent_metadata (
  agent_id uuid NOT NULL DEFAULT uuid_generate_v1() PRIMARY KEY,
  server_name varchar(100) NOT NULL,
  server_type varchar(100) NOT NULL,
  operating_system varchar(100) NOT NULL,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL
)