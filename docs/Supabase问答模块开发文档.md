# Supabase 问答模块开发文档

## 一、概述

基于 Supabase 构建一个社区问答功能，用户可发布问题、浏览问题列表、查看详情并回答。支持图片附件（压缩后上传）、30 天自动清理过期数据。

### 功能列表
- [ ] 发布问题（标题 + 内容 + 可附带图片）
- [ ] 问题列表（按时间倒序，分页加载）
- [ ] 问题详情（查看问题 + 所有回答）
- [ ] 回答问题
- [ ] 采纳最佳答案（问题发起人）
- [ ] 图片压缩上传
- [ ] 30 天自动删除过期问题

---

## 二、数据表设计

在 Supabase SQL Editor 中执行以下 SQL：

```sql
-- 启用 UUID 扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 问题表
CREATE TABLE questions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    image_urls TEXT[] DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 回答表
CREATE TABLE answers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    image_urls TEXT[] DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX idx_questions_created_at ON questions(created_at DESC);
CREATE INDEX idx_questions_user_id ON questions(user_id);
CREATE INDEX idx_answers_question_id ON answers(question_id);

-- 30 天后自动删除的清理函数（由 pg_cron 或 Edge Function 调用）
CREATE OR REPLACE FUNCTION cleanup_old_questions()
RETURNS void AS $$
BEGIN
    DELETE FROM answers
    WHERE question_id IN (
        SELECT id FROM questions
        WHERE created_at < NOW() - INTERVAL '30 days'
    );
    DELETE FROM questions
    WHERE created_at < NOW() - INTERVAL '30 days';
END;
$$ LANGUAGE plpgsql;
```

### Row Level Security (RLS)

```sql
-- 启用 RLS
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE answers ENABLE ROW LEVEL SECURITY;

-- questions: 所有人可读
CREATE POLICY "questions_select" ON questions
    FOR SELECT USING (true);

-- questions: 自己可增删改
CREATE POLICY "questions_insert" ON questions
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "questions_update" ON questions
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "questions_delete" ON questions
    FOR DELETE USING (auth.uid() = user_id);

-- answers: 所有人可读
CREATE POLICY "answers_select" ON answers
    FOR SELECT USING (true);

-- answers: 自己可增，问题作者可删
CREATE POLICY "answers_insert" ON answers
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "answers_update" ON answers
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "answers_delete" ON answers
    FOR DELETE USING (
        auth.uid() = user_id OR
        auth.uid() IN (SELECT user_id FROM questions WHERE id = answers.question_id)
    );
```

---

## 三、Android 端架构

### 3.1 包结构

```
com.shrimpfarm.app.qa/
├── QaListActivity.java          // 问题列表页
├── QaDetailActivity.java        // 问题详情 + 回答列表
├── QaPostActivity.java          // 发布问题页
├── adapter/
│   ├── QaListAdapter.java       // 问题列表适配器
│   └── AnswerAdapter.java       // 回答列表适配器
├── model/
│   ├── Question.java            // 问题数据模型
│   └── Answer.java              // 回答数据模型
└── api/
    └── QaApi.java               // Supabase REST API 封装
```

### 3.2 模型类

#### Question.java

```java
package com.shrimpfarm.app.qa.model;

import java.util.List;

public class Question {
    public long id;
    public String userId;
    public String title;
    public String content;
    public boolean isResolved;
    public List<String> imageUrls;
    public String createdAt;
    public String updatedAt;
    // 联表查询时携带的 answers（详情页使用）
    public List<Answer> answers;
}
```

#### Answer.java

```java
package com.shrimpfarm.app.qa.model;

import java.util.List;

public class Answer {
    public long id;
    public long questionId;
    public String userId;
    public String content;
    public boolean isAccepted;
    public List<String> imageUrls;
    public String createdAt;
    public String updatedAt;
}
```

### 3.3 API 层

#### QaApi.java

封装所有 Supabase REST 调用，使用已有的 `OkHttpClient` 单例（`HttpClientSingleton`）和 `SupabaseAuthManager` 获取 token。

| 方法 | HTTP | 端点 | 说明 |
|------|------|------|------|
| `getQuestions(page, pageSize)` | GET | `/rest/v1/questions?select=*,user:user_id(nickname)&order=created_at.desc&limit={pageSize}&offset={offset}` | 分页获取问题列表（联表查用户昵称） |
| `getQuestionDetail(id)` | GET | `/rest/v1/questions?id=eq.{id}&select=*,answers(*),user:user_id(nickname)` | 获取问题详情 + 所有回答 + 用户昵称 |
| `postQuestion(Question)` | POST | `/rest/v1/questions` | 发布问题 |
| `deleteQuestion(id)` | DELETE | `/rest/v1/questions?id=eq.{id}` | 删除问题 |
| `postAnswer(Answer)` | POST | `/rest/v1/answers` | 回答问题 |
| `acceptAnswer(questionId, answerId)` | PATCH | `/rest/v1/answers?id=eq.{answerId}` | 采纳回答 |
| `markResolved(questionId)` | PATCH | `/rest/v1/questions?id=eq.{id}` | 标记问题已解决 |

