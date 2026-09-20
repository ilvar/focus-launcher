#!/usr/bin/env python3
"""Tests fdroid/submit.py against a fake GitLab: python3 fdroid/test_submit.py

The point of submit.py is that there is only ever ONE merge request. These tests start a small
HTTP server that behaves like the handful of GitLab endpoints the script uses, run the script as
the workflow does, and look at what it did to the server's state.
"""
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
APP = "com.focus.launcher"
FILE = f"metadata/{APP}.yml"
UPSTREAM, FORK, OTHER_FORK = 1, 2, 3


class FakeGitLab:
    def __init__(self):
        self.paths = {"fdroid/fdroiddata": UPSTREAM, "owner/fdroiddata": FORK}
        self.files = {}          # (project, ref, path) -> text
        self.branches = {(UPSTREAM, "master"), (FORK, "master")}
        self.requests = []       # merge requests on upstream
        self.notes = []          # (iid, body)
        self.commits = []        # bodies of POST .../repository/commits
        self.created = 0

    def add_request(self, state, source_project=FORK):
        iid = len(self.requests) + 1
        self.requests.insert(0, {"iid": iid, "state": state, "source_project_id": source_project, "source_branch": APP,
                                 "web_url": f"https://gitlab.example/fdroid/fdroiddata/-/merge_requests/{iid}"})
        return iid


def handler_for(gl):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def reply(self, status, body=None, raw=None):
            data = raw.encode() if raw is not None else json.dumps(body).encode()
            self.send_response(status); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

        def body(self):
            return json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")

        def route(self, method):
            if self.headers.get("PRIVATE-TOKEN") != "test-token":
                return self.reply(401, {"message": "401 Unauthorized"})
            url = urllib.parse.urlsplit(self.path)
            parts = [urllib.parse.unquote(p) for p in url.path.split("/")[2:]]   # after /v4
            query = urllib.parse.parse_qs(url.query)
            if parts[0] != "projects":
                return self.reply(404, {})
            if len(parts) == 2 and method == "GET":
                pid = gl.paths.get(parts[1])
                return self.reply(200, {"id": pid}) if pid else self.reply(404, {"message": "404 Project Not Found"})
            pid = int(parts[1]); rest = parts[2:]
            if rest[:2] == ["repository", "files"] and rest[-1] == "raw":
                text = gl.files.get((pid, query["ref"][0], rest[2]))
                return self.reply(200, raw=text) if text is not None else self.reply(404, {"message": "404 File Not Found"})
            if rest[:2] == ["repository", "branches"]:
                return self.reply(200, {"name": rest[2]}) if (pid, rest[2]) in gl.branches else self.reply(404, {"message": "404 Branch Not Found"})
            if rest == ["repository", "commits"] and method == "POST":
                body = self.body(); gl.commits.append(body); branch = body["branch"]
                if (pid, branch) not in gl.branches:
                    if "start_branch" not in body:
                        return self.reply(400, {"message": "You can only create or edit files when you are on a branch"})
                    gl.branches.add((pid, branch))
                elif "start_branch" in body:
                    return self.reply(400, {"message": "A branch called '%s' already exists" % branch})
                for action in body["actions"]:
                    key = (pid, branch, action["file_path"])
                    if action["action"] == "create" and key in gl.files:
                        return self.reply(400, {"message": "A file with this name already exists"})
                    if action["action"] == "update" and key not in gl.files:
                        return self.reply(400, {"message": "A file with this name doesn't exist"})
                    gl.files[key] = action["content"]
                return self.reply(201, {"id": "abc"})
            if rest == ["merge_requests"] and method == "GET":
                return self.reply(200, [m for m in gl.requests if m["source_branch"] == query["source_branch"][0]])
            if rest == ["merge_requests"] and method == "POST":
                body = self.body(); gl.created += 1
                iid = gl.add_request("opened", source_project=pid)
                gl.requests[0].update(title=body["title"], description=body["description"])
                return self.reply(201, gl.requests[0])
            if len(rest) == 2 and rest[0] == "merge_requests" and method == "PUT":
                match = next(m for m in gl.requests if m["iid"] == int(rest[1]))
                if self.body().get("state_event") == "reopen":
                    match["state"] = "opened"
                return self.reply(200, match)
            if len(rest) == 3 and rest[0] == "merge_requests" and rest[2] == "notes" and method == "POST":
                gl.notes.append((int(rest[1]), self.body()["body"]))
                return self.reply(201, {"id": 1})
            return self.reply(404, {"message": "not faked: " + self.path})

        def do_GET(self): self.route("GET")
        def do_POST(self): self.route("POST")
        def do_PUT(self): self.route("PUT")
    return Handler


