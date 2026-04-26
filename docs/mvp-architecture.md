# ClawHub MVP Architecture

## Core Boundary

ClawHub is the control plane.
QwenPaw is a managed data-plane instance.

That means:

- ClawHub decides when an instance exists and what container it maps to
- QwenPaw decides how the application behaves after the container is running

## MVP Functional Scope

1. Admin lists managed instances.
2. Admin creates an instance record with product type, image, container name, host port, and volume path.
3. ClawHub creates and starts the Docker container.
4. Admin can stop, restart, and delete that instance.
5. Admin opens the instance URL in a new tab.

## Suggested Runtime Model

- one managed instance maps to one Docker container
- one user typically maps to one instance in v1
- each instance gets its own host port and volume mount
- each instance stores a public URL or derives one from host + port

## Persistence Model

Suggested first table: `managed_instances`

Suggested fields:

- `id`
- `owner_user_id` nullable for early admin-only mode
- `product_type`
- `instance_name`
- `container_name`
- `docker_image`
- `host`
- `host_port`
- `container_port`
- `data_volume_host_path`
- `data_volume_container_path`
- `public_base_url`
- `status`
- `created_at`
- `updated_at`

## API Shape

- `GET /api/admin/instances`
- `POST /api/admin/instances`
- `POST /api/admin/instances/{id}/start`
- `POST /api/admin/instances/{id}/stop`
- `POST /api/admin/instances/{id}/restart`
- `DELETE /api/admin/instances/{id}`

## Non-Goals For MVP

- iframe embedding
- deep SSO integration with managed instances
- end-user self-service provisioning
- streaming task/event relay through ClawHub
- multi-host orchestration and scheduling
