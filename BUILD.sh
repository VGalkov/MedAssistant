#!/bin/bash
# ============================================
# Скрипт сборки и запуска МедАссистент (Linux/Mac)
# ============================================

set -e

# Путь к репозиторию Maven (без русских символов)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
MAVEN_REPO_LOCAL="$SCRIPT_DIR/.m2/repository"

# Создаём директорию репозитория
mkdir -p "$MAVEN_REPO_LOCAL"

echo "============================================"
echo "МедАссистент - Сборка и запуск"
echo "============================================"
echo
echo "Maven repository: $MAVEN_REPO_LOCAL"
echo

# Проверяем наличие Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java не найдена! Установите Java 17+."
    echo "Download: https://adoptium.net/"
    exit 1
fi

echo "[OK] Java найдена"
java -version
echo

# Проверяем наличие Maven
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven не найден!"
    echo "Установите Maven: https://maven.apache.org/download.cgi"
    exit 1
fi

echo "[OK] Maven найден"
mvn --version
echo

# Сборка проекта
echo "============================================"
echo "Шаг 1: Очистка и компиляция"
echo "============================================"
mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -s settings.xml clean compile

echo
echo "[OK] Компиляция успешна"
echo

# Запуск приложения
echo "============================================"
echo "Шаг 2: Запуск приложения"
echo "============================================"
echo
echo "Приложение будет доступно:"
echo "  - Пациент: http://localhost:8080/patient/start"
echo "  - Врач: http://localhost:8080/doctor"
echo
echo "Для остановки нажмите Ctrl+C"
echo

mvn -Dmaven.repo.local="$MAVEN_REPO_LOCAL" -s settings.xml spring-boot:run
