# --- GIAI ĐOẠN 1: ĐÓNG GÓI (BUILD) ---
# Đổi sang dùng Maven kèm JDK 21 cho khớp với dự án của bạn
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy file pom.xml và tải thư viện trước (giúp cache lại, lần sau build sẽ cực kỳ nhanh)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ source code vào và build ra file .jar (bỏ qua chạy test cho lẹ)
COPY src ./src
RUN mvn clean package -DskipTests

# --- GIAI ĐOẠN 2: CHẠY THỰC TẾ (RUN) ---
# Đổi sang JRE 21 bản Alpine siêu nhẹ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Chỉ copy đúng file .jar đã build thành công từ giai đoạn 1 sang đây
COPY --from=builder /app/target/*.jar app.jar

# TUYỆT CHIÊU ÉP RAM: Giới hạn RAM tối đa cho Spring Boot (Ví dụ: 256MB)
ENV JAVA_OPTS="-Xms128m -Xmx256m"

# Mở cổng 8080 (hoặc cổng bạn cài trong application.properties)
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]