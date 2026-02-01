# Graph API v1

Backend для интерактивного исследования графа (1-hop, shortest-path, resolve) на DuckDB.

## Быстрый старт

### Требования
- JDK 17+ (в проекте указана цель `java.version=17`)
- Maven Wrapper (в репозитории есть `./mvnw`)

### Запуск
```
./mvnw spring-boot:run
```

По умолчанию приложение слушает `http://localhost:8080`.

### OpenAPI / Swagger
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- JSON схемы: `http://localhost:8080/v3/api-docs`

## Конфигурация

Файл БД по умолчанию: `data/graph.duckdb` (см. `src/main/resources/application.yml`).

> Примечание: при старте приложение пытается загрузить расширение `duckpgq`.
> Если его нет, в логах будет предупреждение, но приложение продолжит работу.

## Основные эндпоинты

### 1) Resolve идентификаторов
`POST /api/v1/graph/resolve`

Запрос:
```
{
  "ids": ["123", "456"],
  "phoneNos": ["+79995556677"],
  "partyRks": ["7700000000"]
}
```

Поддерживаются алиасы в JSON: `phone_no`, `party_rk`. Если ничего не найдено — вернётся пустой массив `nodes`.

Ответ:
```
{
  "nodes": [
    {
      "id": "person:123",
      "kind": "person",
      "label": "Alice",
      "attrs": { "age": 30 },
      "flags": ["vip"]
    }
  ]
}
```

### 2) 1-hop расширение
`POST /api/v1/graph/one-hop`

Запрос (по стабильным id):
```
{
  "seeds": ["person:123", "company:7700000000"],
  "cursor": "",
  "limit": 50,
  "edgeKinds": ["transfer", "contact"]
}
```

Запрос (по id/phone/party_rk):
```
{
  "ids": ["123"],
  "phone_no": ["+79995556677"],
  "party_rk": ["7700000000"],
  "limit": 50
}
```

Правила:
- Нужно передать хотя бы одно из `seeds/ids/phoneNos/partyRks`.
- `limit` обязателен (1..200).
- Граф ориентированный: связи возвращаются как `src -> dst`.
- `edgeKinds` необязателен (если не задан — все типы).
- `cursor` используется для пагинации (по `dst`). Для стабильной пагинации рекомендуем
  отправлять по одному seed за запрос.

Ответ:
```
{
  "nodes": [ ... ],
  "edges": [ ... ],
  "pages": [
    { "seed": "person:123", "endCursor": "person:456", "hasNext": false }
  ]
}
```

### 3) Кратчайший путь (Roadmap)
`POST /api/v1/graph/shortest-path`

Запрос:
```
{
  "from": "person:123",
  "to": "phone:+79995556677",
  "edgeKinds": ["transfer", "contact"],
  "maxHops": 6
}
```
`maxHops` необязателен: по умолчанию 10, максимум 20.

Ответ:
```
{
  "nodes": [ ... ],
  "edges": [ ... ],
  "length": 2
}
```

### 4) Health check
`GET /health` → `OK`

## Формат узлов и ребер

Node (`NodeDto`):
- `id`: стабильный id (`person:123`, `phone:+7999`)
- `kind`: тип сущности
- `label`: короткая подпись
- `attrs`: произвольные поля для hover
- `flags`: статусы (`vip`, `blacklist`, ...)

Edge (`EdgeDto`):
- `id`: стабильный id ребра
- `src`, `dst`: направление
- `kind`: тип связи (`transfer`, `tk`, ...)
- `attrs`, `flags`: произвольные поля/статусы

## Ошибки валидации

При неверном запросе возвращается:
```
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "fields": [
    { "field": "limit", "message": "must be greater than or equal to 1" }
  ]
}
```

## Тесты

```
./mvnw test
```

В тестах используется отдельная DuckDB БД: `./target/graph_test.duckdb`.
