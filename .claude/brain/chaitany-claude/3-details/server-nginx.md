# Tier 3 · The server and nginx

**Public file.** Where the server is, how to log in, what else runs on it and the names of the
files that were changed there are in `site/deploy.env` and `private/server.md`. Neither is
committed; do not copy anything from them into this file. Use `ssh -o BatchMode=yes` so that a
problem fails instead of prompting.

## What matters about the machine
- A Linux VM of the owner's with nginx from the distribution's packages. The login user has sudo.
- It is **shared**: several unrelated sites and services of his run there, and the domain's main
  site is one of them. Focus owns exactly two things on it: its own web directory and one nginx
  snippet. Everything else is out of scope, even when it looks improvable: tell the owner instead.
- No `rsync`. Upload with `tar | ssh tar -x` (on macOS: `COPYFILE_DISABLE=1 tar --no-xattrs`,
  otherwise resource-fork files end up on the server).
- The stock `mime.types` does not know `.apk`; the snippet sets the type itself.
- TLS for the domain is handled by certbot. Parts of the domain's server block are marked as
  managed by it: leave those lines alone.

## What Focus added
- Its own web directory (directories 755, files 644, owned by the login user): the page, the
  APK(s), sitemap, robots.txt, IndexNow key file.
- One snippet, identical to `site/nginx-focusapp.conf` in the repo.
- **One line** in the domain's existing server block, after its `location /` block:
  `include snippets/focusapp.conf;`. A timestamped backup of that file was made first, and the
  snippet is backed up before each replacement.

## The snippet, and why each part is there
- `location = /focusapp { return 301 /focusapp/; }`
- `location ^~ /focusapp/ { root …; … }`: `^~` beats the server's regex locations; `root`
  (not `alias`, which misbehaves with `try_files`).
- `add_header` in a location **replaces** inherited headers, so nosniff / frame options are
  repeated, plus `Referrer-Policy: no-referrer`, `Cache-Control: no-cache` and the CSP
  `default-src 'none'; style-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none';
  frame-ancestors 'none'`. (JSON-LD data blocks are not executed and raise no violation.)
- nested `location ~* \.apk$`: `types { }` + `default_type application/vnd.android.package-archive`
  + `Content-Disposition: attachment`.
- `location ~ /\. { deny all; }`
- `location = /robots.txt { … }`: the domain had none; ours is `Allow: /` plus the `Sitemap:`
  line. If the main site ever gets its own robots.txt, delete this block and keep the Sitemap
  line there.

## Procedure for any change
```bash
sudo cp -p FILE FILE.bak-$(date +%Y%m%d-%H%M%S)
# edit / install
sudo nginx -t && sudo systemctl reload nginx || { restore the backup; sudo nginx -t && sudo systemctl reload nginx; }
```
Then verify from outside: routes and MIME types, headers, served APK sha256 == built == the one
printed on the page, **and `https://how2me.me/` still 200**. `site/deploy.sh` does the file upload
+ checksum check; it never touches nginx.

## Things noticed on the server that are not Focus's
Written down in `private/server.md`, reported to the owner, not changed.