Supabase REST API 公共请求头：
```
apikey: {SUPABASE_ANON_KEY}
Authorization: Bearer {access_token}
Content-Type: application/json
Prefer: return=representation
```

从 `SupabaseAuthManager` 获取当前 token：
```java
String token = SupabaseAuthManager.getInstance(this).getAccessToken();
```

### 3.4 图片处理

**压缩上传流程：**

1. 用户从相册选择图片（`ActivityResultContracts.GetContent` 或 `PickVisualMedia`）
2. Android 端用 BitmapFactory 解码后压缩（最大宽高 1920px，质量 80%）
3. 上传到 Supabase Storage `qa-images` 桶
4. 将返回的 URL 存入 `image_urls` 字段

**压缩工具方法（可放到现有的 utils 包）：**

```java
// 最大宽高 1920px，质量 80%
public static File compressImage(Context context, Uri uri, int maxWidth, int maxHeight, int quality)
```

**Storage 桶：**

在 Supabase Dashboard → Storage 创建 `qa-images` 桶，RLS 策略：
```sql
-- 认证用户可上传
CREATE POLICY "qa_images_insert" ON storage.objects
    FOR INSERT WITH CHECK (
        auth.role() = 'authenticated'
        AND bucket_id = 'qa-images'
    );
-- 所有人可读取
CREATE POLICY "qa_images_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'qa-images');
```

### 3.5 30 天自动清理

**方案一：Supabase Edge Function（推荐）**

创建一个 Edge Function `cleanup-qa`，用 `Deno.cron` 定时每天执行：

```ts
Deno.cron("Cleanup old QA", "0 0 * * *", async () => {
    const { data, error } = await supabase.rpc('cleanup_old_questions');
});
```

**方案二：外部定时任务**（如果没有 Supabase Edge Function 权限）

在 Android 端用 `WorkManager` 每月执行一次清理（不推荐——如果用户不打开 App 则不会执行）。

---

## 四、集成方式（关键）

### 4.1 集成策略

Q&A 模块**不新建独立入口**，而是融入现有的"帮助建议"（`HelpActivity`）页面。

**改造 HelpActivity：**
- 保持首页 GridView 第 8 项"帮助建议"不变，仍打开 `HelpActivity`
- `HelpActivity` 从纯 WebView 改为 **Tab 容器**：
  - Tab ① "使用帮助" → 现有 WebView（内容不变）
  - Tab ② "问答社区" → Q&A 问题列表（RecyclerView）
- 不需要新增 GridView 项，不改 MainActivity，不改底部导航

### 4.2 包结构

```
com.shrimpfarm.app/
├── HelpActivity.java            ← 改造：WebView → TabLayout 容器
├── qa/
│   ├── QaDetailActivity.java    // 问题详情 + 回答列表（独立 Activity）
│   ├── QaPostActivity.java      // 发布问题（独立 Activity）
│   ├── adapter/
│   │   ├── QaListAdapter.java   // 问题列表适配器
│   │   └── AnswerAdapter.java   // 回答列表适配器
│   ├── model/
│   │   ├── Question.java        // 问题数据模型
│   │   └── Answer.java          // 回答数据模型
│   └── api/
│       └── QaApi.java           // Supabase REST API 封装
```

### 4.3 HelpActivity 改造方案

**布局变化（`activity_help.xml`）：**

```xml
<!-- 改造前 -->
<WebView xmlns:android="..." android:id="@+id/web_view" ... />

<!-- 改造后 -->
<LinearLayout xmlns:android="..." android:orientation="vertical">
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
    <FrameLayout android:id="@+id/container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">
        <WebView android:id="@+id/web_view" ... />         <!-- Tab 0 -->
        <androidx.recyclerview.widget.RecyclerView          <!-- Tab 1 -->
            android:id="@+id/qa_recycler_view"
            android:visibility="gone" ... />
    </FrameLayout>
    <!-- FAB 只在问答 Tab 可见 -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_post_question"
        android:visibility="gone" ... />
</LinearLayout>
```

**逻辑变化（`HelpActivity.java`）：**

```java
// Tab 切换逻辑
tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        switch (tab.getPosition()) {
            case 0: // 使用帮助
                webView.setVisibility(View.VISIBLE);
                qaRecyclerView.setVisibility(View.GONE);
                fabPostQuestion.hide();
                break;
            case 1: // 问答社区
                webView.setVisibility(View.GONE);
                qaRecyclerView.setVisibility(View.VISIBLE);
                fabPostQuestion.show();
                loadQuestions(); // 首次加载
                break;
        }
    }
});
```

