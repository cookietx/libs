cumulus-4-docker-services

# Bring up Cumulus 4 Services in Docker
docker-compose -f docker-compose_cumulus4_services.yml up -d

# Attach endpoints to allow docker services to be discovered by Kubernetes pods
kubectl apply -f ./kubernetes_service_endpoints

# Verify that the services were created 
# Docker - Shows active containers running
docker ps

# Verify that the service endpoints were created for Kubernetes
kubectl get svc
