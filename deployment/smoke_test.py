"""Exercise the real deployed JAR against disposable data; no fake application responses."""
import argparse
import datetime as dt
import json
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


class SmokeFailure(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        # Treat redirects as contract failures; never forward a bearer to another host.
        return None


class Client:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.checks = []
        self.opener = urllib.request.build_opener(NoRedirect())

    def check(self, condition, label):
        if not condition:
            raise SmokeFailure(label)
        self.checks.append({"check": label, "status": "PASS"})
        print("PASS: " + label)

    def request(self, method, path, expected=200, body=None, token=None, as_json=True):
        headers = {"Accept": "application/json" if as_json else "*/*"}
        if token:
            headers["Authorization"] = "Bearer " + token
        payload = None
        if body is not None:
            payload = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base_url + path, payload, headers, method=method)
        try:
            with self.opener.open(request, timeout=35) as response:
                status, content = response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            status, content = error.code, error.read().decode("utf-8")
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            # Never retry writes: the server might already have committed them.
            raise SmokeFailure(f"{method} {path}: network failure; write outcomes may be uncertain") from error
        self.check(status == expected, f"{method} {path}: HTTP {expected}")
        if not content or not as_json:
            return content
        try:
            return json.loads(content)
        except json.JSONDecodeError as error:
            raise SmokeFailure(f"{method} {path}: expected valid JSON") from error

    def wait_for_health(self, seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                with self.opener.open(self.base_url + "/api/health", timeout=10) as response:
                    if response.status == 200 and json.load(response) == {"status": "UP"}:
                        self.check(True, "Public health became ready")
                        return
            except (urllib.error.URLError, TimeoutError, OSError, ValueError):
                pass
            time.sleep(min(3, max(0, deadline - time.monotonic())))
        raise SmokeFailure("Public health did not become ready before the startup deadline")


def run(client, wait_seconds):
    client.wait_for_health(wait_seconds)
    client.check(client.request("GET", "/api/health") == {"status": "UP"}, "Health JSON contract")
    client.check("Reality" in client.request("GET", "/", as_json=False), "Packaged web index")
    for path in ("/css/styles.css", "/js/app.js", "/js/api.js", "/favicon.svg"):
        client.check(bool(client.request("GET", path, as_json=False)), "Packaged static resource " + path)
    unauthorized = client.request("GET", "/activities", expected=401)
    client.check(set(("timestamp", "status", "error", "message", "path")).issubset(unauthorized),
                 "Structured unauthorized response")

    suffix = secrets.token_hex(6)
    password = "Reality-ci-" + secrets.token_urlsafe(16)
    credentials = [{"username": f"ci_{letter}_{suffix}", "displayName": f"CI user {letter}",
                    "password": password} for letter in ("a", "b")]
    registration = [client.request("POST", "/api/auth/register", expected=201, body=value)
                    for value in credentials]
    client.check(registration[0]["user"]["id"] != registration[1]["user"]["id"], "Server generated distinct user IDs")
    tokens = []
    for value, registered in zip(credentials, registration):
        logged_in = client.request("POST", "/api/auth/login", body={"username": value["username"], "password": password})
        client.check(logged_in["user"]["id"] == registered["user"]["id"], "Login preserves server user identity")
        client.check(isinstance(logged_in.get("expiresAt"), str), "Login includes server token expiration")
        tokens.append(logged_in["token"])
        me = client.request("GET", "/api/auth/me", token=tokens[-1])
        client.check(me["username"] == value["username"], "Bearer token resolves the correct account")
    a, b = tokens
    client.request("POST", "/api/auth/register", expected=409, body=credentials[0])
    client.request("POST", "/api/auth/login", expected=401,
                   body={"username": credentials[0]["username"], "password": "Definitely-wrong-password"})

    # Asia/Kolkata has no daylight saving for contemporary session dates.
    day = dt.datetime.now(dt.timezone(dt.timedelta(hours=5, minutes=30))).date().isoformat()
    month = day[:7]
    weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    activity_body = {"name": "CI isolation " + suffix, "minimumDuration": 1, "category": "PERSONAL",
                     "startDate": day, "scheduledDays": weekdays}
    activity = client.request("POST", "/activities", expected=201, body=activity_body, token=a)
    activity_id = activity["id"]
    activity_path = f"/activities/{activity_id}"
    client.check(activity_id > 0, "Activity uses server generated ID")
    client.check(client.request("GET", activity_path, token=a)["name"] == activity_body["name"], "Created activity is persisted")
    activity_body["name"] += " edited"
    client.check(client.request("PUT", activity_path, body=activity_body, token=a)["name"] == activity_body["name"], "Activity edit persists")
    client.check(any(row["id"] == activity_id for row in client.request("GET", "/activities", token=a)), "Owner sees created activity")
    client.check(all(row["id"] != activity_id for row in client.request("GET", "/activities", token=b)), "Other account cannot list activity")
    for method, body in (("GET", None), ("PUT", activity_body), ("DELETE", None)):
        client.request(method, activity_path, expected=404, body=body, token=b)
    for path in (f"/api/sessions/activity/{activity_id}",
                 f"/api/sessions/activity/{activity_id}/date/{day}",
                 f"/api/daily-progress/activity/{activity_id}/date/{day}",
                 f"/api/streaks/activity/{activity_id}",
                 f"/api/reports/activity/{activity_id}/month/{month}"):
        client.request("GET", path, expected=404, token=b)
    client.request("POST", "/api/sessions/start", expected=404, body={"activityId": activity_id}, token=b)

    session = client.request("POST", "/api/sessions/start", expected=201, body={"activityId": activity_id}, token=a)
    session_id = session["id"]
    session_path = f"/api/sessions/{session_id}"
    client.check(session["activityId"] == activity_id and session["endTime"] is None, "Session starts with server timestamps")
    client.check(client.request("GET", session_path, token=a)["id"] == session_id, "Owner can read session")
    client.check(any(row["id"] == session_id for row in client.request("GET", "/api/sessions", token=a)), "Owner can list session")
    client.check(all(row["id"] != session_id for row in client.request("GET", "/api/sessions", token=b)), "Other account cannot list session")
    for method, path, body in (("GET", session_path, None), ("DELETE", session_path, None),
                               ("PUT", session_path + "/stop", None),
                               ("POST", session_path + "/break", {"description": "foreign break"}),
                               ("PUT", session_path + "/resume", None),
                               ("GET", session_path + "/breaks", None)):
        client.request(method, path, expected=404, body=body, token=b)
    started_break = client.request("POST", session_path + "/break", expected=201,
                                   body={"description": "CI break"}, token=a)
    client.check(started_break["sessionId"] == session_id and started_break["endTime"] is None, "Break is persisted in running state")
    client.request("POST", session_path + "/break", expected=400, body={}, token=a)
    client.check(len(client.request("GET", session_path + "/breaks", token=a)) == 1, "Duplicate break was not created")
    resumed = client.request("PUT", session_path + "/resume", token=a)
    client.check(resumed["endTime"] is not None, "Resume closes server break")
    stopped = client.request("PUT", session_path + "/stop", token=a)
    client.check(stopped["endTime"] is not None and stopped["duration"] >= 0, "Stop stores completed duration")
    client.request("PUT", session_path + "/stop", expected=400, token=a)
    history = client.request("GET", f"/api/sessions/activity/{activity_id}", token=a)
    client.check(any(row["id"] == session_id for row in history), "Activity session history includes ended session")
    daily_sessions = client.request("GET", f"/api/sessions/activity/{activity_id}/date/{day}", token=a)
    client.check(any(row["id"] == session_id for row in daily_sessions["sessions"]), "Daily session history uses real completed session")
    progress = client.request("GET", f"/api/daily-progress/activity/{activity_id}/date/{day}", token=a)
    client.check(progress["minimumDuration"] == 1 and progress["totalDuration"] >= 0, "Daily progress preserves target minutes and recorded seconds")
    streak = client.request("GET", f"/api/streaks/activity/{activity_id}", token=a)
    client.check(streak["activityId"] == activity_id, "Streak belongs to owner activity")
    report = client.request("GET", f"/api/reports/activity/{activity_id}/month/{month}", token=a)
    expected_report = {"activityId", "activityName", "year", "month", "totalDuration", "totalSessions", "scheduledDays",
                       "completedDays", "missedDays", "completionPercentage", "currentStreak", "longestStreak"}
    client.check(expected_report.issubset(report) and report["totalSessions"] == 1, "Monthly report returns all twelve fields and real history")

    other_body = dict(activity_body, name="CI second account " + suffix)
    other = client.request("POST", "/activities", expected=201, body=other_body, token=b)
    client.check(all(row["id"] != other["id"] for row in client.request("GET", "/activities", token=a)), "Isolation works in both directions")
    client.request("DELETE", session_path, expected=204, token=a)
    client.request("GET", session_path, expected=404, token=a)
    client.request("DELETE", activity_path, token=a, as_json=False)
    client.request("GET", activity_path, expected=404, token=a)
    client.request("DELETE", f"/activities/{other['id']}", token=b, as_json=False)
    for token in tokens + [item["token"] for item in registration]:
        client.request("POST", "/api/auth/logout", expected=204, token=token)
        client.request("GET", "/api/auth/me", expected=401, token=token)
    client.check(True, "Test sessions deleted, activities deactivated, and all test tokens revoked")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--wait-seconds", type=int, default=180)
    parser.add_argument("--allow-http", action="store_true")
    parser.add_argument("--allow-create-test-data", action="store_true",
                        help="Required: creates two accounts; successful cleanup retains deactivated activities/accounts")
    parser.add_argument("--output", type=Path, default=Path("deployment-smoke-results.json"))
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        parser.error("Use the backend origin URL without credentials, path, query, or fragment")
    if parsed.scheme == "http" and not args.allow_http:
        parser.error("Use HTTPS, or explicitly allow HTTP for a local disposable test")
    if not args.allow_create_test_data:
        parser.error("Pass --allow-create-test-data only for a server where creating disposable test accounts is authorized")
    if args.wait_seconds < 1 or args.wait_seconds > 600:
        parser.error("Startup wait must be between 1 and 600 seconds")
    client = Client(args.base_url)
    passed, failure = False, None
    try:
        run(client, args.wait_seconds)
        passed = True
    except (SmokeFailure, KeyError, TypeError, ValueError) as error:
        failure = str(error) if isinstance(error, SmokeFailure) else "Unexpected JSON response structure"
        print("FAIL: " + failure, file=sys.stderr)
    finally:
        args.output.write_text(json.dumps({"passed": passed, "checks": client.checks, "failure": failure}, indent=2) + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
