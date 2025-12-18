FROM alpine:latest AS downloader
RUN wget -O /envsubst https://github.com/a8m/envsubst/releases/download/v1.2.0/envsubst-Linux-x86_64 \
    && chmod +x /envsubst

FROM quay.io/keycloak/keycloak:26.0

USER root

# Copy statically-linked envsubst from downloader stage
COPY --from=downloader /envsubst /usr/local/bin/envsubst

# Copy the entrypoint script and set permissions
COPY docker-entrypoint.sh /opt/keycloak/docker-entrypoint.sh
RUN chmod +x /opt/keycloak/docker-entrypoint.sh

USER keycloak

# Copy realm templates
COPY realms/*.tpl /opt/keycloak/data/import-templates/

ENTRYPOINT ["/opt/keycloak/docker-entrypoint.sh"]