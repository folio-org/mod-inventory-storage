# 🐳 Docker Compose Setup for mod-inventory-storage
Local development environment for mod-inventory-storage using Docker Compose.
## 📋 Prerequisites
- Docker and Docker Compose V2+
- Java 21+ (for local development mode)
- Maven 3.8+ (for building the module)
## 🏗️ Architecture
Two compose files provide flexible development workflows:
- **`infra-docker-compose.yml`**: Infrastructure services only (PostgreSQL, Kafka, pgAdmin, Kafka UI)
- **`app-docker-compose.yml`**: Full stack including the module (uses `include` to incorporate infra services)
## ⚙️ Configuration
Configuration is managed via the `.env` file in this directory.
### Key Environment Variables
| Variable                 | Description                   | Default       |
|--------------------------|-------------------------------|---------------|
| `ENV`                    | FOLIO environment name        | `folio`       |
| `MODULE_REPLICAS`        | Number of module instances    | `1`           |
| `MODULE_PORT`            | Module host port              | `8081`        |
| `DEBUG_PORT`             | Remote debugging port         | `5005`        |
| `DB_HOST`                | PostgreSQL hostname           | `postgres`    |
| `DB_PORT`                | PostgreSQL port               | `5432`        |
| `DB_DATABASE`            | Database name                 | `folio`       |
| `DB_USERNAME`            | Database user                 | `folio_admin` |
| `DB_PASSWORD`            | Database password             | `folio_admin` |
| `KAFKA_HOST`             | Kafka hostname                | `kafka`       |
| `KAFKA_PORT`             | Kafka port (Docker internal)  | `9093`        |
| `KAFKA_UI_PORT`          | Kafka UI port                 | `8080`        |
| `KAFKA_TOPIC_PARTITIONS` | Default partitions for topics | `10`          |
| `REPLICATION_FACTOR`     | Kafka replication factor      | `1`           |
| `PGADMIN_PORT`           | pgAdmin port                  | `5050`        |
## 🚀 Services
### PostgreSQL
- **Purpose**: Primary database for module data
- **Version**: PostgreSQL 16 Alpine
- **Access**: `localhost:5432` (configurable via `DB_PORT`)
- **Credentials**: See `DB_USERNAME` and `DB_PASSWORD` in `.env`
- **Database**: See `DB_DATABASE` in `.env`
### pgAdmin
- **Purpose**: Database administration interface
- **Access**: http://localhost:5050 (configurable via `PGADMIN_PORT`)
- **Login**: Use `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`
- **Pre-configured**: The PostgreSQL server is automatically registered on startup via `pgadmin-servers.json`
### Apache Kafka
- **Purpose**: Message broker for event-driven architecture
- **Mode**: KRaft mode (no Zookeeper required)
- **Listeners**:
  - Docker internal: `kafka:9093`
  - Host: `localhost:29092`
### Kafka UI
- **Purpose**: Web interface for Kafka management
- **Access**: http://localhost:8080 (configurable via `KAFKA_UI_PORT`)
- **Features**: Topic browsing, message viewing/producing, consumer group monitoring
### Kafka Topic Initialization
- **Purpose**: Automatically creates required Kafka topics on startup
- **Script**: `kafka-init.sh`
- **Configuration**: Edit `KAFKA_TOPIC_PARTITIONS` in `.env` to adjust partition count
- **Topics Created**:
  - `{ENV}.Default.inventory.async-migration`
  - `{ENV}.Default.inventory.bound-with`
  - `{ENV}.Default.inventory.campus`
  - `{ENV}.Default.inventory.classification-type`
  - `{ENV}.Default.inventory.call-number-type`
  - `{ENV}.Default.inventory.holdings-record`
  - `{ENV}.Default.inventory.instance`
  - `{ENV}.Default.inventory.instance-contribution`
  - `{ENV}.Default.inventory.instance-date-type`
  - `{ENV}.Default.inventory.institution`
  - `{ENV}.Default.inventory.item`
  - `{ENV}.Default.inventory.library`
  - `{ENV}.Default.inventory.loan-type`
  - `{ENV}.Default.inventory.location`
  - `{ENV}.Default.inventory.reindex-records`
  - `{ENV}.Default.inventory.reindex.file-ready`
  - `{ENV}.Default.inventory.service-point`
  - `{ENV}.Default.inventory.subject-source`
  - `{ENV}.Default.inventory.subject-type`
  - `{ENV}.Default.inventory.material-type`
