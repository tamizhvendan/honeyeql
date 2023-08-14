#!/usr/bin/env bash
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P );

docker run \
	--name sakila_pg_test \
	--rm \
	--detach \
	-p 5433:5432 \
	--env POSTGRES_USER=postgres \
	--env POSTGRES_PASSWORD=postgres \
	--env POSTGRES_DB=sakila \
	-v "$parent_path/postgres/sakila.sql:/docker-entrypoint-initdb.d/sakila.sql" \
	postgres:11-alpine

docker run \
	--name honeyeql_pg_test \
	--rm \
	--detach \
	-p 5434:5432 \
	--env POSTGRES_USER=postgres \
	--env POSTGRES_PASSWORD=postgres \
	--env POSTGRES_DB=honeyeql \
	-v "$parent_path/postgres/honeyeql.sql:/docker-entrypoint-initdb.d/honeyeql.sql" \
	postgres:11-alpine

docker run \
	--name sakila_mysql_test \
	--rm \
	--detach \
	-p 3307:3306 \
	--env MYSQL_ROOT_PASSWORD=mysql123 \
	--env MYSQL_DATABASE=sakila \
	-v "$parent_path/mysql/sakila.sql:/docker-entrypoint-initdb.d/sakila.sql" \
	mysql:8.0

docker run \
	--name honeyeql_mysql_test \
	--rm \
	--detach \
	-p 3308:3306 \
	--env MYSQL_ROOT_PASSWORD=mysql123 \
	--env MYSQL_DATABASE=honeyeql \
	-v "$parent_path/mysql/honeyeql.sql:/docker-entrypoint-initdb.d/honeyeql.sql" \
	mysql:8.0