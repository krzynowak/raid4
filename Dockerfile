FROM alpine:3.14
COPY root/* /root/
WORKDIR /root/src
RUN apk update
RUN apk add openjdk11 
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk
ENV PATH="$JAVA_HOME/bin:${PATH}"



CMD [ "sh", "-c", "./../script.sh" ]
