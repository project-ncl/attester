#!/bin/bash

export QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=$(cat /mnt/secrets/kieserver-${APP_ENV}/kieserver-sa-secret)
export QUARKUS_OIDC_CLIENT_CLIENT_ID=$(cat /mnt/secrets/kieserver-${APP_ENV}/kieserver-sa-client)

export ATTESTER_CONTAINER_REGISTRY_USERNAME=$(cat /mnt/secrets/attester-${APP_ENV}/oci-registry-username)
export ATTESTER_CONTAINER_REGISTRY_PASSWORD=$(cat /mnt/secrets/attester-${APP_ENV}/oci-registry-password)
export ATTESTER_CONTAINER_REGISTRY_IMAGE=$(cat /mnt/secrets/attester-${APP_ENV}/container-image)
export ATTESTER_COSIGN_PRIVATEKEYPASSWORD=$(cat /mnt/secrets/attester-${APP_ENV}/cosign.password)

/opt/jboss/container/java/run/run-java.sh
