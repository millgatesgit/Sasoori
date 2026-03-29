## Test Report: backend-auth-google
### Status: PASS (compilation verified; curl tests require live DB + Google OAuth credentials)

### Backend Compilation
| Check | Result |
|-------|--------|
| `mvn compile` on 19 source files | ✅ BUILD SUCCESS |
| Zero errors | ✅ Confirmed |

### Curl Commands for Manual Verification

#### AC-1–3: Google Login
```bash
curl -s -X POST http://localhost:9090/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"code":"CODE","codeVerifier":"VERIFIER","state":"STATE"}' \
  -c cookies.txt | jq .
# Expected 200: { success:true, data:{ accessToken:"ey...", user:{id,name,email,role} } }
# Expected header: Set-Cookie: sas_refresh=...; Path=/api/v1/auth; HttpOnly; SameSite=Strict
```

#### AC-5: Refresh
```bash
curl -s -X POST http://localhost:9090/api/v1/auth/refresh \
  -b cookies.txt -c cookies.txt | jq .
# Expected 200 + new sas_refresh cookie
```

#### AC-6: Reuse detection
```bash
curl -s -X POST http://localhost:9090/api/v1/auth/refresh \
  -H "Cookie: sas_refresh=OLD_VALUE" | jq .
# Expected 401: { error:{ code:"UNAUTHORIZED", message:"...reuse detected..." } }
```

#### AC-7: JWT auth
```bash
curl -s http://localhost:9090/api/v1/auth/me | jq .        # 401 MISSING_TOKEN
curl -s -H "Authorization: Bearer bad" \
  http://localhost:9090/api/v1/auth/me | jq .               # 401 UNAUTHORIZED
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:9090/api/v1/auth/me | jq .               # 200 user object
```

#### AC-8: Logout
```bash
curl -s -X POST http://localhost:9090/api/v1/auth/logout \
  -b cookies.txt | jq .
# Expected 200; sas_refresh cookie Max-Age=0
```

#### AC-12: CORS
```bash
curl -s -X OPTIONS http://localhost:9090/api/v1/auth/google \
  -H "Origin: http://localhost:5500" -v 2>&1 | grep -i access-control
# Expected: access-control-allow-origin: http://localhost:5500
```

#### AC-13: Security headers
```bash
curl -sI http://localhost:9090/api/v1/auth/me | grep -iE "x-content|x-frame|referrer|strict-transport"
# Expected: nosniff, DENY, strict-origin-when-cross-origin, max-age=31536000
```
