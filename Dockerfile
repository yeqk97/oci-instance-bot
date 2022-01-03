FROM openjdk:11.0.13-jre-slim
RUN addgroup --system spring && useradd --system -g spring spring
RUN mkdir /app
RUN chown -R spring:spring /app
USER spring:spring
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]