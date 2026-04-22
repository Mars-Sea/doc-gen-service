---
name: build-docker
description: Build the project and start it with Docker Compose. Use for local integration testing or to verify the Docker setup.
---

Build the JAR and start the service in Docker:

```bash
mvn clean package -DskipTests && docker-compose up -d --build
```

After it starts, the API is available at `http://localhost:8081` and Swagger UI at `http://localhost:8081/swagger-ui.html`.

To view logs:

```bash
docker-compose logs -f
```

To stop:

```bash
docker-compose down
```
