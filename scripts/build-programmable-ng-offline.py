#!/usr/bin/env python3
"""Build the NG191 MPP with downloaded public artifacts, without GitHub Packages.
See docs/programmable-ng-191.md. Inputs remain in --tools; output is under --out.
The source APK is not needed to build the bundle. It is needed for patch verification.
"""
import argparse
import pathlib
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--tools', type=pathlib.Path, required=True)
parser.add_argument('--out', type=pathlib.Path, required=True)
parser.add_argument('--java', default='java', help='Java 17+ with jdk.compiler module')
args = parser.parse_args()
tools = args.tools.resolve()
out = args.out.resolve()
repo = pathlib.Path(__file__).resolve().parents[1]
work = out / 'ng191-build'
work.mkdir(parents=True, exist_ok=True)
required = ['android35.jar', 'rhino.jar', 'hiddenapi.jar', 'morphe-desktop.jar', 'haiagaru-base.mpp', 'r8.jar']
for name in required:
    if not (tools / name).is_file():
        raise SystemExit('Missing dependency: ' + name)
gradle_libs = list(tools.glob('gradle/gradle-*/lib'))
if len(gradle_libs) != 1:
    raise SystemExit('Provide one extracted Gradle distribution under --tools/gradle (Kotlin compiler).')

def run(*cmd):
    subprocess.run([str(x) for x in cmd], check=True)

def java(*cmd):
    run(args.java, '-Djava.io.tmpdir=' + str(work), *cmd)

for name in ['extension-classes', 'patch-classes', 'extension-dex', 'patch-dex']:
    path = work / name
    if path.exists():
        shutil.rmtree(path)
    path.mkdir()
java('-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '17', '-target', '17',
     '-cp', ':'.join(str(tools / n) for n in ['android35.jar', 'hiddenapi.jar', 'rhino.jar']),
     '-d', work / 'extension-classes', *sorted((repo / 'extensions/chmate/src/main/java/app/morphe/extension/chmate').glob('*.java')))
java('-cp', str(gradle_libs[0] / '*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
     '-no-stdlib', '-no-reflect', '-jvm-target', '11',
     '-classpath', ':'.join(str(tools / n) for n in ['morphe-desktop.jar', 'haiagaru-base.mpp', 'android35.jar']),
     '-d', work / 'patch-classes', *sorted((repo / 'patches/src/main/kotlin/app/morphe/patches/chmate').glob('*.kt')))
for part in ['extension', 'patch']:
    compiled = work / (part + '-classes')
    generated = {p.relative_to(compiled).as_posix(): p for p in compiled.rglob('*.class')}
    with zipfile.ZipFile(work / (part + '-classes.jar'), 'w', zipfile.ZIP_DEFLATED) as z:
        if part == 'patch':
            with zipfile.ZipFile(tools / 'haiagaru-base.mpp') as base:
                for name in base.namelist():
                    if name.endswith('.class') and name not in generated and not name.startswith('META-INF/versions/'):
                        z.writestr(name, base.read(name))
        for name, path in generated.items():
            z.write(path, name)
    inputs = [work / (part + '-classes.jar')]
    extra = []
    if part == 'extension':
        inputs += [tools / 'hiddenapi.jar', tools / 'rhino.jar']
    else:
        extra = ['--classpath', tools / 'morphe-desktop.jar']
    java('-cp', tools / 'r8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '21' if part == 'extension' else '26',
         '--lib', tools / 'android35.jar', *extra, '--output', work / (part + '-dex'), *inputs)
    if len(list((work / (part + '-dex')).glob('*.dex'))) != 1:
        raise SystemExit('Expected a single DEX for ' + part)
output = out / 'haiagaru-ng191-0.4.mpp'
with zipfile.ZipFile(tools / 'haiagaru-base.mpp') as base, zipfile.ZipFile(work / 'patch-classes.jar') as classes, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
    for name in base.namelist():
        if name.endswith('.class') or name in ['classes.dex', 'extensions/chmate.mpe', 'META-INF/MANIFEST.MF']:
            continue
        z.writestr(name, base.read(name))
    manifest = base.read('META-INF/MANIFEST.MF').decode().replace('Name: Haiagaru', 'Name: Haiagaru NG191 (experimental)')
    manifest = manifest.replace('Version: 1.3.3\r\n', 'Version: 1.3.3-ng191.4\r\n').replace('Patcher-Version: 1.10.0', 'Patcher-Version: 1.14.0')
    z.writestr('META-INF/MANIFEST.MF', manifest)
    for name in classes.namelist():
        z.writestr(name, classes.read(name))
    z.write(work / 'patch-dex/classes.dex', 'classes.dex')
    z.write(work / 'extension-dex/classes.dex', 'extensions/chmate.mpe')
    z.write(repo / 'LICENSE', 'licenses/Haiagaru-LICENSE')
    z.write(repo / 'docs/programmable-ng-191.md', 'README-NG191.md')
    with zipfile.ZipFile(tools / 'rhino.jar') as rhino:
        for name in rhino.namelist():
            if ('LICENSE' in name.upper() or 'NOTICE' in name.upper()) and not name.endswith('/'):
                z.writestr('licenses/rhino/' + pathlib.Path(name).name, rhino.read(name))
print(output)
