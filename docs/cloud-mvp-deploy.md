# Cloud MVP Deploy

This document assumes:

- `ClawHub` runs as the control-plane container
- managed `QwenPaw` instances run as separate containers on the same host
- the host already has the `qwenpaw:local` image loaded
- the control plane reaches host Docker through `/var/run/docker.sock`
- the control plane reuses the host Docker CLI binary through `/usr/bin/docker`
- the frontend has already been built into `src/main/resources/static`

## Known Actual Values

- `QwenPaw` image: `qwenpaw:local`
- `QwenPaw` container port: `8088`
- `QwenPaw` persistent path in container: `/root/.qwenpaw`
- `ClawHub` data path on host: `/srv/clawhub/data`
- per-instance host path convention: `/srv/clawhub/instances/<instanceName>`

## Important Port Rule

Container port `8088` is the app port inside each `QwenPaw` container.

Host port must be unique for each instance.

Example:

- existing standalone `qwenpaw`: `http://IP:8088`
- managed instance A: `http://IP:18088`
- managed instance B: `http://IP:18089`

## Build ClawHub Image

```bash
cd /srv/clawhub
npm --prefix frontend install
npm --prefix frontend run build
mvn -DskipTests package
docker build -t clawhub:mvp .
```

## Run ClawHub

```bash
mkdir -p /srv/clawhub/data
mkdir -p /srv/clawhub/instances

docker rm -f clawhub 2>/dev/null || true

docker run -d \
  --name clawhub \
  --restart unless-stopped \
  -p 8080:8080 \
  -v /usr/bin/docker:/usr/bin/docker \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /srv/clawhub/data:/app/data \
  clawhub:mvp
```

Open:

- `http://IP:8080/`

## First MVP Validation Flow

Create an instance in the UI using values like:

- product type: `QWENPAW`
- instance name: `demo-qwenpaw-01`
- container name: `qwenpaw-demo-01`
- docker image: `qwenpaw:local`
- host: `IP or 127.0.0.1`
- host port: `18088`
- container port: `8088`
- host volume path: `/srv/clawhub/instances/demo-qwenpaw-01`
- container volume path: `/root/.qwenpaw`
- public base url: `http://IP:18088`

Then verify:

```bash
docker ps
docker logs clawhub --tail 200
docker logs qwenpaw-demo-01 --tail 200
```

## Current Limitation

The current control plane creates containers through Docker CLI with:

- one port mapping
- one volume mapping
- no custom environment variables yet
- no reverse proxy automation yet
- bootstrap admin credentials should be rotated before long-lived deployment

If `QwenPaw` later requires fixed env vars or extra mounts, add them to the control-plane create template before wider rollout.
