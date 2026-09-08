-- =============================================================================
-- 小棚养虾 · Supabase 行级安全（RLS）策略脚本
-- 在 Supabase Dashboard -> SQL Editor 中整体执行即可。
-- 重复执行是幂等的（先 DROP 后 CREATE）。
--
-- 涉及数据表：profiles / questions / answers / answer_votes / app_checksums
-- 涉及存储桶：qa-images
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) profiles（用户资料：id, nickname）
--    读取：公开（问答列表要显示昵称）；写入：仅本人
-- -----------------------------------------------------------------------------
alter table public.profiles enable row level security;

drop policy if exists "Profiles are viewable by everyone" on public.profiles;
create policy "Profiles are viewable by everyone"
  on public.profiles for select
  using (true);

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

drop policy if exists "Users can update own profile" on public.profiles;
create policy "Users can update own profile"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- 如需用户自己删除资料（一般不需要），取消下面注释
-- drop policy if exists "Users can delete own profile" on public.profiles;
-- create policy "Users can delete own profile"
--   on public.profiles for delete
--   using (auth.uid() = id);


-- -----------------------------------------------------------------------------
-- 2) questions（问题：user_id, title, content, is_resolved, ...）
--    读取：公开；发布/删除：仅本人；解决状态：仅本人
-- -----------------------------------------------------------------------------
alter table public.questions enable row level security;

drop policy if exists "Questions are viewable by everyone" on public.questions;
create policy "Questions are viewable by everyone"
  on public.questions for select
  using (true);

drop policy if exists "Users can create questions" on public.questions;
create policy "Users can create questions"
  on public.questions for insert
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own questions" on public.questions;
create policy "Users can update own questions"
  on public.questions for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can delete own questions" on public.questions;
create policy "Users can delete own questions"
  on public.questions for delete
  using (auth.uid() = user_id);


-- -----------------------------------------------------------------------------
-- 3) answers（回答：question_id, user_id, content, is_accepted, ...）
--    读取：公开；发布/删除：仅本人；
--    采纳（is_accepted）与标记已解决：问题作者可操作
-- -----------------------------------------------------------------------------
alter table public.answers enable row level security;

drop policy if exists "Answers are viewable by everyone" on public.answers;
create policy "Answers are viewable by everyone"
  on public.answers for select
  using (true);

drop policy if exists "Users can create answers" on public.answers;
create policy "Users can create answers"
  on public.answers for insert
  with check (auth.uid() = user_id);

-- 回答作者可编辑/删除自己的回答
drop policy if exists "Users can update own answers" on public.answers;
create policy "Users can update own answers"
  on public.answers for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can delete own answers" on public.answers;
create policy "Users can delete own answers"
  on public.answers for delete
  using (auth.uid() = user_id OR
         exists (
           select 1 from public.questions q
           where q.id = answers.question_id and q.user_id = auth.uid()
         ));

-- 问题作者可采纳回答（将某回答标记为正确答案）
drop policy if exists "Question authors can accept answers" on public.answers;
create policy "Question authors can accept answers"
  on public.answers for update
  using (
    exists (
      select 1 from public.questions q
      where q.id = answers.question_id and q.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.questions q
      where q.id = answers.question_id and q.user_id = auth.uid()
    )
  );


-- -----------------------------------------------------------------------------
-- 4) answer_votes（答案投票：answer_id, user_id, vote_type）
--    读取：公开；投票/取消：仅本人（含 upsert）
-- -----------------------------------------------------------------------------
alter table public.answer_votes enable row level security;

drop policy if exists "Votes are viewable by everyone" on public.answer_votes;
create policy "Votes are viewable by everyone"
  on public.answer_votes for select
  using (true);

drop policy if exists "Users can create own votes" on public.answer_votes;
create policy "Users can create own votes"
  on public.answer_votes for insert
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own votes" on public.answer_votes;
create policy "Users can update own votes"
  on public.answer_votes for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can delete own votes" on public.answer_votes;
create policy "Users can delete own votes"
  on public.answer_votes for delete
  using (auth.uid() = user_id);


-- -----------------------------------------------------------------------------
-- 5) app_checksums（应用构建校验：package_name, version_code, allowed_fingerprint）
--    只读（匿名 + 登录用户均可读，用于完整性校验）。
--    写入仅允许 service_role（Supabase 后台），普通用户不可写。
-- -----------------------------------------------------------------------------
alter table public.app_checksums enable row level security;

drop policy if exists "App checksums are viewable" on public.app_checksums;
create policy "App checksums are viewable"
  on public.app_checksums for select
  using (true);


-- -----------------------------------------------------------------------------
-- 6) 存储桶 qa-images（问答图片）
--    公开可读；仅登录用户可上传（属于上传者本人）
-- -----------------------------------------------------------------------------
drop policy if exists "QA images are publicly viewable" on storage.objects;
create policy "QA images are publicly viewable"
  on storage.objects for select
  using (bucket_id = 'qa-images');

drop policy if exists "Authenticated users can upload QA images" on storage.objects;
create policy "Authenticated users can upload QA images"
  on storage.objects for insert
  with check (
    bucket_id = 'qa-images'
    and auth.role() = 'authenticated'
    -- 可选：强制上传者归属，把文件路径改为 {userId}/xxx 后放开下面注释
    -- and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "Users can update own QA images" on storage.objects;
create policy "Users can update own QA images"
  on storage.objects for update
  using (bucket_id = 'qa-images' and auth.uid()::text = (storage.foldername(name))[1])
  with check (bucket_id = 'qa-images' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "Users can delete own QA images" on storage.objects;
create policy "Users can delete own QA images"
  on storage.objects for delete
  using (bucket_id = 'qa-images' and auth.uid()::text = (storage.foldername(name))[1]);


-- =============================================================================
-- 附：验证是否已启用（应全部为 true）
-- =============================================================================
select tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in ('profiles','questions','answers','answer_votes','app_checksums');
