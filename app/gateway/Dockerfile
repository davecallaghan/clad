FROM docker.io/library/eclipse-temurin:17.0.12_7-jre
RUN useradd --create-home --uid 10001 clad
COPY clad-gateway.jar /opt/clad/clad-gateway.jar
RUN printf '#!/bin/sh\nexec java -jar /opt/clad/clad-gateway.jar "$@"\n' > /usr/local/bin/clad-gateway && chmod +x /usr/local/bin/clad-gateway
USER clad
ENTRYPOINT ["clad-gateway"]
CMD ["serve"]
