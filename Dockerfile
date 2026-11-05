FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble


RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-tur \
    libtesseract-dev \
    libleptonica-dev && \
    rm -rf /var/lib/apt/lists/*


ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

WORKDIR /app

COPY --from=build /app/target/cv-ats-analyzer.jar /app/cv-ats-analyzer.jar

CMD ["java", "-jar", "/app/cv-ats-analyzer.jar"]
