## Development environment setup

### Start Redis, MySQL, Prometheus, and Grafana

```bash
cd Redis
docker compose -f docker/docker-compose.yml up -d
```

### Service URLs

- Redis: `localhost:6379`
- MySQL: `localhost:3306`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
  - ID: `admin`
  - Password: `admin`

Grafana automatically provisions the `Redis vs MySQL Read Benchmark` dashboard under the `Benchmark` folder.

### Check Status

```bash
docker compose -f docker/docker-compose.yml ps

# Redis
docker exec -it jonghyun-redis-server redis-cli ping

# MySQL
docker exec -it jonghyun-redis-mysql mysql -uroot -proot -e "SELECT 1"
```

### Prometheus Targets

Open `http://localhost:9090/targets` and check these jobs:

- `redis`: Redis exporter metrics
- `mysql`: MySQL exporter metrics
- `spring-api`: Spring Boot actuator metrics from `host.docker.internal:8080`

The `spring-api` target is up only when the Spring Boot app is running and `/actuator/prometheus` is exposed.

### Stop Services

```bash
docker compose -f docker/docker-compose.yml down

# Stop and remove volumes.
docker compose -f docker/docker-compose.yml down -v
```

### Benchmark Observability

k6 still measures end-to-end HTTP latency. Grafana adds internal signals that explain why the latency changed:

- Redis command rate, memory usage, connected clients
- MySQL query rate, threads, InnoDB buffer pool logical reads vs disk reads
- Spring Hikari pool state and connection acquire time, when actuator metrics are available
