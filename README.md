# VCART – Online Shopping Cart and Order Management System

<p align="center">
  <strong>A Java Spring Boot based e-commerce and inventory management application</strong>
</p>

VCART is a database-driven e-commerce web application developed using Java, Spring Boot, Thymeleaf and PostgreSQL. It provides customers with an online shopping experience while giving store owners a dedicated workspace to manage products, inventory, orders and business operations.

The application combines object-oriented programming principles with backend development, database connectivity, authentication, authorization and web technologies to demonstrate how a real-world shopping management system can be designed.

---

## Table of Contents

1. [About the Project](#about-the-project)
2. [Problem Statement](#problem-statement)
3. [Project Objectives](#project-objectives)
4. [Key Features](#key-features)
5. [Technology Stack](#technology-stack)
6. [Why These Technologies?](#why-these-technologies)
7. [Java OOP Concepts Used](#java-oop-concepts-used)
8. [System Architecture](#system-architecture)
9. [Database and Data Management](#database-and-data-management)
10. [Installation and Setup](#installation-and-setup)
11. [Running the Application](#running-the-application)
12. [Testing](#testing)
13. [Security](#security)
14. [Current Limitations](#current-limitations)
15. [Future Scope](#future-scope)
16. [Learning Outcomes](#learning-outcomes)

---

## About the Project

In today's digital environment, customers expect a convenient way to discover products, compare prices, manage shopping carts and place orders online. At the same time, store owners need an organized system to manage products, stock levels, customer orders and business information.

VCART aims to bring these two experiences together in one application.

It provides two separate interfaces:

- **Customer Interface:** An online shopping experience where customers can browse products, search the catalogue, manage their carts, maintain wishlists and track orders.
- **Owner Interface:** A dedicated management environment where store owners can access business analytics and manage products, inventory, orders and other store operations.

The application uses a centralized PostgreSQL database so that customer activities and owner operations work with shared application data.

## Problem Statement

Traditional shopping and inventory management processes can become difficult to manage when product information, stock records, customer details and order information are maintained separately.

Some common problems include:

- Difficulty maintaining accurate product and inventory records.
- Time-consuming manual order management.
- Lack of a centralized shopping platform.
- Difficulty tracking customer orders.
- Limited visibility into sales and business performance.
- Risk of inconsistencies between orders and inventory.

VCART addresses these problems by providing a centralized web-based application that connects the customer shopping experience with owner-side management operations.

## Project Objectives

- Develop a complete online shopping and order management application.
- Implement separate customer and owner authentication flows.
- Provide a structured product catalogue with search, filtering and sorting.
- Allow customers to manage shopping carts and wishlists.
- Support checkout and order management workflows.
- Provide owners with analytics and store management functionality.
- Maintain centralized data using PostgreSQL.
- Apply Java OOP principles and Spring Boot architecture.
- Implement authentication, authorization and secure application operations.

---

## Key Features

### 1. Public Landing Page

- Common homepage for visitors.
- Project information and feature sections.
- Separate customer and owner login options.
- Navigation to the appropriate application experience.

### 2. Customer E-commerce Platform

- Customer registration and login.
- Product catalogue.
- Database-backed product discovery.
- Product search.
- Category-based browsing.
- Price sorting and catalogue filters.
- Product details.
- Shopping cart management.
- Wishlist functionality.
- Checkout workflow.
- Order history and order details.
- Customer account and profile-related functionality.

### 3. Owner Analytics Dashboard

- Dedicated owner login.
- Business analytics dashboard.
- Sales and order summaries.
- Customer-related metrics.
- Product and inventory information.
- Low-stock visibility.
- Recent order information.

### 4. Owner Management Workspace

- Product management.
- Category management.
- Inventory management.
- Order management.
- Customer management.
- Transaction and refund workflows.
- Staff management.
- Support management.
- Finance and coupon management.

The availability of individual operations depends on the configured application data, permissions and existing business rules.

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Core programming language and application logic |
| Spring Boot 4.1.2 | Backend application framework |
| Spring MVC | HTTP requests, controllers and routing |
| Thymeleaf | Server-side HTML rendering |
| Spring Security | Authentication and authorization |
| Spring JDBC | Database communication |
| JdbcTemplate | Executing SQL queries and handling database operations |
| PostgreSQL | Relational database |
| Supabase | Hosted PostgreSQL database platform |
| HTML5 | Webpage structure |
| CSS3 | Styling and responsive layouts |
| JavaScript | Client-side interactions and API requests |
| Maven | Dependency management and build automation |
| Maven Wrapper | Consistent project build and execution |
| Git and GitHub | Version control and source-code hosting |

---

## Why These Technologies?

### Java 17

Java is used for the main application logic because it supports object-oriented programming, strong typing, exception handling and reusable application components.

### Spring Boot

Spring Boot simplifies Java backend development by providing application configuration, dependency injection, web development support and integration with other Spring modules.

### Spring MVC

Spring MVC organizes the application around controllers and request mappings. It handles incoming HTTP requests and connects them to the appropriate application logic.

### Thymeleaf

Thymeleaf is used to generate dynamic HTML pages on the server side. It allows backend data, such as products and categories, to be displayed directly in HTML templates.

### Spring Security

Spring Security protects application routes and APIs. It supports authentication, role-based access, session handling and CSRF protection.

### Spring JDBC and JdbcTemplate

Spring JDBC provides a straightforward way to communicate with relational databases.

JdbcTemplate helps execute SQL statements, retrieve records and map database results into Java objects without requiring extensive JDBC boilerplate.

### PostgreSQL and Supabase

PostgreSQL provides relational data storage and supports structured queries, relationships and constraints.

Supabase provides a hosted PostgreSQL database environment, allowing the application to use centralized database storage rather than relying only on local data.

### HTML, CSS and JavaScript

These technologies provide the customer and owner interfaces.

- HTML structures the pages.
- CSS creates the visual design and responsive layouts.
- JavaScript handles interactions such as search, cart updates and asynchronous API requests.

### Maven

Maven manages project dependencies, compilation, testing and packaging. The included Maven Wrapper allows developers to build the project without separately installing Maven.

---

## Java OOP Concepts Used

Object-oriented programming is an important part of VCART's implementation. The application organizes its logic into Java classes with separate responsibilities.

### 1. Classes and Objects

A class defines the structure and behaviour of an entity, while an object is an instance of that class.

Examples of application classes include:

- `Product`
- `CartItem`
- `ProductService`
- `CartService`
- `OrderService`
- `AuthService`

For example, the `Product` class represents a product with properties such as its ID, name, price and stock quantity.

A product record retrieved from the database can be represented as a Java object.

### 2. Encapsulation

Encapsulation means combining data and related methods inside a class while controlling access to that data.

In VCART, model classes represent application entities and their associated properties. Business operations are handled through service classes instead of allowing every part of the application to directly manipulate database records.

For example, product and cart-related data is handled through their respective model and service classes.

### 3. Abstraction

Abstraction means hiding internal implementation details and exposing only the functionality required by other parts of the application.

VCART follows a layered structure:

- Controllers handle incoming requests.
- Services contain business logic.
- Repositories handle database operations.
- Models represent application data.

For example, a controller can call a product service to retrieve products without needing to know the SQL query used internally by the repository.

### 4. Inheritance

Inheritance allows one class to acquire properties and behaviour from another class.

Java and Spring provide inheritance-based framework classes and extension points. However, VCART primarily uses a layered service, controller and repository design rather than relying on a large custom inheritance hierarchy.

### 5. Polymorphism

Polymorphism allows the same method or interface contract to work with different implementations.

Java supports polymorphism through method overriding and interfaces. Framework callback contracts and interface-based components can also use this principle.

The central design of VCART focuses more on separation of responsibilities and dependency management than on custom polymorphic class hierarchies.

### Additional Java Concepts

Other Java concepts relevant to the application include:

- Packages and access modifiers.
- Constructors and object initialization.
- Exception handling.
- Collections and data handling.
- Annotations.
- Dependency injection.
- Method calls and parameter passing.

**Important:** OOP principles are not just theoretical concepts; they help organize the application into maintainable components and make the code easier to test and extend.

---

## System Architecture

VCART follows a layered web application architecture.

```text
                 USER
                  |
                  v
            WEB BROWSER
                  |
                  v
          PRESENTATION LAYER
       Thymeleaf + HTML + CSS + JS
                  |
                  v
          SPRING MVC CONTROLLERS
                  |
                  v
            SERVICE LAYER
          Business Logic
                  |
                  v
          REPOSITORY / DAO LAYER
             JdbcTemplate
                  |
                  v
          POSTGRESQL DATABASE
                SUPABASE
```

### Layer Responsibilities

| Layer | Responsibility |
|---|---|
| Presentation | Displays webpages and handles user interactions |
| Controller | Receives HTTP requests and returns responses |
| Service | Implements application business rules |
| Repository | Executes database queries |
| Model | Represents application entities and data |

The frontend and backend are part of the same Spring Boot application. They do not need to run as separate servers.

---

## Database and Data Management

VCART uses PostgreSQL as its relational database, hosted through Supabase.

The database stores application information such as:

- User and account information.
- Products and categories.
- Inventory records.
- Shopping cart information.
- Customer orders.
- Wishlist information.
- Payment and transaction-related records.
- Other store management information.

Spring JDBC and JdbcTemplate are used to interact with the database.

The application uses database-backed queries to retrieve and manage application information.

### Database Configuration

Database connection settings are maintained in:

`src/main/resources/application.properties`

The database password is supplied through an environment variable rather than being committed as a password in the source code.

---

## Installation and Setup

Follow these steps to run VCART on another Windows laptop.

### Prerequisites

Install the following:

1. **Java Development Kit 17**
2. **Git**
3. An internet connection.
4. Access to the configured Supabase PostgreSQL database.

Maven does not need to be installed separately because the project includes Maven Wrapper.

Node.js is not required to run the current Spring Boot application unless additional frontend development tooling is introduced.

### Step 1: Clone the Repository

Open PowerShell and execute:

```powershell
git clone https://github.com/BusySouvik/OOP-S-Project.git
```

Navigate to the VCART project:

```powershell
cd OOP-S-Project\VCART
```

### Step 2: Verify Java Installation

```powershell
java -version
```

The installed version should be Java 17.

If Java is missing or a different version is being used, install and configure JDK 17 before continuing.

### Step 3: Configure the Database

Open the application configuration:

```powershell
Get-Content .\src\main\resources\application.properties
```

Review the datasource configuration and identify all required database connection properties.

Configure the connection to your authorized Supabase PostgreSQL instance.

The project uses the following environment variable for the database password:

```text
SPRING_DATASOURCE_PASSWORD
```

Do not hardcode the database password in `application.properties`, Java files or GitHub.

For the current PowerShell session, securely enter the password:

```powershell
$secure = Read-Host "Enter Supabase database password" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)

try {
    $env:SPRING_DATASOURCE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
```

Configure any additional datasource environment variables required by `application.properties`.

**Database access note:** A GitHub clone does not automatically provide access to the Supabase database. The person running the application must have authorized database credentials and access to the required database schema.

### Step 4: Build the Application

Run:

```powershell
.\mvnw.cmd clean install
```

This command cleans previous build output, compiles the application, executes tests and installs the resulting build into the local Maven repository.

### Step 5: Start the Application

Run:

```powershell
.\mvnw.cmd spring-boot:run
```

Wait until Spring Boot completes its startup process.

### Step 6: Open VCART

Open your browser and visit:

```text
http://localhost:8080
```

The public VCART landing page should appear.

From there, users can navigate to customer login or owner login.

---


## Testing

VCART includes automated tests using the Spring Boot testing infrastructure and MockMvc.

To run the test suite:

```powershell
.\mvnw.cmd clean test
```

To package the application:

```powershell
.\mvnw.cmd package
```

The latest reported local verification completed with:

- **46 automated tests passed**
- **0 test failures**
- **0 test errors**
- JavaScript syntax checks passed
- Maven package build successful

These results represent the reported local test run. They do not establish that every workflow has been verified against a live Supabase database.

---

## Security

Security is an important part of VCART.

The application uses:

- Spring Security for authentication and authorization.
- BCrypt password hashing.
- Role-based access control.
- CSRF protection for state-changing requests.
- Session management.
- Session fixation protection.
- Customer ownership checks for protected resources.
- Environment-based database password configuration.

Customer and owner access are separated so that customers cannot access owner-only management functionality.

Database credentials and other private configuration values must never be committed to a public repository.

---

## Current Limitations

The following limitations should be considered when running or evaluating the application:

1. **Database credentials:** Each installation needs authorized access to the configured Supabase database.
2. **Live database verification:** A successful local build does not guarantee that a fresh installation can connect to the hosted database.
3. **Payment processing:** The current payment workflow is a development/mock flow and should not be considered a fully integrated live payment gateway.
4. **External services:** Any future third-party integrations require their own configuration and credentials.
5. **Browser testing:** Desktop and mobile appearance should be manually checked after installation.

---

## Future Scope

VCART can be extended with additional capabilities:

- Integration with a real payment gateway.
- Product reviews and ratings.
- Personalized product recommendations.
- Advanced sales and inventory analytics.
- Email and SMS notifications.
- Order tracking and delivery integration.
- Product image storage and management.
- Improved reporting and business intelligence.
- Automated deployment and CI/CD pipelines.
- Expanded mobile shopping experience.

---

## Learning Outcomes

Developing VCART provides practical experience in:

- Java object-oriented programming.
- Spring Boot application development.
- MVC architecture.
- REST API development.
- Authentication and authorization.
- SQL and relational database management.
- Database connectivity using JdbcTemplate.
- Frontend and backend integration.
- Session and security management.
- Software testing and debugging.
- Git and GitHub version control.

The project demonstrates how concepts learned in Java and database management can be applied to develop a structured, database-driven web application.

---

## Author and Repository

**Project:** VCART – Online Shopping Cart and Order Management System

**Repository:** [OOP-S-Project / VCART](https://github.com/BusySouvik/OOP-S-Project/tree/main/VCART)

**Technology:** Java 17, Spring Boot, Thymeleaf, Spring Security, Spring JDBC and PostgreSQL.

---

<p align="center">
  <strong>VCART – Making Online Shopping and Store Management More Organized.</strong>
</p>
