FROM quay.io/keycloak/keycloak:26.0

# No need for envsubst - Keycloak handles variable substitution natively!

# Copy realm files directly (not as templates)
COPY realms/*.json /opt/keycloak/data/import/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--import-realm"]