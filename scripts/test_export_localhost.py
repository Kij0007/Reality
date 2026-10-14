"""Exporter safety tests use fixture records only; no actual user history is committed."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("reality_export", Path(__file__).with_name("export-localhost.py"))
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)


class ExportSafetyTests(unittest.TestCase):
    def setUp(self):
        self.activity = {"id": 7, "active": True, "name": "Test", "minimumDuration": 1, "category": "OTHER"}
        self.session = {"id": 9, "activityId": 7, "startTime": "2026-10-12T10:00:00"}
        self.streak = {"activityId": 7, "currentStreak": 9, "lastEvaluatedDate": "2026-10-12"}
        self.backup = {"activities": [self.activity, dict(self.activity, id=2, active=False)],
            "sessions": [self.session], "streaks": [self.streak]}

    def test_archived_parent_is_kept_without_requiring_active_list_visibility(self):
        export.check_identity(self.backup, [self.activity], [self.session], lambda _: self.streak)
        self.assertEqual(2, len(self.backup["activities"]))

    def test_different_database_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "complete session IDs"):
            export.check_identity(self.backup, [self.activity], [], lambda _: self.streak)
        with self.assertRaisesRegex(ValueError, "activity details"):
            export.check_identity(self.backup, [dict(self.activity, name="Other")], [self.session], lambda _: self.streak)

    def test_streak_snapshot_must_match_real_server(self):
        with self.assertRaisesRegex(ValueError, "streak does not match"):
            export.check_identity(self.backup, [self.activity], [self.session], lambda _: dict(self.streak, currentStreak=8))

    def test_nonlocal_urls_and_embedded_credentials_are_rejected(self):
        for address in ("https://example.com", "http://user:password@localhost:8081", "file:///tmp"):
            with self.assertRaises(ValueError):
                export.api_reader(address)

    def test_properties_support_environment_defaults_without_printing_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "application.properties"
            path.write_text("spring.datasource.url=${REALITY_UNIT_DB:jdbc:postgresql://localhost:5432/reality}\nspring.datasource.password=${REALITY_UNIT_PASSWORD}\n", encoding="utf-8")
            with patch.dict(os.environ, {"REALITY_UNIT_PASSWORD": "fixture only"}):
                values = export.read_properties(path)
                self.assertEqual("jdbc:postgresql://localhost:5432/reality", values["spring.datasource.url"])
                self.assertEqual("fixture only", values["spring.datasource.password"])

    def test_sql_export_is_read_only_and_includes_all_relationships(self):
        sql = Path(__file__).with_name("reality-export.sql").read_text(encoding="utf-8")
        self.assertIn("ISOLATION LEVEL REPEATABLE READ READ ONLY", sql)
        self.assertIn("public.session_break", sql)
        self.assertIn("public.activity_scheduled_days", sql)
        self.assertIn("__OWNER_CONDITION__", sql)
        self.assertNotRegex(sql, r"(?i)\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER)\b")


if __name__ == "__main__":
    unittest.main()

