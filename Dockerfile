# Narrative News API: Spring Boot jar in a small Java 21 image. Works on any container host (Render, Koyeb, a VM...).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
# fontconfig/freetype are needed by Java2D, which PDFBox uses to draw PDF pages.
# libtesseract5 is the OCR engine (text view / tap-a-story); Tess4J looks for the unversioned libtesseract.so.
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig libfreetype6 fonts-dejavu-core libtesseract5 \
    && ln -sf "$(find /usr/lib -name 'libtesseract.so.5' | head -n1)" /usr/lib/libtesseract.so \
    && rm -rf /var/lib/apt/lists/*
# Hindi + English OCR models (more accurate than the distro's "fast" ones)
ADD https://github.com/tesseract-ocr/tessdata/raw/main/hin.traineddata https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata /app/tessdata/
ENV TESSDATA_PATH=/app/tessdata
WORKDIR /app
COPY --from=build /app/target/narrative-news-1.0.0.jar app.jar
# Sized for a 512 MB instance. With 1 GB or more, raise -Xmx and turn OCR on (OCR_ENABLED=true).
ENV JAVA_OPTS="-Xmx224m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss256k -Dserver.tomcat.threads.max=16" \
    MALLOC_ARENA_MAX=2
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
