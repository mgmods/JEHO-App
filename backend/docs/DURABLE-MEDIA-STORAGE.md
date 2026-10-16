# Durable media storage for JEHO-OWN / MG-JEHO

## Why this is required

The Blitz container filesystem is not durable across replacement/redeploy/sleep cycles. Production uploads must be written to Supabase Storage; the local `uploads/` directory is only a temporary staging area.

The upload service now rejects production uploads with HTTP 503 when Supabase Storage is not configured, instead of returning a URL that can disappear on restart. Local uploads remain available for development.

## Configure the dedicated JEHO-OWN Supabase project

Use the dedicated JEHO-OWN project only. Do not point these settings at the legacy `JEHOO` project.

Set these server-side environment variables in Blitz for the backend service:

- `SUPABASE_URL`: the project API URL, e.g. `https://<project-ref>.supabase.co`.
- `SUPABASE_STORAGE_BUCKET=jeho-own-uploads`.
- `SUPABASE_SERVICE_ROLE_KEY`: the project's server-side service role key, or use the supported `SUPABASE_SECRET_KEY` / `SUPABASE_STORAGE_KEY` variable instead.

Never put the server-side key in the Android app, Vite/public dashboard variables, source control, logs, or chat.

Create a Storage bucket named `jeho-own-uploads` in that same project. A private bucket is supported because file reads are proxied through the backend using the server-side key. The current `/uploads/:filename` route does not require user authentication, matching the app's existing media URL behavior. Set the bucket's maximum file size to at least 40 MiB if uploads of that size are required. The backend accepts image, video, SVGA, audio, PDF and JSON files within its configured size limit.

## Verification checklist

1. Confirm the backend startup log says `Supabase Storage configured` and the expected bucket name; it must not print any secret.
2. Upload a test image from the dashboard and another from the Android app.
3. Confirm each returned `/uploads/<filename>` URL can be opened through the backend.
4. Restart/redeploy the backend, then open the same two URLs again. Both must still return the same files.
5. Test tab icons, splash/banner images, profile avatars, room covers, and store assets because those features may use different settings fields but should resolve uploaded media through the same durable upload service.
6. Confirm failed Storage uploads are shown as failures and no successful URL is saved for them.

## Existing media and migration

This change does not delete existing media or modify database records. Existing files already in Supabase remain available. Files that only exist in a running container's local `uploads/` directory must be copied to the dedicated bucket before that container is replaced; keep the old service alive until this copy and URL verification are complete. If a local file has already disappeared, it cannot be reconstructed from a database URL alone.

## Operational notes

- The API continues returning stable same-origin `/uploads/<filename>` paths so existing app/dashboard references do not need to change to direct Storage URLs.
- The backend uses the server-side key to upload, read, and delete objects. Keep the bucket private unless a separate public-delivery design is deliberately adopted.
- No database migration is needed for this storage change.
- Audio/ZEGO/RTC configuration is unrelated and must remain unchanged.
