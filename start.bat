@echo off
rem Windows launcher. Uses the built JAR, building it first with Maven (Java 21) if needed.
cd /d "%~dp0"
if not exist target\metropolitan.jar (
  where mvn >nul 2>nul || (echo Maven not found. Install Java 21 + Maven, or use: docker compose up --build & exit /b 1)
  call mvn -q -DskipTests package || exit /b 1
)
echo Starting Metropolitan on http://localhost:8080  (local H2 database in .\data)
java -jar target\metropolitan.jar
