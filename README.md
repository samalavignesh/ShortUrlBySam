# ShortUrlBySam 🔗

**A high-performance, secure, and highly available URL shortening service designed for enterprise scale.**

I built this project to dive deep into building scalable, event-driven microservice architectures. While shortening a URL is easy, tracking rich analytics (geography, device types) on every click *without* slowing down the user's redirect requires a robust, asynchronous design. 

This application handles high-traffic redirection with sub-millisecond latency by offloading the heavy lifting of analytics parsing to background workers.

---

## ✨ Features

- **Blazing Fast Redirects**: Achieves sub-millisecond redirect latency. Instead of blocking the request to write to the database, it grabs the destination from a **Redis Cache** and fires a raw event to a **Redis Stream**.
- **Deep Analytics**: Tracks total clicks, referrers, operating systems, browsers, and geographic locations (Country/City).
- **Asynchronous Processing**: Background consumers parse User-Agents (via Yauaa) and IP addresses (via MaxMind GeoLite2) entirely off the main execution thread.
- **Enterprise Security**: 
  - **Sliding-Window Rate Limiting** using Redis Sorted Sets to accurately prevent abuse.
  - **IP Spoofing Protection** to safely parse `X-Forwarded-For` headers.
  - **Stateless JWT Authentication** with secure CORS configurations.
- **Custom Links & QR Codes**: Generate branded custom aliases or auto-generated secure hashes, complete with downloadable QR codes (via ZXing).
- **Dead-Letter Queues (DLQ)**: Fault-tolerant message processing. If an analytics event fails to process (e.g., DB constraint violation), it's safely routed to a DLQ for manual inspection to prevent infinite crashing loops.

---

## 🛠️ Tech Stack

- **Backend**: Java 21, Spring Boot 3.3.5, Spring Security
- **Data Persistence**: PostgreSQL, Spring Data JPA / Hibernate
- **Database Migrations**: Flyway (Highly optimized B-Tree indexing)
- **Caching & Messaging**: Redis (Caching, Rate Limiting, Pub/Sub Streams)
- **Deployment**: Docker, Docker Compose
- **Integrations**: MaxMind GeoLite2, Yauaa, Resend API (OTP Emails)

---

## 🏗️ How the Architecture Works

When a user clicks a shortened link (e.g., `shrt.ly/abc`):

1. **The Hot Path (Redirect)**: The API hits the endpoint, fetches the original URL directly from the Redis cache, and immediately returns an `HTTP 302 Redirect`. 
2. **The Async Path (Analytics)**: At the exact same time, a `ClickEventProducer` pushes the raw request headers (IP, User-Agent) to a Redis Stream. 
3. **The Consumer**: A background worker (`ClickEventConsumer`) pulls these events from the stream, parses the location and browser data, and batch-inserts the rich analytics into PostgreSQL.

---

## 🚀 Getting Started Locally

It's incredibly easy to run this project locally since the entire infrastructure is containerized.

### Prerequisites
- Docker & Docker Compose installed.
- Java 21 (if running outside of Docker).

### Run with Docker Compose

1. **Clone the repository**
   ```bash
   git clone https://github.com/samalavignesh/ShortUrlBySam.git
   cd ShortUrlBySam
   ```

2. **Spin up the infrastructure**
   This will start the Spring Boot application, PostgreSQL, and Redis in isolated containers.
   ```bash
   docker-compose -f docker-compose.prod.yml up -d --build
   ```

3. **Verify**
   The application will automatically run the Flyway database migrations on startup.
   - API is available at: `http://localhost:8080`
   - You can view the logs via: `docker-compose -f docker-compose.prod.yml logs -f app`

---

## 🤝 Contributing
Feel free to fork this project, submit pull requests, or open issues to suggest new features (like AI-powered predictive routing or LLM-generated link previews!).

---
*Built with ❤️ by Samala Vignesh*
