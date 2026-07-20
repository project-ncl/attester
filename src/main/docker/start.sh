#!/bin/bash

export QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET=$(cat /mnt/secrets/kieserver-${APP_ENV}/kieserver-sa-secret)
export QUARKUS_OIDC_CLIENT_CLIENT_ID=$(cat /mnt/secrets/kieserver-${APP_ENV}/kieserver-sa-client)

export ATTESTER_CONTAINER_REGISTRY_USERNAME=$(cat /mnt/secrets/attester-${APP_ENV}/oci-registry-username)
export ATTESTER_CONTAINER_REGISTRY_PASSWORD=$(cat /mnt/secrets/attester-${APP_ENV}/oci-registry-password)
export ATTESTER_CONTAINER_REGISTRY_IMAGE=$(cat /mnt/secrets/attester-${APP_ENV}/container-image)
export ATTESTER_COSIGN_PRIVATEKEYPASSWORD=$(cat /mnt/secrets/attester-${APP_ENV}/cosign.password)

# Reuse the same optional no-network signing configuration as the pipeline when mounted.
if [[ -z "${ATTESTER_COSIGN_SIGNINGCONFIGPATH:-}" && -f "/mnt/secrets/attester-${APP_ENV}/signing_config.v0.2.no-network.json" ]]; then
  export ATTESTER_COSIGN_SIGNINGCONFIGPATH="/mnt/secrets/attester-${APP_ENV}/signing_config.v0.2.no-network.json"
fi
export CLIENT_JAAS_CONF="$(cat /mnt/secrets/attester-${APP_ENV}/kafka_jaas_conf)"

/opt/jboss/container/java/run/run-java.sh
