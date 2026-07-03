# Production Deployment Guide - I Rasa Perfumes

This document provides a comprehensive, production-ready guide to deploy the **I Rasa Perfumes** backend and frontend services onto a Linux server (IP: `35.154.206.209`) under the domain `www.irasaperfumes.com`.

---

## 1. System Architecture Overview

The production architecture consists of the following components:
1. **AWS Application Load Balancer (ALB)**: Receives external HTTPS requests on port `443` and routes them to the Linux target instance.
2. **Nginx Reverse Proxy**: Receives traffic from the ALB on port `80` (or `443` if SSL termination is at the instance level) and:
   - Proxies backend requests (`/api/**`, `/login`, `/register`, `/actuator/**`, etc.) to the Spring Boot Docker container.
   - Serves frontend static files directly from the disk for optimal performance.
3. **Docker Container**: Runs the Spring Boot JVM backend (`java -jar app.jar`) on port `8080` in a lightweight, non-root sandbox container.
4. **MySQL Database**: Hosted on the Linux host (or via AWS RDS) for data persistence.

```mermaid
graph TD
    Client[Client / Web Browser] -->|HTTPS :443| ALB[AWS Application Load Balancer]
    ALB -->|HTTP :80| Nginx[Nginx Reverse Proxy]
    
    subgraph Host Server (35.154.206.209)
        Nginx -->|Proxy Pass :8080| SpringBoot[Spring Boot Docker Container]
        Nginx -->|Serve Directly| Static[Static Frontend Assets]
        SpringBoot -->|JDBC :3306| MySQL[(MySQL Database)]
    end
```

---

## 2. Docker Containerization

We containerize the Spring Boot application using the provided multi-stage `Dockerfile`. 

### A. Environment Configuration
Create a `.env` file on the production server to hold sensitive environment variables:

```bash
# Database Configuration
DB_URL=jdbc:mysql://localhost:3306/rasa_db_prod?useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=kunal11

# Mail Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=irasaperfumes@gmail.com
MAIL_PASSWORD=jsfe look dbnx wqli

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your_admin_secure_password
ADMIN_EMAIL=irasaperfumes@gmail.com

# Base URL & CORS Configurations
SPRING_PROFILES_ACTIVE=prod
APP_BASE_URL=https://www.irasaperfumes.in
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,https://www.irasaperfumes.in,https://irasaperfumes.in
```

### B. Building the Docker Image
Navigate to the backend folder (where the `Dockerfile` resides) and run:

```bash
docker build -t irasa-backend:latest .
```

### C. Running the Docker Container
Run the container in detached mode with automated restarts and mount a volume for uploaded files (e.g., invoices, documents):

```bash
docker run -d \
  --name irasa-app \
  --restart always \
  -p 8080:8080 \
  --env-file .env \
  -v /var/www/irasa/uploads:/app/upload \
  irasa-backend:latest
```

---

## 3. Nginx Configuration

Install Nginx on the Linux server:
```bash
sudo apt update
sudo apt install nginx -y
```

### Configure Virtual Host
Create or edit `/etc/nginx/sites-available/irasaperfumes`:

```nginx
server {
    listen 80;
    server_name irasaperfumes.com www.irasaperfumes.com;

    # Root directory of the frontend static files
    root /var/www/html/i-rasa;
    index index.html index.htm;

    # Serve static assets directly (CSS, JS, Images)
    location ~* \.(?:css|js|jpg|jpeg|gif|png|ico|svg|woff|woff2|ttf|otf|webp)$ {
        expires 30d;
        add_header Cache-Control "public, no-transform";
        access_log off;
    }

    # Proxy Actuator health checks and monitoring safely
    location /actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Proxy backend API calls
    location ^~ /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Max upload size configuration
        client_max_body_size 5M;
    }

    # Fallback to backend routing for auth and core MVC endpoints
    location /login {
        proxy_pass http://127.0.0.1:8080/login;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /register {
        proxy_pass http://127.0.0.1:8080/register;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Custom Luxury Error Pages Served Directly by Nginx
    error_page 404 /404.html;
    location = /404.html {
        root /var/www/html/i-rasa;
        internal;
    }

    error_page 500 502 503 504 /505.html;
    location = /500.html {
        root /var/www/html/i-rasa;
        internal;
    }

    # General fallback to index.html for SPA client-side routing if applicable
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Enable the site configuration and restart Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/irasaperfumes /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## 4. SSL Certificates with Certbot (HTTPS)

Secure the Nginx server with Let's Encrypt SSL certificates. Certbot will automatically rewrite the Nginx config to handle HTTPS on port 443 and redirect all port 80 traffic to HTTPS.

```bash
# Install Certbot and the Nginx plugin
sudo apt install certbot python3-certbot-nginx -y

# Request and install the certificate
sudo certbot --nginx -d irasaperfumes.com -d www.irasaperfumes.com
```

Confirm that Let's Encrypt auto-renewal is scheduled:
```bash
sudo systemctl status certbot.timer
# Or run a dry-run test
sudo certbot renew --dry-run
```

---

## 5. AWS Application Load Balancer (ALB) Setup

When deploying behind an AWS ALB, follow these configuration steps to ensure seamless communication and health monitoring:

### A. Target Group Configuration
1. Create a new Target Group under the AWS EC2 dashboard.
2. Select **Instances** or **IP Addresses** (if targeting the Linux server IP `35.154.206.209` directly).
3. Set the Target Port to **80** (to forward traffic to Nginx) or **8080** (if sending traffic directly to the Spring Boot Docker container).
4. **Health Check Settings**:
   - **Path**: `/actuator/health`
   - **Port**: Same as target (80 or 8080).
   - **Protocol**: `HTTP`
   - **Success Codes**: `200` (Spring Boot Actuator returns `200 OK` for healthy up-status).
   - **Healthy Threshold**: `3`
   - **Unhealthy Threshold**: `3`
   - **Timeout**: `5 seconds`
   - **Interval**: `30 seconds`

### B. Load Balancer Listener Settings
1. Create an HTTPS listener on port `443`.
2. Attach a public SSL/TLS certificate created via AWS Certificate Manager (ACM) for `irasaperfumes.com`.
3. Set the default action of the listener to forward traffic to the Target Group created in Step A.
4. (Optional) Create an HTTP listener on port `80` with a redirect rule targeting `HTTPS://#{host}:443#{path}?#{query}`.

---

## 6. Verification and Troubleshooting

### A. Check Container Logs
To monitor backend startup and runtime logs:
```bash
docker logs -f irasa-app
```

### B. Verify Actuator Health Endpoints
From the server local shell, test the health check output:
```bash
curl -i http://localhost:8080/actuator/health
```
You should receive a `200 OK` response with a JSON body similar to:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### C. Verify Error Pages
To verify Nginx displays the elegant error pages:
- Visit `https://www.irasaperfumes.com/invalid-path-for-testing` to see the **404 Scent Lost** page.
- Stop the Docker container (`docker stop irasa-app`) and visit any dynamic page to verify the Nginx **500 Server Error** fallback page displays.
