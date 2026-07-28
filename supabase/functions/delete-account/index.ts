import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { serve } from "https://deno.land/std@0.177.0/http/server.ts"

serve(async (req) => {
  const authHeader = req.headers.get("Authorization")
  if (!authHeader) return new Response("未授权", { status: 401 })

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  )

  const { data: { user }, error: userErr } = await supabase.auth.getUser(
    authHeader.replace("Bearer ", "")
  )
  if (userErr || !user) return new Response("用户不存在", { status: 404 })

  const { error } = await supabase.auth.admin.deleteUser(user.id)
  if (error) return new Response(error.message, { status: 500 })

  return new Response("ok", { status: 200 })
})