### 4.4 页面导航

```
首页 GridView "帮助建议"(原位置不变)
  └─ HelpActivity (TabLayout 容器)
       ├─ Tab "使用帮助" → WebView 显示 help.html（不变）
       └─ Tab "问答社区" → RecyclerView 问题列表
            ├─ 点击问题 → startActivity(QaDetailActivity)
            └─ FAB → startActivity(QaPostActivity)
```

### 4.5 问题列表（Tab ② 内嵌的 RecyclerView）

- 顶部：TabLayout 自带标题切换，"问答社区" Tab 内显示列表
- RecyclerView 每项显示：
  - 用户昵称（从 Supabase 联表获取）
  - 问题标题（最多 1 行，省略号）
  - 内容摘要（最多 2 行）
  - 发布时间（相对时间）
  - 回答数 / 已解决标记
- 分页加载（列表滚动到底加载下一页）
- 列表为空时显示空状态提示："暂无问题，快来提第一个问题吧"

### 4.6 问题详情页 (QaDetailActivity)

独立 Activity，通过 Intent 传 `questionId`：
- 问题详情区域：标题 + 内容 + 图片 + 作者 + 时间
- 回答列表（RecyclerView）：
  - 每条回答显示内容 + 作者 + 时间
  - 采纳标记（绿色"最佳答案"角标）
  - 问题作者可点击采纳回答
- 底部固定输入区：EditText + 发送按钮

### 4.7 发布问题页 (QaPostActivity)

独立 Activity：
- 标题输入框
- 内容输入框（多行）
- 添加图片按钮（最多 9 张）
- 图片预览（可删除）
- 底部发布按钮

---

## 五、开发步骤

### Phase 1：数据库准备
1. 在 Supabase Dashboard → SQL Editor 执行建表 SQL
2. 配置 RLS 策略
3. 创建 Storage `qa-images` 桶

### Phase 2：模型 + API
1. 创建 `qa/model/Question.java`、`qa/model/Answer.java`
2. 创建 `qa/api/QaApi.java` 封装所有 HTTP 调用
3. 实现图片压缩工具方法

### Phase 3：改造 HelpActivity
1. 修改 `activity_help.xml`：WebView → TabLayout + FrameLayout(WebView + RecyclerView) + FAB
2. 重写 `HelpActivity.java`：添加 Tab 切换、Q&A 列表加载、FAB 跳转
3. 创建 `qa/adapter/QaListAdapter.java`

### Phase 4：详情 + 发布
1. 创建 `QaDetailActivity.java` + `activity_qa_detail.xml`
2. 创建 `qa/adapter/AnswerAdapter.java`
3. 创建 `QaPostActivity.java` + `activity_qa_post.xml`
4. 在 AndroidManifest.xml 注册两个新 Activity

### Phase 5：收尾
1. 30 天清理（Edge Function）
2. 图片放大查看
3. 空状态 / 加载中 / 错误提示
4. 未登录时跳转登录页

---

## 六、依赖项

不需要新增依赖，项目已有的直接用：
- `OkHttp 5.4.0` — HTTP 请求
- `Gson 2.14.0` / `org.json` — JSON 解析
- `Glide 5.0.7` — 图片加载
- `RecyclerView 1.3.2` — 列表
- `SupabaseAuthManager` — 用户认证和 token 管理
- `HttpClientSingleton` — OkHttp 单例

---

## 七、注意事项

1. **Supabase 用户联表查询**：Supabase REST API 支持 `select=user:user_id(nickname)` 语法，但需要先在 `auth.users` 表加 `nickname` 字段（`SupabaseAuthManager` 注册时已存 `nickname` 到 `user_metadata`），或创建一个视图来暴露昵称。
2. **分页**：使用 `limit` + `offset` 参数，每页 20 条。
3. **网络配置**：项目没有 `network_security_config.xml`，如果 Supabase URL 是 HTTPS 则没问题。
4. **图片选择**：minSdk 28 不支持 `PhotoPicker`（API 33+），用 `Intent.ACTION_PICK` + `ActivityResultContracts.GetContent`。
5. **Error Handling**：所有 API 调用需处理网络异常、401 token 过期等场景。
6. **TabLayout 依赖**：项目已有 `com.google.android.material:material:1.12.0`，`TabLayout` 和 `FloatingActionButton` 直接可用，无需加新依赖。
7. **Tab 切换时机**：切换到"问答社区"Tab 时再首次加载数据，不要提前加载，避免浪费流量。
8. **旋转锁定**：项目所有 Activity 已锁定竖屏，新增的 `QaDetailActivity` 和 `QaPostActivity` 也要在 `AndroidManifest.xml` 加 `android:screenOrientation="portrait"`。
