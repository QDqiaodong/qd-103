COMPOSE_FILE := docker/docker-compose.yml

.PHONY: up down logs ps rebuild

up:
	docker compose -f $(COMPOSE_FILE) up -d --build --remove-orphans

down:
	docker compose -f $(COMPOSE_FILE) down

logs:
	docker compose -f $(COMPOSE_FILE) logs -f

ps:
	docker compose -f $(COMPOSE_FILE) ps

rebuild:
	docker compose -f $(COMPOSE_FILE) build --no-cache
