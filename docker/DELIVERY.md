# Docker Delivery

## Build and run

From the repository root:

```sh
sh start.sh
```

Or:

```sh
make up
```

Or:

```sh
docker compose -f docker/docker-compose.yml up -d --build --remove-orphans
```

## Access

- Frontend: http://localhost
- Backend API: http://localhost:8080/api/questionnaires
- Redis: localhost:6379

## Stop

```sh
sh stop.sh
```
