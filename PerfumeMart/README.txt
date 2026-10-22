PerfumeMart - simple perfume store (HTML + Java + MySQL)

REQUIREMENTS
- Java 11 or newer (JDK)
- MySQL 8 running locally

SETUP
1. Download MySQL Connector/J (platform independent) from
   https://dev.mysql.com/downloads/connector/j/
   and copy the jar into the lib/ folder as lib/mysql-connector-j.jar
   (or replace the placeholder file there).
2. Create the database:
   mysql -u root -p < database.sql
3. Open PerfumeMartServer.java and set DB_USER and DB_PASS.
4. Compile (run inside the PerfumeMart folder):
   Windows:    javac -cp "lib/mysql-connector-j.jar" PerfumeMartServer.java
   Mac/Linux:  javac -cp "lib/mysql-connector-j.jar" PerfumeMartServer.java
5. Run:
   Windows:    java -cp ".;lib/mysql-connector-j.jar" PerfumeMartServer
   Mac/Linux:  java -cp ".:lib/mysql-connector-j.jar" PerfumeMartServer
6. Open http://localhost:8080

PAGES
- index.html     Home
- products.html  Product list from the database, add to cart
- checkout.html  Cart and order form, saves order and reduces stock

API
- GET  /api/products
- POST /api/orders  {"name","phone","address","items":[{"id":1,"qty":2}]}
