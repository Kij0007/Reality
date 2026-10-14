#!/usr/bin/env python3
"""Optional native-library-free exporter. Primary Windows setup uses Export-Reality-Localhost.ps1.

Requires Python 3.10+ and pg8000. Install: python -m pip install -r requirements-export.txt
Reads existing application.properties privately; never puts credentials in the backup or logs.
"""
from __future__ import annotations

import argparse
import datetime as dt
import getpass
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("The backend redirected an export request. Use its exact localhost address.")


def read_properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"\s*([^#!][^=]*?)\s*=\s*(.*?)\s*$", line)
        if not match:
            continue
        key, value = match.groups()
        environment = re.fullmatch(r"\$\{([^}:]+)(?::([^}]*))?\}", value)
        if environment:
            variable, fallback = environment.groups()
            value = os.environ.get(variable, fallback or "")
        result[key.strip()] = value.replace(r"\:", ":").replace(r"\=", "=")
    return result


def api_reader(base_url: str):
    parsed = urllib.parse.urlsplit(base_url)
    if parsed.hostname not in ("localhost", "127.0.0.1", "::1") or parsed.scheme not in ("http", "https") or parsed.username or parsed.password:
        raise ValueError("Use your localhost backend address without embedded credentials.")
    opener = urllib.request.build_opener(NoRedirect)
    token = os.environ.get("REALITY_EXPORT_TOKEN")
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token

    def get(path):
        request = urllib.request.Request(base_url.rstrip("/") + path, headers=headers)
        try:
            with opener.open(request, timeout=30) as response:
                contents = response.read(50 * 1024 * 1024 + 1)
                if len(contents) > 50 * 1024 * 1024:
                    raise ValueError("The server response exceeded the export safety limit.")
                return json.loads(contents)
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"The backend returned HTTP {error.code} for {path}. Check your server and, if required, REALITY_EXPORT_TOKEN.") from None
        except urllib.error.URLError:
            raise RuntimeError("Cannot reach your localhost application. Keep Spring Boot running during export.") from None

    return get


def check_identity(backup, activities, sessions, get):
    database_active = {item["id"] for item in backup["activities"] if item["active"]}
    api_active = {item["id"] for item in activities if item["active"]}
    if database_active != api_active or {item["id"] for item in backup["sessions"]} != {item["id"] for item in sessions}:
        raise ValueError("This database does not match the active activities and complete session IDs returned by localhost. No backup was written.")
    by_activity = {item["id"]: item for item in backup["activities"]}
    for activity in activities:
        saved = by_activity.get(activity["id"])
        if not saved or any(saved.get(field) != activity.get(field) for field in ("name", "minimumDuration", "category")):
            raise ValueError("Localhost activity details do not match this database. No backup was written.")
    by_session = {item["id"]: item for item in backup["sessions"]}
    for session in sessions:
        saved = by_session[session["id"]]
        if saved["activityId"] != session["activityId"] or dt.datetime.fromisoformat(saved["startTime"]) != dt.datetime.fromisoformat(session["startTime"]):
            raise ValueError("Localhost session history does not match this database. No backup was written.")
    for streak in backup["streaks"]:
        current = get(f'/api/streaks/activity/{streak["activityId"]}')
        if any(current[field] != streak[field] for field in ("currentStreak", "lastEvaluatedDate")):
            raise ValueError("The server streak does not match this snapshot. Check the source timezone and finish recording changes before export.")