## 📖 Usage
> **Note**: Docker Compose commands assume you are in the `docker/` directory. Maven build commands must be run from the **project root** (`cd ..` from `docker/`) due to the Vert.x build plugin requiring `pom.xml` in the working directory.
### Starting the Environment
```bash
# Build the module first
cd .. && mvn clean package -DskipTests
# Start all services (infrastructure + module)
cd docker
docker compose -f app-docker-compose.yml up -d
```
```bash
# Start only infrastructure services (for local development)
docker compose -f infra-docker-compose.yml up -d
```
```bash
# Start with build (if module code changed)
docker compose -f app-docker-compose.yml up -d --build
```
```bash
# Start specific service
docker compose -f infra-docker-compose.yml up -d postgres
```
### Stopping the Environment
```bash
# Stop all services
docker compose -f app-docker-compose.yml down
```
```bash
# Stop infra services only
docker compose -f infra-docker-compose.yml down
```
```bash
# Stop and remove volumes (clean slate)
docker compose -f app-docker-compose.yml down -v
```
### Viewing Logs
```bash
# All services
docker compose -f app-docker-compose.yml logs
```
```bash
# Specific service
docker compose -f app-docker-compose.yml logs mod-inventory-storage
```
```bash
# Follow logs in real-time
docker compose -f app-docker-compose.yml logs -f mod-inventory-storage
```
```bash
# Last 100 lines
docker compose -f app-docker-compose.yml logs --tail=100 mod-inventory-storage
```
### Scaling the Module
The module is configured with resource limits and deployment policies for production-like scaling:
- **CPU Limits**: 1.0 CPU (max), 0.5 CPU (reserved)
- **Memory Limits**: 1024M (max), 512M (reserved)
- **Restart Policy**: Automatic restart on failure
- **Update Strategy**: Rolling updates with 1 instance at a time, 10s delay
```bash
# Scale to 3 instances
docker compose -f app-docker-compose.yml up -d --scale mod-inventory-storage=3
```
```bash
# Or modify MODULE_REPLICAS in .env and restart
echo "MODULE_REPLICAS=3" >> .env
docker compose -f app-docker-compose.yml up -d
```
### Cleanup and Reset
```bash
# Complete cleanup (stops containers, removes volumes)
docker compose -f app-docker-compose.yml down -v
```
```bash
# Remove all Docker resources
docker compose -f app-docker-compose.yml down -v
docker volume prune -f
docker network prune -f
```
## 🔧 Development Workflows
### Workflow 1: Full Docker Stack
Run everything in Docker, including the module.
```bash
# Build the module
cd .. && mvn clean package -DskipTests
# Start all services
docker compose -f app-docker-compose.yml up -d
# View logs
docker compose -f app-docker-compose.yml logs -f mod-inventory-storage
```
**Use Case**: Testing the full deployment, simulating production environment, scaling tests.
### Workflow 2: Infrastructure Only + IDE
Run infrastructure in Docker, develop the module in your IDE.
```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d
# Run module from IDE or command line (connects to localhost ports)
```
**Use Case**: Active development with hot reload, debugging in IDE, faster iteration cycles.
## 🛠️ Common Tasks
### Building the Module
```bash
# Clean build (skip tests)
cd .. && mvn clean package -DskipTests
```
```bash
# Build with tests
cd .. && mvn clean package
```
### Consortium Tenant Setup

After starting the module, register the required tenants by calling the `/_/tenant` API with `loadReference=true`.
Run the following commands from **inside the module container**:

```bash
docker compose -f app-docker-compose.yml exec mod-inventory-storage /bin/sh
```

**1. Register the `consortium` (central) tenant:**

```bash
wget --no-check-certificate \
  --header="X-Okapi-Tenant: consortium" \
  --header="Content-Type: application/json" \
  --header="X-Okapi-Url: http://localhost:9130" \
  --post-data='{"module_to":"mod-inventory-storage-30.0.0-SNAPSHOT","parameters":[{"key":"loadReference","value":"true"}]}' \
  http://localhost:8081/_/tenant
```

**2. Register the `member` tenant:**

