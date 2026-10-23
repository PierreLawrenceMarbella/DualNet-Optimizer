"""Check staged blobs; report file names only, never suspected secret values."""
import re
import subprocess
import sys

patterns = [
    rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    rb"gh[pousr]_[A-Za-z0-9]{30,}",
    rb"github_pat_[A-Za-z0-9_]{30,}",
    rb"AKIA[A-Z0-9]{16}",
    rb"xox[baprs]-[A-Za-z0-9-]{20,}",
    rb"sk-proj-[A-Za-z0-9_-]{20,}",
    rb"https?://[^\s/@:]+:[^\s/@]+@github\.com",
]
names = subprocess.check_output(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"]).decode().split("\0")
hits = []
count = 0
for name in filter(None, names):
    count += 1
    data = subprocess.check_output(["git", "show", ":" + name])
    if any(re.search(pattern, data) for pattern in patterns):
        hits.append(name)
    if name.endswith((".pem", ".key", ".keystore", ".jks", ".env")) or name == "local.properties":
        hits.append(name)
if hits:
    print("Review required for staged paths:\n" + "\n".join(sorted(set(hits))))
    sys.exit(1)
print(f"Scanned {count} staged files: no known credential patterns or private-key files found.")
print("This pattern scan complements manual review; it is not a guarantee against every secret format.")