def main():
    parser = argparse.ArgumentParser(description="Read-only complete Reality localhost export, including archived activity history.")
    parser.add_argument("--project", type=Path, default=Path(r"D:\Commitment_tracker\reality"))
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--output", type=Path, default=Path.home() / "Downloads" / "Reality-localhost-backup.json")
    parser.add_argument("--time-zone", default="Asia/Kolkata")
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--owner-id", type=int)
    selection.add_argument("--unowned", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    if args.output.exists() and not args.force:
        raise ValueError("The output already exists. Choose another path or use --force.")
    if args.owner_id is not None and args.owner_id <= 0:
        raise ValueError("Owner ID must be positive.")
    if not re.fullmatch(r"[A-Za-z0-9_+./-]{1,100}", args.time_zone):
        raise ValueError("Enter the source JVM timezone, such as Asia/Kolkata.")
    properties = read_properties(args.project / "src/main/resources/application.properties")
    jdbc = properties.get("spring.datasource.url", "")
    if not jdbc.startswith("jdbc:postgresql://"):
        raise ValueError("The project must configure a PostgreSQL JDBC datasource.")
    address = urllib.parse.urlsplit(jdbc.removeprefix("jdbc:"))
    if address.username or address.password:
        raise ValueError("Use separate datasource username/password properties.")
    user = properties.get("spring.datasource.username") or input("PostgreSQL username: ")
    password = properties.get("spring.datasource.password") or getpass.getpass("PostgreSQL password (hidden): ")
    get = api_reader(args.base_url)
    activities, sessions = get("/activities"), get("/api/sessions")
    try:
        import pg8000.dbapi
    except ImportError:
        raise RuntimeError("Install the optional exporter dependency: python -m pip install -r requirements-export.txt") from None
    connection = pg8000.dbapi.connect(user=user, password=password, host=address.hostname,
        port=address.port or 5432, database=urllib.parse.unquote(address.path.lstrip("/")), timeout=120)
    password = None
    connection.autocommit = True
    try:
        cursor = connection.cursor()
        cursor.execute("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
        cursor.execute("SET LOCAL statement_timeout = '90s'")
        cursor.execute("""SELECT jsonb_build_object(
            'sessionTable',to_regclass('public.session') IS NOT NULL,
            'breakTable',to_regclass('public.session_break') IS NOT NULL,
            'scheduleTable',to_regclass('public.activity_scheduled_days') IS NOT NULL,
            'owned',EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='activities' AND column_name='owner_id'))""")
        metadata = cursor.fetchone()[0]
        if not all(metadata[field] for field in ("sessionTable", "breakTable", "scheduleTable")):
            raise ValueError("This database lacks tracking or schedule tables and does not match the complete localhost application. No incomplete history will be exported.")
        if metadata["owned"] and args.owner_id is None and not args.unowned:
            raise ValueError("This backend has separate accounts. Choose --owner-id <your ID> or --unowned for the original shared history.")
        if not metadata["owned"] and (args.owner_id is not None or args.unowned):
            raise ValueError("This original database has no owner column. Omit account selection.")
        condition = "TRUE"
        if args.owner_id is not None:
            condition = f"(to_jsonb(a)->>'owner_id')::bigint = {args.owner_id}"
        elif args.unowned:
            condition = "to_jsonb(a)->>'owner_id' IS NULL"
        query = Path(__file__).with_name("reality-export.sql").read_text(encoding="utf-8")
        query = query[query.index("WITH selected_activities"):].removesuffix("COMMIT;\n").removesuffix("COMMIT;")
        query = query.replace("__OWNER_CONDITION__", condition).replace("__SOURCE_TIMEZONE__", args.time_zone)
        cursor.execute(query)
        backup = cursor.fetchone()[0]
        cursor.execute("COMMIT")
    except Exception:
        try:
            connection.cursor().execute("ROLLBACK")
        except Exception:
            pass
        raise
    finally:
        connection.close()
    check_identity(backup, activities, sessions, get)
    contents = json.dumps(backup, ensure_ascii=False, indent=2).encode("utf-8")
    if len(contents) > 20 * 1024 * 1024:
        raise ValueError("This history exceeds the Android 20 MB safety limit. No backup was written.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb" if args.force else "xb") as output:
        output.write(contents)
    print(f'Saved {len(backup["activities"])} activities including archived, {len(backup["sessions"])} sessions, {len(backup["breaks"])} breaks and {len(backup["streaks"])} streak snapshots.')
    print(f"Backup: {args.output.resolve()}")
    print("Transfer this JSON to your phone, then use Reality Offline > Settings > Backup and restore.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Never print driver connection objects, SQL contents, passwords or tokens.
        if isinstance(error, (ValueError, RuntimeError, FileNotFoundError)):
            print(f"Export stopped: {error}", file=sys.stderr)
        else:
            print("Export stopped: unable to read the PostgreSQL database. Check its availability and credentials. No records were changed.", file=sys.stderr)
        sys.exit(1)
