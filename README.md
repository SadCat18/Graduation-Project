# Graduation Project

A full-stack graduation project built with Spring Boot and Vue.

## Tech Stack
- Backend: Java, Spring Boot, Maven
- Frontend: Vue, Vue Router
- Database: MySQL

## Project Structure
- `src/`: Spring Boot backend source code
- `frontend/`: Vue frontend source code
- `database/`: SQL scripts
- `deploy/`: deployment-related files

## Local Development

### Backend
```bash
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run serve
```

## Build

### Backend
```bash
mvn -DskipTests package
```

### Frontend
```bash
cd frontend
npm run build
```

## Notes
- Configure database connection in backend config before first run.
- If needed, initialize schema/data from `database/` scripts.
