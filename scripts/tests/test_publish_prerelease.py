import contextlib
import hashlib
import io
import json
import os
import pathlib
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'publish_prerelease.py'
SHA = 'a' * 40


class FakeGitHub:
    def __init__(self):
        self.release = None
        self.ref = None
        self.bytes = {}
        self.writes = []
        self.error = None

    def run(self, args, check=True, **kwargs):
        assert args[0] == 'gh'
        code, out, err = 0, '', ''
        if args[1] == 'api':
            if self.error:
                code, err = 1, self.error
            elif '/releases/tags/' in args[2]:
                if self.release and not self.release['draft']:
                    out = json.dumps({**self.release, 'assets': [{'name': n} for n in self.bytes]})
                else:
                    code, err = 1, 'HTTP 404'
            elif args[2:4] == ['--paginate', '--slurp']:
                out = json.dumps([[self.release] if self.release else []])
            elif args[2].endswith('/releases/123'):
                out = json.dumps({**self.release, 'assets': [{'name': n} for n in self.bytes]})
            elif '/git/ref/tags/' in args[2]:
                if self.ref:
                    out = json.dumps({'object': {'type': 'commit', 'sha': self.ref}})
                else:
                    code, err = 1, 'HTTP 404'
            else:
                raise AssertionError(args)
        elif args[1:3] == ['release', 'download']:
            target = pathlib.Path(args[args.index('--dir') + 1])
            for name, content in self.bytes.items():
                (target / name).write_bytes(content)
        elif args[1:3] == ['release', 'create']:
            self.writes.append('create')
            self.ref = args[args.index('--target') + 1]
            self.release = {'id': 123, 'tag_name': f'dev-{SHA}', 'draft': True, 'prerelease': True, 'target_commitish': SHA}
        elif args[1:3] == ['release', 'upload']:
            self.writes.append('upload')
            for name in args[args.index('--clobber') + 1:]:
                p = pathlib.Path(name)
                self.bytes[p.name] = p.read_bytes()
        elif args[1:3] == ['release', 'edit']:
            self.writes.append('edit')
            self.release['draft'] = False
        else:
            raise AssertionError(args)
        result = subprocess.CompletedProcess(args, code, out, err)
        if check and code:
            raise subprocess.CalledProcessError(code, args, out, err)
        return result


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.old = os.getcwd()
        self.addCleanup(os.chdir, self.old)
        os.chdir(self.temp.name)
        self.assets = pathlib.Path('release-assets')
        self.assets.mkdir()
        for mc in ['26.1.2', '26.2', '26.3']:
            (self.assets / f'regrowth-kotlin-neoforge-{mc}-0.1.0-dev.jar').write_bytes(mc.encode())
        self.github = FakeGitHub()

    def execute(self):
        with patch.dict(os.environ, {'GITHUB_SHA': SHA, 'GITHUB_REPOSITORY': 'example/regrowth'}), \
                patch('subprocess.run', self.github.run), contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(SCRIPT), run_name='__main__')

    def test_initial_publish_and_rerun_are_idempotent(self):
        self.execute()
        self.assertEqual(['create', 'upload', 'edit'], self.github.writes)
        self.assertFalse(self.github.release['draft'])
        self.assertEqual(4, len(self.github.bytes))
        self.execute()
        self.assertEqual(['create', 'upload', 'edit'], self.github.writes)

    def test_conflicting_tag_is_never_overwritten(self):
        self.github.ref = 'b' * 40
        with self.assertRaises(AssertionError):
            self.execute()
        self.assertFalse(self.github.writes)

    def test_draft_can_be_repaired_for_same_commit(self):
        self.github.ref = SHA
        self.github.release = {'id': 123, 'tag_name': f'dev-{SHA}', 'draft': True, 'prerelease': True, 'target_commitish': SHA}
        self.execute()
        self.assertEqual(['upload', 'edit'], self.github.writes)

    def test_draft_without_matching_commit_identity_is_rejected(self):
        self.github.release = {'id': 123, 'tag_name': f'dev-{SHA}', 'draft': True, 'prerelease': True, 'target_commitish': 'b' * 40}
        with self.assertRaises(AssertionError):
            self.execute()
        self.assertFalse(self.github.writes)

    def test_server_error_is_not_mistaken_for_missing_release(self):
        for error in ['HTTP 503', 'HTTP 503 (request id 404-invalid)', 'HTTP 403']:
            with self.subTest(error=error):
                self.github.error = error
                with self.assertRaises(RuntimeError):
                    self.execute()
                self.assertFalse(self.github.writes)

    def test_published_bytes_are_immutable(self):
        self.execute()
        next(self.assets.glob('*.jar')).write_bytes(b'changed')
        with self.assertRaises(AssertionError):
            self.execute()
        self.assertEqual(['create', 'upload', 'edit'], self.github.writes)


if __name__ == '__main__':
    unittest.main()
