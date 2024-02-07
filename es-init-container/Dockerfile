FROM alpine:3.12

RUN apk add --no-cache jq curl

RUN adduser --disabled-password --shell=/bin/bash user

USER root
RUN mkdir /work
WORKDIR /work

COPY *.sh /work/
RUN chmod +x /work/*.sh

USER user

ENTRYPOINT ["bash","/work/init.sh"]