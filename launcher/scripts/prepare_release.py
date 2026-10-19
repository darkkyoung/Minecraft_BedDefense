"""Prepare an immutable dev release from a successful Gradle build.
Only official Fabric HTTPS endpoints supply dependencies. No Minecraft binaries.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
import zipfile

REPO = 'darkkyoung/Minecraft_BedDefense'

def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'BedDefense-Release/0.1'})
    with urllib.request.urlopen(req, timeout=90) as r:
        if not r.url.startswith(('https://meta.fabricmc.net/', 'https://maven.fabricmc.net/')):
            raise ValueError('Untrusted Fabric redirect')
        data = r.read(128 * 1024 * 1024 + 1)
    if len(data) > 128 * 1024 * 1024:
        raise ValueError('Oversized dependency')
    return data

def mod_metadata(data_path, expected_id, expected_version):
    with zipfile.ZipFile(data_path) as z:
        info = json.loads(z.read('fabric.mod.json'))
    assert info['id'] == expected_id, info
    assert info['version'] == expected_version, info
    constraint = json.dumps(info['depends']['minecraft'])
    assert '26.3' in constraint and '26.2' not in constraint, constraint


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', default='release')
    parser.add_argument('--build', type=int, default=int(os.environ.get('GITHUB_RUN_NUMBER', '1')))
    args = parser.parse_args()
    props = dict(line.strip().split('=', 1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    mc, loader, api = (props[k] for k in ('minecraft_version', 'loader_version', 'fabric_api_version'))
    assert (mc, loader, api) == ('26.3', '0.19.5', '0.163.0+26.3'), 'Update launcher compatibility policy before changing dependencies'
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    assert re.fullmatch('[0-9a-f]{40}', sha)
    tag = f'dev-mc{mc}-{args.build}-{sha[:12]}'
    base = f'https://github.com/{REPO}/releases/download/{tag}/'
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    cache = out / 'validation-cache'
    cache.mkdir(exist_ok=True)
    files = []

    def payload(role, path, url, library_path=None):
        data = path.read_bytes()
        obj = dict(role=role, name=path.name, url=url, size=len(data), sha256=hashlib.sha256(data).hexdigest())
        if library_path:
            obj['libraryPath'] = library_path
        files.append(obj)
        shutil.copyfile(path, cache / f'payload-{len(files)-1}.bin')

    jars = [p for p in Path('build/libs').glob('beddefense-*.jar') if not p.name.endswith('-sources.jar')]
    assert len(jars) == 1, jars
    jar = out / jars[0].name
    shutil.copyfile(jars[0], jar)
    mod_metadata(jar, 'beddefense', props['version'])
    payload('beddefense', jar, base + jar.name)

    api_url = f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar'
    api_file = cache / f'fabric-api-{api}.jar'
    api_file.write_bytes(fetch(api_url))
    # Verify the upstream Maven digest in addition to recording SHA-256 for the client.
    upstream_sha1 = fetch(api_url + '.sha1').decode().strip().split()[0]
    assert hashlib.sha1(api_file.read_bytes()).hexdigest() == upstream_sha1
    mod_metadata(api_file, 'fabric-api', api)
    payload('fabricApi', api_file, api_url)

    profile = json.loads(fetch(f'https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/profile/json'))
    assert profile['inheritsFrom'] == mc
    assert profile['mainClass'] == 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    profile['id'] = f'beddefense-fabric-{loader}-{mc}'
    profile_file = out / 'fabric-profile.json'
    # No executable launcher arguments beyond the official Fabric metadata.
    for lib in profile['libraries']:
        assert lib['url'] == 'https://maven.fabricmc.net/', lib
        parts = lib['name'].split(':')
        assert len(parts) == 3 and all(re.fullmatch(r'[A-Za-z0-9._+\-]+', p) for p in parts), lib
        group, artifact, version = parts
        relative = f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.jar'
        assert relative.startswith(('net/fabricmc/', 'org/ow2/asm/')), lib
    profile_file.write_text(json.dumps(profile, indent=2) + '\n', encoding='utf-8')
    payload('fabricProfile', profile_file, base + profile_file.name)
    for lib in profile['libraries']:
        group, artifact, version = lib['name'].split(':')
        relative = f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.jar'
        url = 'https://maven.fabricmc.net/' + relative
        path = cache / f'{artifact}-{version}.jar'
        path.write_bytes(fetch(url))
        assert hashlib.sha1(path.read_bytes()).hexdigest() == fetch(url + '.sha1').decode().strip().split()[0]
        with zipfile.ZipFile(path) as z:
            assert z.namelist()
        payload('library', path, url, relative)
    notes = subprocess.check_output(['git', 'log', '-8', '--format=%h %s'], text=True, encoding='utf-8')
    notes = f'Minecraft {mc} / Fabric {loader} / API {api}\n개발 빌드 {args.build} · {sha}\n\n최근 변경 (최대 8개 커밋):\n{notes}\n게임 내 검증과 Windows 실기기 검증은 별도입니다.'
    launcher_notes = Path('launcher/release-notes.md')
    if launcher_notes.exists():
        notes = launcher_notes.read_text(encoding='utf-8') + '\n\n### 빌드 정보 및 상세 커밋\n' + notes
    manifest = dict(schemaVersion=1, channel='dev', minecraftVersion=mc, loaderVersion=loader, fabricApiVersion=api,
                    modVersion=props['version'], commit=sha, buildNumber=args.build, tag=tag, notes=notes, files=files)
    (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (out / 'release-notes.md').write_text(notes, encoding='utf-8')
    (out / 'tag.txt').write_text(tag, encoding='utf-8')
    checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(out.iterdir()) if p.is_file() and p.suffix in ('.json', '.jar'))
    (out / 'SHA256SUMS.txt').write_text(checksums, encoding='utf-8')
    print(f'Validated {len(files)} payloads: {tag}')

if __name__ == '__main__':
    main()
