FROM docker.io/library/eclipse-temurin:17.0.12_7-jre
RUN useradd --create-home --uid 10001 clad
COPY clad-gateway.jar clad-mdr.jar /opt/clad/
RUN printf '#!/bin/sh\nexec java -jar /opt/clad/clad-gateway.jar "$@"\n' > /usr/local/bin/clad-gateway && chmod +x /usr/local/bin/clad-gateway
# the MDR worker ships in the same image; compose runs it with entrypoint clad-mdr
RUN printf '#!/bin/sh\nexec java -jar /opt/clad/clad-mdr.jar "$@"\n' > /usr/local/bin/clad-mdr && chmod +x /usr/local/bin/clad-mdr
USER clad
ENTRYPOINT ["clad-gateway"]
CMD ["serve"]
