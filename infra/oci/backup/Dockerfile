FROM postgres:16-alpine

RUN apk add --no-cache python3 py3-pip \
  && pip3 install --no-cache-dir --break-system-packages oci-cli \
  && rm -rf /root/.cache