```bash
wget --no-check-certificate \
  --header="X-Okapi-Tenant: member" \
  --header="Content-Type: application/json" \
  --header="X-Okapi-Url: http://localhost:9130" \
  --post-data='{"module_to":"mod-inventory-storage-30.0.0-SNAPSHOT","parameters":[{"key":"loadReference","value":"true"}]}' \
  http://localhost:8081/_/tenant
```

> **Note**: Adjust `module_to` version to match the currently running module version if needed.

### Accessing Services
```bash
# PostgreSQL CLI
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d folio
```
```bash
# View database tables
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d folio -c "\dt"
```
```bash
# Check PostgreSQL health
docker compose -f infra-docker-compose.yml exec postgres pg_isready -U folio_admin
```
```bash
# List Kafka topics
docker compose -f infra-docker-compose.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9093 --list
```
```bash
# Consume messages from a topic
docker compose -f infra-docker-compose.yml exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9093 --topic folio.Default.inventory.item --from-beginning
```
### Adding New Kafka Topics
Edit `kafka-init.sh` and add topics to the `TOPICS` array:
```bash
TOPICS=(
  "${ENV}.Default.inventory.instance"
  "${ENV}.Default.inventory.your-new-topic"  # Add your new topic here
)
```
After editing, restart the kafka-topic-init service:
```bash
docker compose -f infra-docker-compose.yml up -d kafka-topic-init
```
### Rebuilding the Module
```bash
# Rebuild and restart the module
cd .. && mvn clean package -DskipTests
docker compose -f app-docker-compose.yml up -d --build mod-inventory-storage
```
```bash
# Force rebuild without cache
docker compose -f app-docker-compose.yml build --no-cache mod-inventory-storage
docker compose -f app-docker-compose.yml up -d mod-inventory-storage
```
## 🐛 Troubleshooting
### Port Conflicts
If you encounter port conflicts, modify the ports in `.env`:
```bash
# Example: Change module port to 8082
echo "MODULE_PORT=8082" >> .env
docker compose -f app-docker-compose.yml up -d
```
### Container Health Issues
```bash
# Check container status
docker compose -f app-docker-compose.yml ps
# Check specific container logs
docker compose -f app-docker-compose.yml logs mod-inventory-storage
# Restart a specific service
docker compose -f app-docker-compose.yml restart mod-inventory-storage
```
### Database Connection Issues
```bash
# Verify PostgreSQL is running
docker compose -f infra-docker-compose.yml ps postgres
# Check PostgreSQL logs
docker compose -f infra-docker-compose.yml logs postgres
# Test database connection
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d folio -c "SELECT 1"
```
**`FATAL: database "folio" does not exist`** — PostgreSQL only creates the database defined in `POSTGRES_DB` on the very first startup with an empty data directory. If the `postgres-data` volume already existed from a previous run (with different settings), the init is skipped. Fix by recreating the volume:
```bash
docker compose -f infra-docker-compose.yml stop postgres pgadmin
docker compose -f infra-docker-compose.yml rm -f postgres pgadmin
docker volume rm folio-mod-inventory-storage_postgres-data
docker compose -f infra-docker-compose.yml up -d postgres pgadmin
```
### Kafka Issues
```bash
# Check Kafka logs
docker compose -f infra-docker-compose.yml logs kafka
# Verify Kafka is ready
docker compose -f infra-docker-compose.yml exec kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9093
```
### Clean Start
If you need to completely reset the environment:
```bash
# Stop and remove everything
docker compose -f app-docker-compose.yml down -v
# Remove any orphaned containers
docker container prune -f
# Remove unused networks
docker network prune -f
# Start fresh
cd .. && mvn clean package -DskipTests
docker compose -f app-docker-compose.yml up -d --build
```
## 📚 Additional Resources
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [mod-inventory-storage Documentation](../README.MD)
## 💡 Tips
- Keep infrastructure running between development sessions to save startup time
- Use **Workflow 1** (Full Docker Stack) when testing deployment or scaling scenarios
- Use **Workflow 2** (Infrastructure Only + IDE) for active development with faster feedback
- Use `docker compose -f infra-docker-compose.yml logs -f` to monitor all infrastructure services
- pgAdmin and Kafka UI provide helpful web interfaces for inspecting database and Kafka state
