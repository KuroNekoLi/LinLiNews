#!/bin/bash
# 為已有 embedding 的文章配對 tags 和專題

cd /Users/linli/dev_spring-boot/LinLiNews

OPENAI_API_KEY='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' \
SPRING_PROFILES_ACTIVE=local \
SERVER_PORT=8081 \
./gradlew bootRun --no-daemon \
  --args='--embedding.batch.enabled=true --embedding.batch.limit=0 --embedding.batch.force=false --embedding.batch.assign-tags=true --embedding.batch.assign-collections=true --server.port=8081' \
  2>&1 | tee /tmp/assign_log.txt
