"""Create a portable source archive without caches, local SDK paths, or secrets."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
excluded = {".git", ".gradle", ".idea", ".vscode", ".kotlin", "build", "artifacts", "__pycache__", ".venv"}
destination = root / "artifacts" / "DualNet-Optimizer-source.zip"
destination.parent.mkdir(exist_ok=True)
with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in excluded for part in relative.parts):
            continue
        if path.name in {"local.properties", ".env"} or path.suffix in {".pem", ".jks", ".keystore", ".hprof"}:
            continue
        archive.write(path, "DualNet Optimizer/" + relative.as_posix())
print(destination)
