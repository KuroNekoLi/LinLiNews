# JobRunr Function0 問題解決方案

## 問題描述

當在 Spring Boot 和 Kotlin 中使用 JobRunr 時，您可能會遇到以下錯誤：

```
org.springframework.beans.factory.NoSuchBeanDefinitionException: 
No qualifying bean of type 'kotlin.jvm.functions.Function0<?>' available
```

此錯誤發生在以下情況：

1. **Lambda 表達式**或**函數參照**被提交為背景作業
2. JobRunr 嘗試將 `kotlin.jvm.functions.Function0` 解析為 Spring bean
3. Spring IoC 容器無法找到 Kotlin 函數類型的 bean 定義

## 根本原因

問題發生是因為與 Spring 整合的 JobRunr 期望所有背景作業都是可以通過 `ApplicationContext` 解析的 **Spring 管理的 bean**
。當您提交 Kotlin lambda 表達式時，例如：

```kotlin
// ❌ 錯誤 - 這會導致 Function0 錯誤
jobScheduler.enqueue { someService.doWork() }

// ❌ 錯誤 - 函數參照也會導致問題
jobScheduler.enqueue(::someFunction)
```

JobRunr 將這些序列化為 `kotlin.jvm.functions.Function0.invoke()` 作業，但 Spring 容器不知道如何建立這些函數類型的實例。

## 解決方案

### 1. JobRunr 配置

建立適當的 JobRunr 配置，確保正確的 Spring IoC 整合：

```kotlin
// src/main/kotlin/com/linli/news/config/JobRunrConfiguration.kt
@Configuration
class JobRunrConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun jobActivator(applicationContext: ApplicationContext): JobActivator = 
        object : JobActivator {
            override fun <T> activateJob(type: Class<T>): T = applicationContext.getBean(type)
        }
}
```

### 2. 背景作業服務

建立專門用於背景作業的服務，使用**方法參照**而不是 lambda 表達式：

```kotlin
// src/main/kotlin/com/linli/news/service/BackgroundJobService.kt
@Service
class BackgroundJobService(
    private val feedImportService: FeedImportService,
    private val jobScheduler: JobScheduler
) {

    // ✅ 正確 - 類型安全的方法參照
    fun enqueueBbcNewsImport(rssUrl: String, sourceName: String, section: String): JobId {
        return jobScheduler.enqueue<BackgroundJobService> { service ->
            service.executeBbcNewsImport(rssUrl, sourceName, section)
        }
    }

    @Job(name = "從 RSS 匯入 BBC 新聞: %0", retries = 3)
    fun executeBbcNewsImport(rssUrl: String, sourceName: String, section: String) {
        try {
            val result = feedImportService.importFromFeed(rssUrl, sourceName, section)
            println("成功從 $sourceName/$section 匯入 $result 篇文章")
        } catch (e: Exception) {
            println("從 $sourceName/$section 匯入失敗: ${e.message}")
            throw e // 重新拋出異常以便 JobRunr 重試機制處理
        }
    }
}
```

### 3. 控制器使用

在控制器中使用該服務：

```kotlin
@RestController
@RequestMapping("/api/background-jobs")
class BackgroundJobController(
    private val backgroundJobService: BackgroundJobService
) {
    
    @PostMapping("/test-bbc-main")
    fun testBbcMain(): ResponseEntity<JobResponse> {
        val jobId = backgroundJobService.enqueueBbcNewsImport(
            "https://feeds.bbci.co.uk/news/rss.xml",
            "BBC",
            "news"
        )
        
        return ResponseEntity.ok(
            JobResponse(
                jobId = jobId.toString(),
                message = "測試 BBC 主要新聞匯入作業已提交",
                status = "ENQUEUED"
            )
        )
    }
}
```

## 清理現有的失敗作業

如果您的資料庫中有現有的失敗 Function0 作業，請清理它們：

```sql
-- 檢查失敗的 Function0 作業
SELECT jobSignature, state, createdAt 
FROM jobrunr_jobs 
WHERE state = 'FAILED' 
AND jobSignature LIKE '%kotlin.jvm.functions.Function0%';

-- 刪除失敗的 Function0 作業
DELETE FROM jobrunr_jobs 
WHERE state = 'FAILED' 
AND jobSignature LIKE '%kotlin.jvm.functions.Function0%';
```

## 最佳實踐

### ✅ 應該做：

- 使用 `jobScheduler.enqueue<YourService>` 的**類型安全方法參照**
- 在**Spring 管理的服務**中定義作業方法（`@Service`、`@Component`）
- 使用 `@Job` 註解以獲得更好的作業命名和重試配置
- 確保您的作業方法可以被 Spring 的 IoC 容器解析

### ❌ 不應該做：

- 直接提交 lambda 表達式：`{ someService.doWork() }`
- 使用 Kotlin 函數參照：`::someFunction`
- 提交未由 Spring beans 支援的作業
- 不一致地混合 JobRunr 作業提交模式

## 測試解決方案

1. 啟動您的應用程式
2. 透過 HTTP 提交測試作業：
   ```bash
   curl -X POST http://localhost:8081/api/background-jobs/test-bbc-main \
        -H "Content-Type: application/json"
   ```
3. 檢查 JobRunr 儀表板（如果已啟用）或資料庫以確認作業成功執行

## 所需依賴

確保您有正確的 JobRunr 依賴：

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.jobrunr:jobrunr-spring-boot-3-starter:7.3.0")
    // ... 其他依賴
}
```

## 應用程式配置

確保在 `application.yml` 中正確配置 JobRunr：

```yaml
org:
  jobrunr:
    job-scheduler:
      enabled: true
    background-job-server:
      enabled: true
    dashboard:
      enabled: false  # 如果您想要儀表板，請設為 true
```

此解決方案確保您的背景作業與 Spring 的 IoC 容器正確整合，並可以由 JobRunr 的背景作業伺服器成功執行。