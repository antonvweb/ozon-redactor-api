# API Документация - Система папок для товаров

## Обзор

Система папок позволяет организовать товары в иерархическую структуру с неограниченной вложенностью.

---

## 1. Создать папку

**Endpoint:** `POST /api/folders`

**Headers:**
```
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Зимняя коллекция",
  "parentFolderId": null,
  "color": "#3B82F6",
  "icon": "folder"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "userId": 10,
  "parentFolderId": null,
  "name": "Зимняя коллекция",
  "color": "#3B82F6",
  "icon": "folder",
  "position": 0,
  "productsCount": 0,
  "subfoldersCount": 0,
  "createdAt": "2024-12-05T10:00:00",
  "updatedAt": "2024-12-05T10:00:00"
}
```

---

## 2. Обновить папку

**Endpoint:** `PUT /api/folders/{folderId}`

**Request Body:**
```json
{
  "name": "Зимняя коллекция 2024",
  "parentFolderId": 5,
  "color": "#EF4444",
  "position": 1
}
```

**Response (200 OK):** Аналогично создан

ию

---

## 3. Удалить папку

**Endpoint:** `DELETE /api/folders/{folderId}?moveProductsToParent=false`

**Query Parameters:**
- `moveProductsToParent` (optional, default: false)
  - `true` - переместить товары и подпапки в родительскую папку
  - `false` - убрать товары из папок (товары останутся, но без папки)

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Папка успешно удалена"
}
```

---

## 4. Получить дерево папок

**Endpoint:** `GET /api/folders/tree`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Одежда",
    "color": "#3B82F6",
    "icon": "folder",
    "position": 0,
    "productsCount": 5,
    "subfolders": [
      {
        "id": 2,
        "name": "Футболки",
        "color": "#10B981",
        "icon": "folder",
        "position": 0,
        "productsCount": 15,
        "subfolders": [
          {
            "id": 5,
            "name": "Мужские",
            "color": "#6366F1",
            "icon": "folder",
            "position": 0,
            "productsCount": 8,
            "subfolders": []
          }
        ]
      },
      {
        "id": 3,
        "name": "Джинсы",
        "color": "#F59E0B",
        "icon": "folder",
        "position": 1,
        "productsCount": 12,
        "subfolders": []
      }
    ]
  }
]
```

---

## 5. Получить папки определенного уровня

**Endpoint:** `GET /api/folders?parentFolderId={id}`

**Query Parameters:**
- `parentFolderId` (optional) - ID родительской папки
  - Если не указан - вернет корневые папки
  - Если указан - вернет подпапки этой папки

**Response (200 OK):**
```json
[
  {
    "id": 2,
    "userId": 10,
    "parentFolderId": 1,
    "name": "Футболки",
    "color": "#10B981",
    "icon": "folder",
    "position": 0,
    "productsCount": 15,
    "subfoldersCount": 2,
    "createdAt": "2024-12-05T10:00:00",
    "updatedAt": "2024-12-05T10:00:00"
  }
]
```

---

## 6. Получить информацию о папке

**Endpoint:** `GET /api/folders/{folderId}`

**Response (200 OK):**
```json
{
  "id": 2,
  "userId": 10,
  "parentFolderId": 1,
  "name": "Футболки",
  "color": "#10B981",
  "icon": "folder",
  "position": 0,
  "productsCount": 15,
  "subfoldersCount": 2,
  "createdAt": "2024-12-05T10:00:00",
  "updatedAt": "2024-12-05T10:00:00"
}
```

---

## 7. Переместить товары в папку

**Endpoint:** `POST /api/folders/move-products`

**Request Body:**
```json
{
  "productIds": [123, 124, 125],
  "targetFolderId": 5
}
```

**Для удаления товаров из папки:**
```json
{
  "productIds": [123, 124],
  "targetFolderId": null
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Товары успешно перемещены"
}
```

---

## 8. Получить путь к папке (breadcrumb)

**Endpoint:** `GET /api/folders/{folderId}/path`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Одежда",
    "level": 0
  },
  {
    "id": 2,
    "name": "Футболки",
    "level": 1
  },
  {
    "id": 5,
    "name": "Мужские",
    "level": 2
  }
]
```

---

## 9. Получить товары из папки

**Endpoint:** `GET /api/v1/ozon/products/folder/{folderId}?userId={userId}&page=0&size=20`

**Query Parameters:**
- `userId` (required) - ID пользователя
- `page` (optional, default: 0) - Номер страницы
- `size` (optional, default: 20) - Размер страницы

**Response (200 OK):**
```json
{
  "products": [
    {
      "id": 1,
      "userId": 10,
      "productId": 456789,
      "name": "Футболка мужская",
      "folderId": 5,
      "price": 1500.00,
      ...
    }
  ],
  "currentPage": 0,
  "totalPages": 3,
  "totalElements": 45
}
```

---

## 10. Получить товары без папки

**Endpoint:** `GET /api/v1/ozon/products/no-folder?userId={userId}&page=0&size=20`

**Response:** Аналогично #9

---

## Примеры использования

### Пример 1: Создание структуры папок

```bash
# 1. Создаем корневую папку "Одежда"
curl -X POST http://localhost:8080/api/folders \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Одежда",
    "color": "#3B82F6"
  }'
