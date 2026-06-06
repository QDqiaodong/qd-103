COMPOSE_FILE := docker-compose.yml
ENV_FILE := .env

.PHONY: up down logs ps rebuild

up:
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) down --remove-orphans || true
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) up -d --build --remove-orphans

down:
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) down --remove-orphans

logs:
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) logs -f

ps:
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) ps

rebuild:
	docker compose --env-file $(ENV_FILE) -f $(COMPOSE_FILE) build --no-cache
