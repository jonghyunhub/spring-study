### Development environment setup

## Docker Redis Setup
Redis server configuration for local development.

### Start Redis
```bash
cd docker-Redis
docker-compose up -d
```

### Check Redis Status
```bash
# Check container status
docker ps

# Connect to Redis CLI
docker exec -it redis-server redis-cli

# Test connection
docker exec -it redis-server redis-cli ping
# Expected output: PONG
```

### Stop Redis
```bash
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Redis Configuration
- **Port**: 6379
- **Image**: redis:7-alpine
- **Persistence**: AOF (Append Only File) enabled
- **Data Volume**: redis-data (persistent storage)
- **Network**: redis-network (bridge)

