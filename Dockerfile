# ================= STAGE 1: 빌드 환경 =================
# JDK 25 버전을 빌드 베이스 이미지로 사용합니다.
FROM eclipse-temurin:25-jdk-jammy AS builder

# 작업 디렉토리를 설정합니다.
WORKDIR /workspace

# Gradle 실행에 필요한 파일들을 먼저 복사하여 Docker 캐시를 활용합니다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 소스 코드를 복사합니다.
COPY src src

# gradlew에 실행 권한을 부여하고, 테스트를 제외한 빌드를 실행합니다.
# 이 과정에서 의존성을 다운로드하고 애플리케이션을 컴파일하여 JAR 파일을 생성합니다.
RUN chmod +x ./gradlew && ./gradlew build -x test

# ================= STAGE 2: 실행 환경 =================
# JRE 25 버전을 실행 베이스 이미지로 사용하여 이미지 크기를 최적화합니다.
FROM eclipse-temurin:25-jre-jammy

# 작업 디렉토리를 설정합니다.
WORKDIR /app

# 빌드 단계에서 생성된 JAR 파일의 경로를 변수로 지정합니다.
ARG JAR_FILE=/workspace/build/libs/mocktalkback-0.1.0.jar

# 빌드 단계(builder)에서 생성된 JAR 파일을 실행 환경으로 복사하고 이름을 'app.jar'로 변경합니다.
COPY --from=builder ${JAR_FILE} app.jar

# 컨테이너 외부로 노출할 포트를 지정합니다. (application.yml 기반)
EXPOSE 8082

# 컨테이너가 시작될 때 JAR 파일을 실행하는 명령어를 설정합니다.
# Spring 프로파일을 'prod'로 활성화하여 'application-prod.yml' 설정을 사용하도록 합니다.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