# Получаем id: 1

# 2. Создаем подпапку "Футболки" внутри "Одежда"
curl -X POST http://localhost:8080/api/folders \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Футболки",
    "parentFolderId": 1,
    "color": "#10B981"
  }'
# Получаем id: 2

# 3. Создаем подпапку "Мужские" внутри "Футболки"
curl -X POST http://localhost:8080/api/folders \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Мужские",
    "parentFolderId": 2,
    "color": "#6366F1"
  }'
# Получаем id: 5
```

### Пример 2: Перемещение товаров в папки

```bash
# Переместить товары в папку "Мужские футболки"
curl -X POST http://localhost:8080/api/folders/move-products \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productIds": [456789, 456790, 456791],
    "targetFolderId": 5
  }'
```

### Пример 3: Просмотр дерева папок

```bash
curl -X GET http://localhost:8080/api/folders/tree \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Пример 4: Получение товаров из папки

```bash
curl -X GET "http://localhost:8080/api/v1/ozon/products/folder/5?userId=10&page=0&size=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Пример 5: Перемещение папки

```bash
# Переместить папку "Мужские" из "Футболки" в "Джинсы"
curl -X PUT http://localhost:8080/api/folders/5 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "parentFolderId": 3
  }'
```

### Пример 6: Удаление папки с перемещением товаров

```bash
# Удалить папку, переместив товары в родительскую
curl -X DELETE "http://localhost:8080/api/folders/5?moveProductsToParent=true" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Структура базы данных

### Таблица: product_folders
```sql
- id: bigint (PK)
- user_id: bigint (FK -> users.id)
- parent_folder_id: bigint (FK -> product_folders.id) [самоссылка]
- name: varchar(255)
- color: varchar(20)
- icon: varchar(50)
- position: integer
- created_at: timestamp
- updated_at: timestamp
```

### Обновленная таблица: ozon_products
```sql
+ folder_id: bigint (FK -> product_folders.id)
```

---

## Особенности работы

### Иерархия папок
- Неограниченная вложенность
- Самоссылающаяся структура через `parent_folder_id`
- Корневые папки имеют `parent_folder_id = NULL`

### Безопасность
- Проверка владения папкой перед любой операцией
- Невозможно переместить папку в саму себя
- Невозможно переместить папку в свою подпапку

### Удаление папок
- **С перемещением** (`moveProductsToParent=true`):
  - Товары перемещаются в родительскую папку
  - Подпапки перемещаются на уровень выше
- **Без перемещения** (`moveProductsToParent=false`):
  - Товары теряют привязку к папке (`folder_id = NULL`)
  - Товары НЕ удаляются

### Перемещение товаров
- Товары можно перемещать между папками
- Можно убрать из папки (`targetFolderId = null`)
- Товар может находиться только в одной папке

---

## Frontend интеграция

### React пример - Дерево папок

```jsx
import { useState, useEffect } from 'react';

function FolderTree() {
  const [tree, setTree] = useState([]);
  
  useEffect(() => {
    fetchFolderTree();
  }, []);
  
  const fetchFolderTree = async () => {
    const response = await fetch('/api/folders/tree', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('jwt')}`
      }
    });
    const data = await response.json();
    setTree(data);
  };
  
  const renderFolder = (folder) => (
    <div key={folder.id} style={{ marginLeft: '20px' }}>
      <div style={{ color: folder.color }}>
        📁 {folder.name} ({folder.productsCount})
      </div>
      {folder.subfolders.map(sub => renderFolder(sub))}
    </div>
  );
  
  return (
    <div>
      {tree.map(folder => renderFolder(folder))}
    </div>
  );
}
```

---

## SQL запросы для анализа

### Получить полный путь к папке
```sql
WITH RECURSIVE folder_path AS (
    SELECT id, parent_folder_id, name, 0 as level
    FROM product_folders
    WHERE id = 5
    
    UNION ALL
    
    SELECT f.id, f.parent_folder_id, f.name, fp.level + 1
    FROM product_folders f
    INNER JOIN folder_path fp ON f.id = fp.parent_folder_id
)
SELECT name FROM folder_path ORDER BY level DESC;
```

### Подсчитать товары во всех папках
```sql
SELECT 
    f.id,
    f.name,
    COUNT(p.id) as products_count
FROM product_folders f
LEFT JOIN ozon_products p ON f.id = p.folder_id
WHERE f.user_id = 10
GROUP BY f.id, f.name
ORDER BY f.name;
```

### Найти папки без товаров
```sql
SELECT f.*
FROM product_folders f
LEFT JOIN ozon_products p ON f.id = p.folder_id AND p.user_id = f.user_id
WHERE f.user_id = 10
GROUP BY f.id
HAVING COUNT(p.id) = 0;
```