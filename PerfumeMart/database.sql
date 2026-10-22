CREATE DATABASE IF NOT EXISTS perfumemart CHARACTER SET utf8mb4;
USE perfumemart;

CREATE TABLE IF NOT EXISTS products (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  brand VARCHAR(50) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  size VARCHAR(20),
  stock INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS orders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  customer_name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  address VARCHAR(255) NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  product_id INT NOT NULL,
  quantity INT NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (product_id) REFERENCES products(id)
);

INSERT INTO products (name, brand, price, size, stock) VALUES
('Sauvage EDT', 'Dior', 9500.00, '100ml', 25),
('Bleu de Chanel', 'Chanel', 11500.00, '100ml', 20),
('Eros', 'Versace', 6200.00, '100ml', 30),
('Good Girl', 'Carolina Herrera', 8900.00, '80ml', 15),
('Acqua di Gio', 'Armani', 7400.00, '100ml', 22),
('Black Opium', 'YSL', 9800.00, '90ml', 18);
