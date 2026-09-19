# ScholarMind development commands
SERVER_URL ?= http://localhost:9900
DOCKER_COMPOSE_FILE ?= vector-database.yml
FILE ?=

.PHONY: help init up down status start check upload test

help:
	@echo "ScholarMind: make up | down | status | start | check | test"
	@echo "make init: start infrastructure, then run the application in foreground"
	@echo "make upload FILE=/path/to/paper.pdf: upload one paper"

init: up
	$(MAKE) start

up:
	docker compose -f $(DOCKER_COMPOSE_FILE) up -d

down:
	docker compose -f $(DOCKER_COMPOSE_FILE) down

status:
	docker compose -f $(DOCKER_COMPOSE_FILE) ps

start:
	mvn spring-boot:run

check:
	curl --fail --show-error "$(SERVER_URL)/milvus/health"

upload:
	$(if $(strip $(FILE)),,$(error Set FILE to the paper to upload))
	curl --fail --show-error -X POST "$(SERVER_URL)/api/upload" -F "file=@$(FILE)"

test:
	mvn test
