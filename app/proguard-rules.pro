# R8 규칙
# - OkHttp, kotlinx.serialization, Compose는 라이브러리에 포함된 규칙(consumer rules)으로 충분하다.
# - KIS 응답은 @Serializable 클래스가 아니라 JsonObject로 직접 읽으므로 모델 보존 규칙이 필요 없다.

# 크래시 리포트에서 줄 번호를 볼 수 있게 남긴다
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
