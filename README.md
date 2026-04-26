# ClawHub

ClawHub is the control plane for managing QwenPaw, OpenClaw, and other product instances.

Current MVP scope:

- public user registration
- session-based login
- two roles: `ADMIN` and `USER`
- one user can own at most one instance
- platform-wide maximum of `5` instances
- create/start/stop/restart/delete Docker containers for managed instances
- open an instance in a new tab by its resolved public URL

Repository layout:

- `docs/` for handover and architecture notes
- `src/main/java/` for the Spring Boot control-plane backend
- `frontend/` for the Vue 3 + TypeScript source
- `src/main/resources/static/` for the built frontend assets

Important boundary:

- ClawHub owns users, instance metadata, and container lifecycle
- QwenPaw owns chat, relay execution, browser tasks, and model/runtime behavior inside each instance

## Run locally

```bash
cd frontend
npm install
npm run build
cd ..
mvn spring-boot:run
```

Then open `http://localhost:8080/`.

## Build image

```bash
cd frontend
npm install
npm run build
cd ..
mvn -DskipTests package
docker build -t clawhub:mvp .
```

## Deploy MVP container on a cloud server

ClawHub calls Docker from inside its own container, so the host Docker socket must be mounted.

Example:

```bash
docker run -d \
  --name clawhub \
  -p 8080:8080 \
  -v /usr/bin/docker:/usr/bin/docker \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /srv/clawhub/data:/app/data \
  clawhub:mvp
```

Then open `http://<server-ip>:8080/`.

Notes:

- `ClawHub` is the control plane inside this container
- `QwenPaw` instances are separate managed containers on the same host
- the container reuses the host Docker CLI via `/usr/bin/docker` and reaches the Docker daemon via `/var/run/docker.sock`
- default bootstrap admin credentials come from `application.yml`:
  - username: `admin`
  - password: `change-me-admin`
  - change these before real deployment
- public registration creates `USER` accounts only
- `USER` can manage only their own instance
- `ADMIN` can manage all users and instances
- one user may hold only one instance at a time
- the platform allows at most `5` total instances at once
- if the server cannot pull external images, preload the target `QwenPaw` image first, then use the UI/API to create containers from that local image
- current default `QwenPaw` runtime values in the UI are:
  - image: `qwenpaw:local`
  - container port: `8088`
  - container data path: `/root/.qwenpaw`
  - sample host volume path: `/srv/clawhub/instances/demo-qwenpaw-01`
- do not reuse host port `8088` if an existing standalone `qwenpaw` container already occupies it; assign unique host ports per managed instance such as `18088`, `18089`, `18090`
