# Cocos Backend Challenge

API de un broker para el [challenge](https://github.com/cocoscap/cocos-challenge/blob/main/backend-challenge.md) de Cocos.

Stack: Java 25, Spring Boot 4 (Web MVC, Data JPA, Validation), Flyway, PostgreSQL 18.

## Cómo correrlo y testearlo

Requisitos: JDK 25 y Docker.

```bash
docker compose up -d                                   # Postgres en localhost:5432
./mvnw spring-boot:run                                 # API en localhost:8080
./mvnw test                                            # requiere Docker
```

### Contra otra base de datos

Con el perfil `prod`, la conexión se configura con variables de entorno:

```bash
./mvnw -q package -DskipTests
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://host:5432/cocos \
DATABASE_USERNAME=usuario \
DATABASE_PASSWORD=contraseña \
java -jar target/cocos-0.0.1-SNAPSHOT.jar
```

`DATABASE_URL` tiene que ser una URL **JDBC** (`jdbc:postgresql://host:puerto/base`), sin el usuario ni la
contraseña adentro: van en sus propias variables. Si el proveedor da una URL del tipo
`postgresql://usuario:contraseña@host:5432/base`, hay que separarla. Las bases en la nube suelen requerir
además `?sslmode=require` al final.

Si la base ya tiene las tablas del challenge, Flyway registra V1 como aplicada sin ejecutarla y solo aplica
V2 y V3.

## Decisiones de diseño

- **Una orden rechazada no es un error HTTP**: se devuelve con 201. El motivo solo viaja en la respuesta,
  porque el esquema no tiene una columna para guardarlo.
- **Precios**: el último precio es el `close` de la fila de `marketdata` con la fecha más reciente; sin
  ninguna, una orden MARKET se rechaza (400) y la posición muestra los precios en `null`. Las LIMIT conservan
  su precio y quedan en NEW, con sus fondos reservados. Las transferencias de cash son MARKET, en pesos
  enteros y a precio 1.
- La API redondea a 2 decimales.
- **Las fechas y horas** están en hora de Argentina, como los datos de prueba, y la aplicación corre en esa zona sin importar la del host.

## Cambios en el esquema

El [esquema provisto](https://github.com/cocoscap/cocos-challenge/blob/main/database.sql) se carga tal cual. Las migraciones agregan:

| Cambio | Por qué |
|---|---|
| `UNIQUE (users.accountNumber)` | Cada request busca al usuario por número de cuenta |
| `CHECK` sobre `orders.side`, `type`, `status` | Un valor desconocido impediría leer las órdenes del usuario |
| `CHECK (orders.size >= 0)` | Un tamaño negativo invertiría la orden (una compra restaría acciones) |
| `NOT NULL` sobre `marketdata.instrumentId`, `date`, `close`; precios `> 0` | Sin fecha o sin precio no hay último precio, y con precio 0 se dividiría por cero |
| Índice `orders (userId, status)` | Cada request lee las órdenes FILLED y NEW de un usuario; sin índice recorrería toda la tabla |
| Índice `marketdata (instrumentId, date)` | Para buscar el último precio de cada instrumento sin recorrer toda la tabla |
