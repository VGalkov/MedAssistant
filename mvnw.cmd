@REM Maven wrapper script for Windows
@REM This script downloads and runs Maven with a custom repository path

@echo off
setlocal

REM Устанавливаем путь к репозиторию без русских символов
set MAVEN_REPO_LOCAL=%~dp0.m2\repository

REM Создаём директорию если не существует
if not exist "%MAVEN_REPO_LOCAL%" mkdir "%MAVEN_REPO_LOCAL%"

REM Запускаем Maven с кастомным репозиторием
echo Using local Maven repository: %MAVEN_REPO_LOCAL%

REM Проверяем есть ли Maven в PATH
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Maven not found in PATH. Please install Maven or use MAVEN_HOME.
    exit /b 1
)

mvn -Dmaven.repo.local="%MAVEN_REPO_LOCAL%" %*
