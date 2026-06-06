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
docker compose --env-file .env -f docker-compose.yml up -d --build --remove-orphans
```

The startup wrapper clears stale containers from the same Compose project before recreating services, which prevents name conflicts like `qd103-mysql-1 already in use`.

## Cache behavior

- The first build downloads dependencies and base images.
- Later `docker compose up --build -d` reuses Docker layer cache when `pom.xml`, `settings.xml`, `package.json`, `package-lock.json`, and `.npmrc` do not change.
- If only application source files change, Docker only reruns the compile/package layers:
  - frontend: `COPY . .` -> `npm run build`
  - backend: `COPY src ./src` -> `mvn package`

## Access

- Frontend: http://localhost:18183
- Frontend dev server: http://localhost:15173
- Backend API: http://localhost:18184/api/questionnaires
- MySQL: localhost:13316
- Redis: localhost:16379

## Stop

```sh
sh stop.sh
```
