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
        self.hide_drafts = False
        self.corrupt_upload = False
        self.list_calls = 0

    def metadata(self):
        return {**self.release, 'assets': [
            {'id': n, 'name': n, 'digest': 'sha256:' + hashlib.sha256(b).hexdigest()}
            for n, b in self.bytes.items()]}

    def upload(self, request, timeout):
        from urllib.parse import urlparse, parse_qs
        url = urlparse(request.full_url)
        assert url.netloc == 'uploads.github.com'
        assert url.path.endswith('/releases/123/assets')
        assert request.get_header('Authorization') == 'Bearer test-token'
        assert request.method == 'POST'
        self.writes.append('upload')
        name = parse_qs(url.query)['name'][0]
        self.bytes[name] = b'corrupt' if self.corrupt_upload else request.data
        return contextlib.closing(io.BytesIO(b'{}'))

    def run(self, args, check=True, **kwargs):
        assert args[0] == 'gh'
        code, out, err = 0, '', ''
        if args[1] == 'api':
            method = args[args.index('--method') + 1] if '--method' in args else 'GET'
            endpoint = next(a for a in args if a.startswith('repos/'))
            if self.error:
                code, err = 1, self.error
            elif method == 'POST' and endpoint.endswith('/releases'):
                self.writes.append('create')
                self.release = {'id': 123, **json.loads(pathlib.Path(args[args.index('--input') + 1]).read_text())}
                # GitHub creates the actual tag only on publication.
                out = json.dumps(self.metadata())
            elif method == 'DELETE' and '/releases/assets/' in endpoint:
                del self.bytes[endpoint.rsplit('/', 1)[1]]
            elif method == 'PATCH':
                assert endpoint.endswith('/releases/123')
                self.writes.append('edit')
                self.release['draft'] = False
                self.ref = SHA
                out = json.dumps(self.metadata())
            elif '/releases/tags/' in endpoint:
                if self.release and not self.release['draft']:
                    out = json.dumps(self.metadata())
                else:
                    code, err = 1, 'HTTP 404'
            elif '--paginate' in args:
                self.list_calls += 1
                out = json.dumps(self.metadata()) + '\n' if self.release and not self.hide_drafts else ''
            elif endpoint.endswith('/releases/123'):
                out = json.dumps(self.metadata())
            elif '/git/ref/tags/' in endpoint:
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
        with patch.dict(os.environ, {'GITHUB_SHA': SHA, 'GITHUB_REPOSITORY': 'example/regrowth', 'GH_TOKEN': 'test-token'}), \
                patch('subprocess.run', self.github.run), \
                patch('urllib.request.urlopen', self.github.upload), contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(SCRIPT), run_name='__main__')

    def test_initial_publish_and_rerun_are_idempotent(self):
        self.execute()
        self.assertEqual(['create', *['upload'] * 4, 'edit'], self.github.writes)
        self.assertFalse(self.github.release['draft'])
        self.assertEqual(4, len(self.github.bytes))
        self.execute()
        self.assertEqual(['create', *['upload'] * 4, 'edit'], self.github.writes)

    def test_new_draft_can_publish_before_discovery_sees_it(self):
        self.github.hide_drafts = True
        self.execute()
        self.assertFalse(self.github.release['draft'])
        self.assertEqual(1, self.github.list_calls)  # pre-create lookup only
        self.assertEqual(4, len(self.github.bytes))

    def test_upload_digest_mismatch_never_publishes(self):
        self.github.corrupt_upload = True
        with self.assertRaisesRegex(AssertionError, 'Uploaded bytes differ'):
            self.execute()
        self.assertTrue(self.github.release['draft'])
        self.assertNotIn('edit', self.github.writes)

    def test_conflicting_tag_is_never_overwritten(self):
        self.github.ref = 'b' * 40
        with self.assertRaises(AssertionError):
            self.execute()
        self.assertFalse(self.github.writes)

    def test_draft_can_be_repaired_for_same_commit(self):
        self.github.ref = SHA
        self.github.release = {'id': 123, 'tag_name': f'dev-{SHA}', 'draft': True, 'prerelease': True, 'target_commitish': SHA}
        self.execute()
        self.assertEqual([*['upload'] * 4, 'edit'], self.github.writes)

    def test_partial_draft_retry_replaces_assets_and_verifies_bytes(self):
        self.github.release = {'id': 123, 'tag_name': f'dev-{SHA}', 'draft': True,
                               'prerelease': True, 'target_commitish': SHA}
        self.github.bytes = {'stale.jar': b'stale', 'SHA256SUMS': b'incomplete'}
        self.execute()
        self.assertFalse(self.github.release['draft'])
        self.assertNotIn('stale.jar', self.github.bytes)
        self.assertEqual(4, len(self.github.bytes))
        for asset in self.assets.iterdir():
            self.assertEqual(asset.read_bytes(), self.github.bytes[asset.name])

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
        self.assertEqual(['create', *['upload'] * 4, 'edit'], self.github.writes)


if __name__ == '__main__':
    unittest.main()
