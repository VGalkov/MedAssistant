# 🏥 MedAssistant

**Система сбора анамнеза пациентов с ИИ-генерацией уточняющих вопросов**

Spring Boot приложение для автоматизации первичного опроса пациентов перед приёмом врача.

---

## 📋 Оглавление

- [Описание](#описание)
- [Возможности](#возможности)
- [Требования](#требования)
- [Быстрый старт](#быстрый-старт)
- [Установка](#установка)
- [Конфигурация](#конфигурация)
- [Запуск](#запуск)
- [API](#api)
- [Структура проекта](#структура-проекта)
- [База данных](#база-данных)
- [Логирование](#логирование)
- [Промпты ИИ](#промпты-ии)
- [Сборка](#сборка)
- [Устранение проблем](#устранение-проблем)
- [Лицензия](#лицензия)

---

## 📖 Описание

**MedAssistant** — это веб-приложение для медицинских клиник, которое:

1. **Регистрирует пациента** перед приёмом
2. **Задаёт вопросы** от имени врача (настраиваемый список)
3. **Генерирует 3 уточняющих вопроса** через ИИ (LM Studio / Ollama / любой OpenAI-совместимый API)
4. **Анализирует ответы** и формирует структурированный отчёт
5. **Предоставляет врачу** готовый анамнез с рекомендациями

---

## ✨ Возможности

| Для пациента | Для врача | Для администратора |
|--------------|-----------|-------------------|
| 📝 Быстрый опрос перед приёмом | 👨‍⚕️ Панель со всеми опросами | ⚙️ Редактирование вопросов |
| 🤖 ИИ-вопросы для уточнения симптомов | 📄 Детальный просмотр ответов | 🔄 Редактор ИИ-промптов |
| 🔒 Конфиденциальность данных | 💡 ИИ-рекомендации | 📊 Управление сценариями |
| 📱 Адаптивный интерфейс | 🚩 Подозрения на недостоверность | 💾 Внешние конфигурации |

---

## 🛠 Требования

| Компонент | Версия | Примечание |
|-----------|--------|------------|
| **Java** | 17+ | LTS версия (рекомендуется 21) |
| **Maven** | 3.8+ | Для сборки |
| **LM Studio** | 0.2.0+ | Или любой OpenAI-совместимый API |
| **Браузер** | Любой современный | Chrome, Firefox, Safari, Edge |

---

## 🚀 Быстрый старт

### 1. Скачать дистрибутив

```bash
# Или собрать самостоятельно
git clone <repository-url>
cd medassistant
./BUILD.sh
```

### 2. Настроить LM Studio

1. Запустите **LM Studio**
2. Загрузите модель (например, `medgemma-4b-it` ,`gemma-2-2b-it` или `llama-3-8b`)
3. Включите **Local Server** на порту `1234`

### 3. Запустить приложение

```bash
cd dist
./run.sh start        # Linux
run.bat start         # Windows
```

### 4. Открыть в браузере

```
http://localhost:8080/patient/start    # Регистрация пациента
http://localhost:8080/doctor           # Панель врача
```

---

## 📦 Установка

### Вариант 1: Готовый дистрибутив

```bash
# Распакуйте архив
unzip medassistant-dist.zip
cd medassistant-dist

# Запустите
./run.sh start        # Linux
run.bat start         # Windows
```

### Вариант 2: Сборка из исходников

```bash
# Клонируйте репозиторий
git clone <repository-url>
cd medassistant

# Настройте Maven (если путь пользователя содержит кириллицу)
# Создайте settings.xml в корне проекта

# Соберите
mvn clean package -DskipTests

# Или используйте скрипт
./BUILD.sh

# Дистрибутив будет в: dist/
```

### Вариант 3: Запуск из IntelliJ IDEA

1. Откройте проект в **IntelliJ IDEA**
2. Настройте **Maven → settings.xml** (если нужно)
3. Запустите **MedAssistantApplication.java**
4. Откройте http://localhost:8080

---

## ⚙️ Конфигурация

### Файлы конфигурации

Все конфигурационные файлы находятся **рядом с JAR** и имеют приоритет над встроенными:

| Файл | Описание |
|------|----------|
| `application.yml` | Основные настройки приложения |
| `logback-spring.xml` | Настройки логирования |
| `prompts/*.txt` | Промпты для ИИ |

### Основные настройки (`application.yml`)

```yaml
# Порт и адрес сервера
server:
  port: 8080
  address: 0.0.0.0

# База данных
spring:
  datasource:
    url: jdbc:h2:file:./data/medassistant_db
    username: sa
    password:

# LM Studio
lmstudio:
  base-url: http://localhost:1234
  model: local-model

# Логирование
logging:
  level:
    ru.medassistant: INFO
  file:
    name: ./logs/medassistant.log
```

### Переменные окружения

Можно переопределять настройки через переменные:

```bash
# Linux
export SERVER_PORT=9090
export LMSTUDIO_BASE_URL=http://192.168.1.100:1234
export SPRING_PROFILES_ACTIVE=prod

# Windows
set SERVER_PORT=9090
set LMSTUDIO_BASE_URL=http://192.168.1.100:1234
```

---

## 🏃 Запуск

### Linux

```bash
cd dist

# Запуск
./run.sh start

# Статус
./run.sh status

# Просмотр логов
./run.sh logs

# Остановка
./run.sh stop

# Перезапуск
./run.sh restart
```

### Windows

```cmd
cd dist

# Запуск (двойной клик или консоль)
run.bat start

# Статус
run.bat status

# Логи
run.bat logs

# Остановка
run.bat stop
```

---

## 🔌 API

### Endpoints

| Метод | URL | Описание |
|-------|-----|----------|
| `GET` | `/patient/start` | Регистрация пациента |
| `POST` | `/patient/register` | Сохранение пациента |
| `GET` | `/patient/survey/{id}` | Опрос пациента |
| `POST` | `/patient/survey/{id}/answer` | Сохранение ответа |
| `POST` | `/patient/survey/{id}/complete` | Завершение опроса |
| `GET` | `/doctor` | Панель врача |
| `GET` | `/doctor/survey/{id}` | Детали опроса |
| `GET` | `/doctor/scenario/edit` | Редактор вопросов |
| `GET` | `/doctor/prompts/edit` | Редактор промптов |
| `POST` | `/doctor/prompts/save` | Сохранение промптов |
| `GET` | `/h2-console` | Консоль базы данных |

### Пример запроса

```bash
# Сохранение ответа
curl -X POST http://localhost:8080/patient/survey/1/answer \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "questionId=-1&answerText=Головная боль&questionText=Что вас беспокоит?"
```

---

## 📁 Структура проекта

```
medassistant/
├── src/
│   └── main/
│       ├── java/ru/medassistant/
│       │   ├── MedAssistantApplication.java
│       │   ├── controller/
│       │   ├── model/
│       │   ├── repository/
│       │   └── service/
│       └── resources/
│           ├── application.yml
│           ├── templates/
│           └── prompts/
├── dist/                      ← Готовый дистрибутив
│   ├── medassistant.jar
│   ├── application.yml
│   ├── logback-spring.xml
│   ├── run.sh
│   ├── run.bat
│   ├── prompts/
│   ├── data/
│   └── logs/
├── settings.xml               ← Настройки Maven
├── pom.xml
├── BUILD.sh
├── BUILD.bat
└── README.md
```

---

## 🗄 База данных

### H2 Database

Приложение использует **встроенную базу H2** с сохранением в файл.

| Параметр | Значение |
|----------|----------|
| **URL** | `jdbc:h2:file:./data/medassistant_db` |
| **Консоль** | http://localhost:8080/h2-console |
| **Username** | `sa` |
| **Password** | (пусто) |

### Таблицы

| Таблица | Описание |
|---------|----------|
| `patients` | Пациенты |
| `surveys` | Опросы |
| `survey_answers` | Ответы на вопросы |
| `scenarios` | Сценарии опросов |
| `scenario_questions` | Вопросы сценария |
| `doctors` | Врачи (резерв) |

### Бэкап базы данных

```bash
# Копирование файла БД
cp data/medassistant_db.mv.db data/medassistant_db.backup.mv.db

# Восстановление
cp data/medassistant_db.backup.mv.db data/medassistant_db.mv.db
```

---

## 📝 Логирование

### Файлы логов

| Файл | Описание |
|------|----------|
| `logs/medassistant.log` | Все логи приложения |
| `logs/medassistant.error.log` | Только ошибки |
| `logs/startup.log` | Логи запуска (Linux) |

### Уровни логирования

```yaml
logging:
  level:
    root: INFO
    ru.medassistant: DEBUG    # Отладка приложения
    org.springframework: WARN  # Spring
    org.hibernate: WARN        # Hibernate
```

### Просмотр логов

```bash
# Linux
./run.sh logs
tail -f logs/medassistant.log

# Windows
run.bat logs
type logs\medassistant.log
```

---

## 🤖 Промпты ИИ

### Расположение

```
dist/prompts/
├── process-survey.txt      # Структурирование ответов
├── recommendations.txt     # Рекомендации врачу
├── suspicion.txt           # Подозрения на недостоверность
└── ai-questions.txt        # Генерация ИИ-вопросов
```

### Редактирование

1. **Через веб-интерфейс:**
   - Откройте http://localhost:8080/doctor/prompts/edit
   - Отредактируйте промпт
   - Нажмите "💾 Сохранить"

2. **Через файл:**
   - Откройте `prompts/*.txt`
   - Отредактируйте текст
   - Перезапустите приложение

### Переменные в промптах

| Промпт | Переменные |
|--------|------------|
| `process-survey.txt` | `%s` — текст опроса |
| `recommendations.txt` | `%s` (1) — оригинал, `%s` (2) — анализ, `%s` (3) — история |
| `suspicion.txt` | `%s` (1) — текст пациента, `%s` (2) — история |
| `ai-questions.txt` | `%s` — ответы пациента |

---

## 🔨 Сборка

### Требования для сборки

- Java 17+
- Maven 3.8+
- Git (опционально)

### Команды сборки

```bash
# Очистка и сборка
mvn clean package

# Пропуск тестов
mvn clean package -DskipTests

# С внешним settings.xml
mvn clean package -s settings.xml

# С указанием репозитория
mvn clean package -Dmaven.repo.local=C:\maven-repo

# Полная сборка дистрибутива
./BUILD.sh        # Linux
BUILD.bat         # Windows
```

### Артефакты

После сборки:

```
target/medassistant-0.0.1-SNAPSHOT.jar
dist/                        ← Готовый дистрибутив
```

---

## 🔧 Устранение проблем

### Ошибка: "Could not create local repository"

**Проблема:** Путь Maven содержит кириллицу.

**Решение:**
```xml
<!-- settings.xml -->
<localRepository>C:\maven-repo\medassistant</localRepository>
```

### Ошибка: "Read timed out"

**Проблема:** Нет доступа к Maven Central.

**Решение:** Добавьте зеркала в `settings.xml`:
```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/central</url>
</mirror>
```

### Ошибка: "Prompt not found"

**Проблема:** Отсутствуют файлы промптов.

**Решение:**
```bash
mkdir prompts
# Скопируйте промпты из src/main/resources/prompts/
```

### Ошибка: "Port 8080 already in use"

**Проблема:** Порт занят.

**Решение:**
```yaml
# application.yml
server:
  port: 8081  # Измените порт
```

### Ошибка: "Java not found"

**Проблема:** Java не установлена или не в PATH.

**Решение:**
```bash
# Проверка
java -version

# Установка (Ubuntu)
sudo apt install openjdk-21-jdk

# Установка (Windows)
# Скачайте с https://adoptium.net/
```

---

## 📊 Мониторинг

### Проверка статуса

```bash
# Linux
./run.sh status

# Windows
run.bat status

# Через curl
curl http://localhost:8080/actuator/health
```

### Метрики

- Количество опросов: `/doctor`
- Размер базы: `data/medassistant_db.mv.db`
- Размер логов: `logs/`

---

## 🔒 Безопасность

### Рекомендации

1. **Не открывайте порт 8080** в интернет без аутентификации
2. **Используйте HTTPS** через reverse proxy (nginx)
3. **Регулярно делайте бэкапы** базы данных
4. **Ограничьте доступ** к `/h2-console`

### Пример nginx конфигурации

```nginx
server {
    listen 443 ssl;
    server_name medassistant.example.com;
    
    ssl_certificate /etc/ssl/certs/medassistant.crt;
    ssl_certificate_key /etc/ssl/private/medassistant.key;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location /h2-console {
        deny all;
    }
}
```

---

## 📄 Лицензия

© 2026 s0506777@yandex.ru

Все права защищены.

---

## 📞 Поддержка

| Вопрос | Контакт                               |
|--------|---------------------------------------|
| Технические проблемы | s0506777@yandex.ru                        |
| Предложения | s0506777@yandex.ru                    |
| Документация | s0506777@yandex.ru       

---

**Версия:** 1.0.0  
**Дата обновления:** 2026-09-07
