#!/usr/bin/env python3
"""Puts the F-Droid recipe on the owner's fdroiddata fork and makes sure there is ONE merge request.

Run by .github/workflows/fdroid.yml (job "submit"). What it does, in this order:

  * Rkd Launcher is already in F-Droid (the recipe is on fdroiddata's master): stop. New versions are
    picked up from this repository's tags; F-Droid's bot owns the recipe from then on.
  * A merge request from the fork's branch is OPEN: update it. The new recipe is committed on top
    of the branch (no force-push, no rebase), a note tells the reviewers what changed, and no
    second merge request is opened. If the recipe is unchanged, nothing is committed.
  * The last merge request from that branch was CLOSED without being merged: update the branch,
    reopen that merge request, leave a note. The discussion stays in one place.
  * Otherwise: create the branch from the fork's master with the recipe and open the merge request.

Environment: GITLAB_TOKEN (api scope), FORK ("user/fdroiddata"), APP_ID, VERSION, and optionally
GITLAB_API (tests point it at a fake server), RECIPE_DIR (default "recipe"), UPSTREAM.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = os.environ.get("GITLAB_API", "https://gitlab.com/api/v4").rstrip("/")
TOKEN = os.environ.get("GITLAB_TOKEN", "")
FORK = os.environ.get("FORK", "")
UPSTREAM = os.environ.get("UPSTREAM", "fdroid/fdroiddata")
APP_ID = os.environ.get("APP_ID", "pw.rkd.launcher")
VERSION = os.environ.get("VERSION", "")
RECIPE_DIR = os.environ.get("RECIPE_DIR", "recipe")
BRANCH = APP_ID
FILE_PATH = f"metadata/{APP_ID}.yml"
TITLE = "New app: Rkd Launcher"


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"GitLab answered {status}: {body[:300]}")
        self.status = status


def quote(value):
    return urllib.parse.quote(value, safe="")


def call(method, path, body=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(API + path, data=data, method=method)
    request.add_header("PRIVATE-TOKEN", TOKEN)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            text = response.read().decode()
    except urllib.error.HTTPError as error:
        raise ApiError(error.code, error.read().decode(errors="replace")) from None
    return text if raw else (json.loads(text) if text else None)


def exists(path):
    try:
        call("GET", path, raw=True)
        return True
    except ApiError as error:
        if error.status == 404:
            return False
        raise


def file_on(project, ref):
    """The recipe as it is on that ref, or None."""
    try:
        return call("GET", f"/projects/{project}/repository/files/{quote(FILE_PATH)}/raw?ref={quote(ref)}", raw=True)
    except ApiError as error:
        if error.status == 404:
            return None
        raise


def say(text):
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write(text + "\n\n")


def main():
    if not TOKEN or not FORK:
        print("::error::RKD_FDROID_GITLAB_TOKEN (secret of the release environment) or RKD_FDROID_GITLAB_FORK "
              "(repository variable, like yourname/fdroiddata) is missing. See fdroid/README.md.")
        return 1
    recipe = open(os.path.join(RECIPE_DIR, f"{APP_ID}.yml")).read()
    description_file = os.path.join(RECIPE_DIR, "MERGE_REQUEST.md")
    description = open(description_file).read() if os.path.exists(description_file) else ""

    upstream = call("GET", f"/projects/{quote(UPSTREAM)}")["id"]
    fork = call("GET", f"/projects/{quote(FORK)}")["id"]

    if file_on(upstream, "master") is not None:
        say(f"### Rkd Launcher is already in F-Droid\n`{FILE_PATH}` is on fdroiddata's master. New versions are picked up from "
            "this repository's tags by F-Droid's bot, which owns the recipe from now on. Nothing was submitted.")
        return 0

    # Every merge request that came from this fork's branch, newest first.
    requests = [m for m in call("GET", f"/projects/{upstream}/merge_requests?source_branch={quote(BRANCH)}"
                                       "&state=all&order_by=created_at&sort=desc&per_page=50")
                if m.get("source_project_id") == fork]
    opened = next((m for m in requests if m["state"] == "opened"), None)
    closed = next((m for m in requests if m["state"] == "closed"), None)

    # The recipe goes onto the branch first: a merge request shows whatever its branch holds.
    branch_exists = exists(f"/projects/{fork}/repository/branches/{quote(BRANCH)}")
    current = file_on(fork, BRANCH) if branch_exists else None
    changed = current != recipe
    if changed:
        commit = {"branch": BRANCH,
                  "commit_message": f"{APP_ID}: recipe for {VERSION}" if (opened or closed or current is not None) else f"New App: {APP_ID}",
                  "actions": [{"action": "update" if current is not None else "create", "file_path": FILE_PATH, "content": recipe}]}
        if not branch_exists:
            commit["start_branch"] = "master"
        call("POST", f"/projects/{fork}/repository/commits", commit)
        print(f"recipe committed to {FORK}, branch {BRANCH}")
    else:
        print(f"the recipe on {FORK}:{BRANCH} is already this one; nothing to commit")

    target = opened or closed
    if target:
        iid, url = target["iid"], target["web_url"]
        if not opened:
            call("PUT", f"/projects/{upstream}/merge_requests/{iid}", {"state_event": "reopen"})
        if changed or not opened:
            note = (f"The recipe was updated by the project's F-Droid workflow: it now builds **{VERSION}**. "
                    "Before this update the workflow ran `fdroid readmeta`, `rewritemeta`, `checkupdates`, `lint` and "
                    "`build` in the `fdroidserver:buildserver` container.")
            if not opened:
                note = "Reopened. " + note
            call("POST", f"/projects/{upstream}/merge_requests/{iid}/notes", {"body": note})
        what = "reopened and updated" if not opened else ("updated" if changed else "already up to date")
        say(f"### Merge request {what}: {url}\nNo second merge request was opened. Answer the reviewers there.")
        return 0

    created = call("POST", f"/projects/{fork}/merge_requests",
                   {"source_branch": BRANCH, "target_branch": "master", "target_project_id": upstream, "title": TITLE,
                    "description": description, "allow_collaboration": True, "remove_source_branch": True})
    say(f"### Merge request opened: {created['web_url']}\nReviewers will ask questions there. Answer them on GitLab; "
        "running this workflow again updates this merge request instead of opening another.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ApiError as error:
        print(f"::error::{error}")
        sys.exit(1)