class SubmitTest(unittest.TestCase):
    def setUp(self):
        self.gl = FakeGitLab()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), handler_for(self.gl))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.dir = tempfile.TemporaryDirectory()
        self.write_recipe("recipe for 1.1.29\n")
        with open(os.path.join(self.dir.name, "MERGE_REQUEST.md"), "w") as f:
            f.write("checklist")

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.dir.cleanup()

    def write_recipe(self, text):
        with open(os.path.join(self.dir.name, f"{APP}.yml"), "w") as f:
            f.write(text)

    def run_submit(self, version="1.1.29", token="test-token"):
        env = dict(os.environ, GITLAB_API=f"http://127.0.0.1:{self.server.server_address[1]}/v4", GITLAB_TOKEN=token,
                   FORK="owner/fdroiddata", APP_ID=APP, VERSION=version, RECIPE_DIR=self.dir.name)
        env.pop("GITHUB_STEP_SUMMARY", None)
        return subprocess.run([sys.executable, os.path.join(HERE, "submit.py")], env=env, capture_output=True, text=True)

    def ours(self):
        return [m for m in self.gl.requests if m["source_project_id"] == FORK]

    def test_first_time_creates_branch_and_one_merge_request(self):
        result = self.run_submit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.gl.commits[0]["start_branch"], "master")
        self.assertEqual(self.gl.commits[0]["actions"][0]["action"], "create")
        self.assertEqual(self.gl.commits[0]["commit_message"], f"New App: {APP}")
        self.assertEqual(len(self.ours()), 1)
        self.assertEqual(self.ours()[0]["title"], "New app: Focus Launcher")
        self.assertEqual(self.gl.notes, [])

    def test_open_merge_request_is_updated_not_duplicated(self):
        self.run_submit()
        self.write_recipe("recipe for 1.1.31\n")
        result = self.run_submit(version="1.1.31")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.gl.created, 1, "a second merge request was opened")
        self.assertEqual(len(self.gl.commits), 2)
        self.assertEqual(self.gl.commits[1]["actions"][0]["action"], "update")
        self.assertNotIn("start_branch", self.gl.commits[1])
        self.assertEqual(self.gl.files[(FORK, APP, FILE)], "recipe for 1.1.31\n")
        self.assertEqual(len(self.gl.notes), 1)
        self.assertIn("1.1.31", self.gl.notes[0][1])
        self.assertIn("updated", result.stdout)

    def test_same_recipe_again_changes_nothing(self):
        self.run_submit()
        result = self.run_submit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.gl.created, len(self.gl.commits), len(self.gl.notes)), (1, 1, 0))
        self.assertIn("already up to date", result.stdout)

    def test_closed_merge_request_is_reopened_and_updated(self):
        self.run_submit()
        self.ours()[0]["state"] = "closed"
        self.write_recipe("recipe for 1.1.33\n")
        result = self.run_submit(version="1.1.33")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.gl.created, 1)
        self.assertEqual(self.ours()[0]["state"], "opened")
        self.assertTrue(self.gl.notes[0][1].startswith("Reopened."))

    def test_somebody_elses_merge_request_is_not_ours(self):
        self.gl.add_request("opened", source_project=OTHER_FORK)
        result = self.run_submit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(len(self.ours()), 1)
        self.assertEqual(self.gl.notes, [], "wrote a note on a stranger's merge request")

    def test_already_in_fdroid_submits_nothing(self):
        self.gl.files[(UPSTREAM, "master", FILE)] = "the recipe F-Droid's bot maintains\n"
        result = self.run_submit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.gl.created, self.gl.commits, self.gl.notes), (0, [], []))
        self.assertIn("already in F-Droid", result.stdout)

    def test_branch_left_over_without_a_merge_request(self):
        self.gl.branches.add((FORK, APP)); self.gl.files[(FORK, APP, FILE)] = "an old recipe\n"
        result = self.run_submit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.gl.commits[0]["actions"][0]["action"], "update")
        self.assertEqual(self.gl.created, 1)

    def test_missing_token_stops_before_any_call(self):
        result = self.run_submit(token="")
        self.assertEqual(result.returncode, 1)
        self.assertIn("FDROID_GITLAB_TOKEN", result.stdout)
        self.assertEqual((self.gl.created, self.gl.commits), (0, []))

    def test_api_error_fails_the_job(self):
        result = self.run_submit(token="wrong")
        self.assertEqual(result.returncode, 1)
        self.assertIn("::error::", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
