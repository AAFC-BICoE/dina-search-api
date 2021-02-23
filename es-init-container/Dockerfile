FROM alpine:3.12

RUN apk --no-cache add bash curl

RUN adduser --disabled-password --shell=/bin/bash user

USER root
RUN mkdir /work
WORKDIR /work

COPY *.sh /work/
RUN chmod +x /work/*.sh

USER user

ENTRYPOINT ["bash","/work/init.sh"]