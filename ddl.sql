-- reset if running again
DROP PROCEDURE create_client_location IF EXISTS;
DROP TABLE client_location IF EXISTS;

-- tables

-- holds records that are inserted by the benchmark 
CREATE TABLE client_location
(
  record_id   bigint          NOT NULL,
  client_id   bigint          NOT NULL,
  insert_ts   timestamp       NOT NULL,
  latitude    decimal         NOT NULL,
  longitude   decimal         NOT NULL,
  notes       varchar(63)     DEFAULT NULL,
  CONSTRAINT  pk_client_location PRIMARY KEY (record_id, client_id)
);
PARTITION TABLE client_location ON COLUMN record_id;

-- stored procedures

-- inserts a client location record
CREATE PROCEDURE create_client_location AS
insert into client_location values (?, ?, NOW, ?, ?, ?);

PARTITION PROCEDURE create_client_location ON TABLE client_location COLUMN record_id;

