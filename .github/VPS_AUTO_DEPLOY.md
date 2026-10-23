# LinLiNews VPS deployment
GitHub Actions deploys this service to the VPS whenever changes reach `main`. The workflow builds a new Docker image, sends it through the service-restricted SSH key, and replaces the container. The SQLite database remains in its persistent VPS directory.
